package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.chat.harness.HarnessConfigResolver;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把对话入参（应用实体 + 请求）翻译为 {@link ContextRequest}。
 * <p>
 * Contributor 只读 ContextRequest，不接触业务实体；应用上的 JSON 数组字段
 * （知识库/技能/规则 ID）在此解析成列表后经 attributes 透传。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextRequestFactory {

    private final HarnessConfigResolver configResolver;

    public ContextRequest newRequest(ApplicationEntity application, String sessionId, String userId,
                                     String userMessage, List<ChatRequestDTO.ConversationMessage> requestMessages,
                                     List<Map<String, Object>> retrievalSources, String traceId) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ContextAttrs.RETRIEVAL_SOURCES, retrievalSources == null ? new ArrayList<>() : retrievalSources);
        attributes.put(ContextAttrs.APPLICATION_NAME, application.getName());
        attributes.put(ContextAttrs.DESCRIPTION, application.getDescription());
        attributes.put(ContextAttrs.PROMPT_TEMPLATE_ID, application.getPromptTemplateId());
        attributes.put(ContextAttrs.SKILL_IDS, parseIdList(application.getSkillIds()));
        attributes.put(ContextAttrs.RULE_IDS, parseIdList(application.getRuleIds()));

        List<ChatMessage> conversation = toConversation(requestMessages);
        if (!conversation.isEmpty()) {
            // OpenAI 兼容路径标记：历史/摘要/用户消息贡献者据此让位
            attributes.put(ContextAttrs.REQUEST_CONVERSATION, conversation);
        }

        return ContextRequest.builder()
                .applicationId(application.getId())
                .sessionId(sessionId)
                .userId(userId)
                .modelId(application.getModelId())
                .userQuery(userMessage)
                .knowledgeBaseIds(parseIdList(application.getKnowledgeBaseIds()))
                .memoryEnabled(Boolean.TRUE.equals(application.getMemoryEnabled()))
                .maxTurns(application.getMaxTurns())
                .ragMode(application.getRagMode())
                .traceId(traceId)
                .config(configResolver.parse(application))
                .attributes(attributes)
                .build();
    }

    private List<ChatMessage> toConversation(List<ChatRequestDTO.ConversationMessage> requestMessages) {
        List<ChatMessage> result = new ArrayList<>();
        if (requestMessages == null) {
            return result;
        }
        for (ChatRequestDTO.ConversationMessage m : requestMessages) {
            if (StringUtils.hasText(m.getRole()) && StringUtils.hasText(m.getContent())) {
                result.add(ChatMessage.builder()
                        .role(m.getRole().toLowerCase())
                        .content(m.getContent())
                        .build());
            }
        }
        return result;
    }

    /**
     * 应用配置里的 JSON 数组字段（知识库/技能/规则 ID）解析为字符串列表，非法格式按空处理
     */
    static List<String> parseIdList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> result = new ArrayList<>();
            for (JsonNode node : JsonUtils.parseArray(json)) {
                String value = node == null || node.isNull() ? null : node.asText();
                if (StringUtils.hasText(value)) {
                    result.add(value);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 JSON 数组失败: {}, json={}", e.getMessage(), json);
            return new ArrayList<>();
        }
    }
}
