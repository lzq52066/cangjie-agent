package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryExtractService {

    private final IModelService modelService;
    private final ILongTermMemoryService longTermMemoryService;

    @Async
    public void extract(String userId, ApplicationEntity application,
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

            OpenAICompatibleClient client = StringUtils.hasText(application.getModelId())
                    ? modelService.getClient(application.getModelId())
                    : modelService.getDefaultClient();

            List<ChatMessage> extractMessages = List.of(
                    ChatMessage.system("你是一个用户画像分析助手，请从对话中提取用户信息。"),
                    ChatMessage.user(extractPrompt));
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(extractMessages)
                    .temperature(application.getTemperature() != null ? application.getTemperature() : 0.7)
                    .build();

            ChatResponse response = client.chat(chatRequest);
            if (response == null || !StringUtils.hasText(response.getContent())) {
                return;
            }

            parseAndSaveMemories(userId, application.getId(), response.getContent());
        } catch (Exception e) {
            log.warn("异步长期记忆提取失败: userId={}, appId={}", userId, application.getId(), e);
        }
    }

    private String buildMemoryExtractPrompt(String userInput, String aiReply) {
        return """
                请从以下对话中提取用户画像信息，按四个维度输出（如无相关信息则对应维度留空）：
                
                【preference】偏好：用户明确表达的好恶、喜欢/不喜欢什么
                【background】背景：用户的职业、技能水平、角色、所处行业等
                【convention】习惯：用户的沟通风格、工作方式、交互模式等
                【goal】目标：用户当前关注的目标、任务意图、期望达成的结果
                
                对话内容：
                用户：%s
                AI：%s
                
                请严格按以下JSON格式输出，不要输出其他内容：
                [
                  {"dimension": "preference", "content": "...", "confidence": 0.8},
                  {"dimension": "background", "content": "...", "confidence": 0.8},
                  {"dimension": "convention", "content": "...", "confidence": 0.8},
                  {"dimension": "goal", "content": "...", "confidence": 0.8}
                ]
                
                注意：如果某个维度没有相关信息，content 设为空字符串 ""。
                """.formatted(userInput, aiReply);
    }

    private void parseAndSaveMemories(String userId, String appId, String llmOutput) {
        try {
            // 提取 JSON 数组
            String jsonStr = llmOutput.trim();
            int start = jsonStr.indexOf('[');
            int end = jsonStr.lastIndexOf(']');
            if (start < 0 || end < 0 || end <= start) {
                log.warn("记忆提取结果不是有效 JSON: {}", llmOutput);
                return;
            }
            jsonStr = jsonStr.substring(start, end + 1);

            List<JSONObject> items = JSON.parseArray(jsonStr, JSONObject.class);
            if (items == null || items.isEmpty()) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            int savedCount = 0;

            for (JSONObject item : items) {
                String dimension = item.getString("dimension");
                String content = item.getString("content");
                if (!StringUtils.hasText(dimension) || !StringUtils.hasText(content)) {
                    continue;
                }
                Double confidence = item.getDouble("confidence");
                if (confidence == null) {
                    confidence = 0.8;
                }

                LongTermMemoryEntity entity = new LongTermMemoryEntity();
                entity.setUserId(userId);
                entity.setApplicationId(appId);
                entity.setDimension(dimension);
                entity.setContent(content);
                entity.setConfidence(confidence);
                entity.setSource("inferred");
                entity.setTriggerCount(0);
                entity.setIsActive(true);
                entity.setLastTriggeredAt(now);

                longTermMemoryService.upsert(entity);
                savedCount++;
            }

            if (savedCount > 0) {
                log.info("长期记忆提取完成: userId={}, appId={}, 保存了 {} 条记忆",
                        userId, appId, savedCount);
            }
        } catch (Exception e) {
            log.warn("解析长期记忆失败: userId={}, output={}", userId, llmOutput, e);
        }
    }
}