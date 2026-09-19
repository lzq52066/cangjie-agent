package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.model.ChatTraceContext;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.entity.MemorySimilarity;
import cn.cangjiecloud.prompt.memory.MemoryDedupProperties;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 长期记忆提取服务：对话结束后异步调用模型提取用户画像并入库。
 *
 * 独立 Bean 而非 ChatServiceImpl 的私有方法，原因：
 * 1. Spring @Async 只能通过代理对外部 Bean 生效，同类自调用会绕过代理；
 * 2. 异步线程中 Sa-Token ThreadLocal 上下文不可用，因此 userId 必须在调用方显式传入。
 *
 * 入库时做语义去重：向量近邻命中已有记忆时，重复则强化置信度，
 * 高度相关则调 LLM 判断能否合并为一条，避免同一事实反复产生措辞不同的重复记忆。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryExtractService {

    /**
     * 记忆提取的输出结构，由 responseSchema 强约束。
     * 根元素必须是 object：OpenAI 结构化输出的严格模式不允许数组直接作为根，故用 memories 包一层。
     */
    private static final String MEMORY_EXTRACT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "memories": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "dimension": {"type": "string", "enum": ["preference", "background", "convention", "goal"]},
                      "memory_type": {"type": "string", "enum": ["user", "scene"]},
                      "content": {"type": "string", "description": "简洁陈述事实，不超过 50 字"},
                      "confidence": {"type": "number", "description": "置信度 0~1，不确定时取 0.8"}
                    },
                    "required": ["dimension", "memory_type", "content", "confidence"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["memories"],
              "additionalProperties": false
            }
            """;

    /** 单条提取结果的强类型映射（public 以便 Jackson 反序列化），字段与上面的 schema 一一对应 */
    public record MemoryItem(String dimension,
                             @JsonProperty("memory_type") String memoryType,
                             String content,
                             Double confidence) {
    }

    /** 记忆提取结果的强类型映射 */
    public record MemoryExtractResult(List<MemoryItem> memories) {
    }

    private final IModelService modelService;
    private final ILongTermMemoryService longTermMemoryService;
    private final MemoryMergeService memoryMergeService;
    private final MemoryDedupProperties dedupProperties;

    @Async
    public void extract(String userId, ApplicationEntity application, String sessionId,
                        ChatMessageEntity userMessage, ChatMessageEntity aiMessage) {
        if (!StringUtils.hasText(userId) || application == null
                || userMessage == null || aiMessage == null) {
            return;
        }
        // 应用未启用记忆开关时跳过提取
        if (!Boolean.TRUE.equals(application.getMemoryEnabled())) {
            return;
        }
        try {
            String extractPrompt = buildMemoryExtractPrompt(userMessage.getContent(), aiMessage.getContent());

            String modelId = application.getModelId();
            // 走默认模型时回填其 ID/名称，避免 trace 缺失模型归属导致成本无法归集
            ModelEntity model = StringUtils.hasText(modelId)
                    ? modelService.getById(modelId) : modelService.getDefaultModel();
            String effectiveModelId = model != null ? model.getId() : modelId;
            OpenAICompatibleClient client = StringUtils.hasText(effectiveModelId)
                    ? modelService.getClient(effectiveModelId) : modelService.getDefaultClient();

            List<ChatMessage> extractMessages = List.of(
                    ChatMessage.system("你是一个用户画像分析助手，请从对话中提取用户信息与场景信息。"),
                    ChatMessage.user(extractPrompt));
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(extractMessages)
                    .temperature(application.getTemperature() != null ? application.getTemperature() : 0.7)
                    .responseSchema(MEMORY_EXTRACT_SCHEMA)
                    .responseSchemaName("memory_extract")
                    // trace 由 ChatModelListener 统一落库，这里只负责把业务归属挂上
                    .traceContext(ChatTraceContext.builder()
                            .requestId(StringUtils.hasText(sessionId)
                                    ? "long-term-memory-extract-" + sessionId : "long-term-memory-extract")
                            .appId(application.getId())
                            .appName(application.getName())
                            .sessionId(sessionId)
                            .userId(userId)
                            .modelId(effectiveModelId)
                            .modelName(model != null ? model.getName() : null)
                            .build())
                    .build();

            ChatResponse response = client.chat(chatRequest);
            if (response == null || !StringUtils.hasText(response.getContent())) {
                return;
            }

            parseAndSaveMemories(userId, application, sessionId, client, response.getContent());
        } catch (Exception e) {
            log.warn("异步长期记忆提取失败: userId={}, appId={}", userId, application.getId(), e);
        }
    }

    private String buildMemoryExtractPrompt(String userInput, String aiReply) {
        return """
                请从以下对话中提取记忆信息，每条记忆标注维度与类型。
                
                【维度 dimension】
                - preference 偏好：用户明确表达的好恶、喜欢/不喜欢什么
                - background 背景：用户的职业、技能水平、角色、所处行业等
                - convention 习惯：用户的沟通风格、工作方式、交互模式等
                - goal 目标：用户当前关注的目标、任务意图、期望达成的结果
                
                【类型 memory_type】
                - user：关于用户本人的稳定信息，跨会话长期有效（如职业、偏好）
                - scene：仅与当前会话/任务相关的事实（如本次任务的具体约定、临时决定、上下文背景）
                
                对话内容：
                用户：%s
                AI：%s
                
                请严格按以下JSON格式输出，不要输出其他内容：
                {"memories": [{"dimension": "preference", "memory_type": "user", "content": "...", "confidence": 0.8}]}
                
                注意：
                1. 如果没有值得记忆的信息，memories 输出空数组 []
                2. 不要把一次性的问候、闲聊当作记忆
                3. content 要简洁陈述事实，不超过 50 字
                """.formatted(userInput, aiReply);
    }

    private void parseAndSaveMemories(String userId, ApplicationEntity application, String sessionId,
                                      OpenAICompatibleClient client, String llmOutput) {
        try {
            // 严格模式下即为纯 JSON，这里仍兼容被解释性文字包裹的情况
            String jsonStr = llmOutput.trim();
            int start = jsonStr.indexOf('{');
            int end = jsonStr.lastIndexOf('}');
            if (start < 0 || end <= start) {
                log.warn("记忆提取结果不是有效 JSON: {}", llmOutput);
                return;
            }
            MemoryExtractResult result =
                    JsonUtils.parseObject(jsonStr.substring(start, end + 1), MemoryExtractResult.class);
            List<MemoryItem> items = result != null && result.memories() != null ? result.memories() : List.of();
            if (items.isEmpty()) {
                return;
            }

            int savedCount = 0;
            for (MemoryItem item : items) {
                if (item == null || !StringUtils.hasText(item.dimension()) || !StringUtils.hasText(item.content())) {
                    continue;
                }
                Double confidence = item.confidence() != null ? item.confidence() : 0.8;
                boolean isScene = "scene".equalsIgnoreCase(item.memoryType());

                LongTermMemoryEntity entity = new LongTermMemoryEntity();
                entity.setUserId(userId);
                entity.setApplicationId(application.getId());
                entity.setDimension(item.dimension());
                entity.setContent(item.content());
                entity.setConfidence(confidence);
                entity.setSource("inferred");
                entity.setTriggerCount(0);
                entity.setIsActive(true);
                entity.setLastTriggeredAt(LocalDateTime.now());
                entity.setMemoryType(isScene ? "scene" : "user");
                entity.setSessionId(isScene ? sessionId : null);

                if (storeMemory(entity, client, application.getTemperature(), application, sessionId, userId)) {
                    savedCount++;
                }
            }

            if (savedCount > 0) {
                log.info("记忆提取完成: userId={}, appId={}, session={}, 入库/更新 {} 条",
                        userId, application.getId(), sessionId, savedCount);
            }
        } catch (Exception e) {
            log.warn("解析长期记忆失败: userId={}, output={}", userId, llmOutput, e);
        }
    }

    /**
     * 单条提取记忆的语义感知入库，返回是否产生了写入（新增/强化/合并）。
     */
    private boolean storeMemory(LongTermMemoryEntity entity, OpenAICompatibleClient client, Double temperature,
                                ApplicationEntity application, String sessionId, String userId) {
        float[] embedding = longTermMemoryService.embed(entity.getContent());
        if (embedding == null) {
            // 向量不可用：直接走精确去重 upsert（内含降级逻辑）
            longTermMemoryService.upsert(entity);
            return true;
        }

        List<MemorySimilarity> neighbors =
                longTermMemoryService.findSimilar(embedding, entity, dedupProperties.getCandidateLimit());
        if (neighbors.isEmpty()) {
            longTermMemoryService.insertNew(entity, embedding);
            return true;
        }

        MemorySimilarity best = neighbors.get(0);
        double sim = best.getSimilarity() != null ? best.getSimilarity() : 0;
        double confidence = entity.getConfidence() != null ? entity.getConfidence() : 0.8;

        // 1) 高度一致：同一条记忆，只强化置信度，不新增
        if (sim >= dedupProperties.getDuplicateThreshold()) {
            longTermMemoryService.reinforce(best.getId(), entity.getSource());
            return true;
        }

        // 2) 中度相似：可能是同一事实的不同表述/互补信息，交 LLM 判定是否合并
        if (sim >= dedupProperties.getMergeThreshold()) {
            String merged = memoryMergeService.decideMergedContent(
                    client, temperature, best.getContent(), entity.getContent(),
                    application.getId(), application.getName(), application.getModelId(), sessionId, userId);
            if (StringUtils.hasText(merged)) {
                float[] mergedVector = longTermMemoryService.embed(merged);
                longTermMemoryService.mergeInto(best.getId(), merged, confidence, mergedVector);
                return true;
            }
        }

        // 3) 不相似或判定不应合并：作为新记忆保留
        longTermMemoryService.insertNew(entity, embedding);
        return true;
    }
}
