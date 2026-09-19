package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.model.ChatTraceContext;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 记忆合并判定：对向量近邻命中的"可能重复"记忆，调 LLM 判断两条记忆是否指向同一信息点，
 * 能否归并为一条更完整的表述。独立于纯向量阈值判重，避免把"喜欢美式 / 喜欢拿铁"这类冲突信息误合并。
 */
@Slf4j
@Service
public class MemoryMergeService {

    /** 合并判定的输出结构；由 responseSchema 强约束，模型不再可能返回格式漂移的结果 */
    private static final String MERGE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "merge": {"type": "boolean", "description": "两条记忆是否应合并为一条"},
                "content": {"type": "string", "description": "合并后的记忆；不合并时输出空字符串"}
              },
              "required": ["merge", "content"],
              "additionalProperties": false
            }
            """;

    /** 合并判定结果的强类型映射（public 以便 Jackson 反序列化） */
    public record MergeDecision(boolean merge, String content) {
    }

    /**
     * 判定两条记忆是否应合并，并返回合并后的内容。
     *
     * @param appId      应用 ID（用于 trace 归属，可为 null）
     * @param appName    应用名称（可为 null）
     * @param modelId    模型 ID（可为 null）
     * @param sessionId  会话 ID（可为 null）
     * @param userId     用户 ID（可为 null）
     * @return 合并后的内容；判定为不应合并或调用失败时返回 null
     */
    public String decideMergedContent(OpenAICompatibleClient client, Double temperature,
                                      String existing, String incoming,
                                      String appId, String appName, String modelId,
                                      String sessionId, String userId) {
        try {
            String prompt = buildMergePrompt(existing, incoming);
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system("你是严谨的用户画像记忆整理助手，只输出 JSON。"),
                            ChatMessage.user(prompt)))
                    .temperature(0.0)
                    .responseSchema(MERGE_SCHEMA)
                    .responseSchemaName("memory_merge")
                    // trace 由 ChatModelListener 统一落库，这里只负责把业务归属挂上
                    .traceContext(ChatTraceContext.builder()
                            .requestId(StringUtils.hasText(sessionId) ? "memory-merge-" + sessionId : "memory-merge")
                            .appId(appId)
                            .appName(appName)
                            .sessionId(sessionId)
                            .userId(userId)
                            .modelId(modelId)
                            .build())
                    .build();
            ChatResponse response;
            try {
                response = client.chat(request);
            } catch (Exception e) {
                // 判定失败按"不合并"处理，保留两条记忆，不影响主流程
                log.warn("记忆合并判定失败，按不合并处理: {}", e.getMessage());
                return null;
            }
            if (response == null || !StringUtils.hasText(response.getContent())) {
                return null;
            }
            String raw = response.getContent().trim();
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            MergeDecision decision = JsonUtils.parseObject(raw.substring(start, end + 1), MergeDecision.class);
            if (decision == null || !decision.merge()) {
                return null;
            }
            String merged = decision.content();
            // 合并结果必须有信息量，且不应只是新记忆的原样复制（那属于判重而非合并）
            if (!StringUtils.hasText(merged)) {
                return null;
            }
            return merged.trim();
        } catch (Exception e) {
            // 判定失败按"不合并"处理，保留两条记忆，不影响主流程
            log.warn("记忆合并判定失败，按不合并处理: {}", e.getMessage());
            return null;
        }
    }

    private String buildMergePrompt(String existing, String incoming) {
        return """
                下面是两条关于同一用户的画像记忆，请判断它们是否在描述同一个信息点。

                记忆A：%s
                记忆B：%s

                判定规则：
                1. 两条记忆指向同一事实/偏好/背景/目标，只是措辞或详略不同（含信息互补），才可合并
                2. 若两条记忆相互矛盾（如 A 说喜欢、B 说不喜欢），不能合并
                3. 若只是主题相关但各说一件事（如"喜欢美式"与"每天喝咖啡"可合并；"喜欢美式"与"喜欢拿铁"不可合并），不能合并
                4. 拿不准时，不合并

                合并时保留两条记忆中的有效信息，写成一句简洁、客观、不超过 50 字的陈述，不要编造未提及的内容。

                只输出 JSON：
                {"merge": true, "content": "合并后的记忆"}
                或
                {"merge": false, "content": ""}
                """.formatted(existing, incoming);
    }
}
