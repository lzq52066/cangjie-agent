package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.rag.QueryRewriter;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 LLM 的查询改写器
 * <p>
 * 结合最近对话历史对用户 query 消歧/扩写，提升多轮场景下的检索召回率。
 * 复用 {@link IModelService} 的客户端缓存，改写失败或历史为空时降级返回原 query。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmQueryRewriter implements QueryRewriter {

    private static final String REWRITE_SYSTEM =
            "你是一个检索查询改写助手。结合对话历史将用户最新 query 改写为适合向量检索的独立检索文本。要求：\n"
            + "1. 消除指代（他/它/这个/那个），补全省略主语；\n"
            + "2. 保留核心检索词，不添加检索无关的对话信息；\n"
            + "3. 原文已明确时不改写，直接返回原文；\n"
            + "4. 只输出改写后的检索文本，禁止输出 JSON、解释或前缀。";

    private final IModelService modelService;

    @Value("${cangjie.rag.query-rewrite.llm.temperature:0.3}")
    private double temperature;

    @Value("${cangjie.rag.query-rewrite.llm.max-tokens:200}")
    private int maxTokens;

    @Override
    public String getType() {
        return "llm";
    }

    @Override
    public String rewrite(String originalQuery, List<ChatMessage> history) {
        if (!StringUtils.hasText(originalQuery)) {
            return originalQuery;
        }
        if (history == null || history.isEmpty()) {
            return originalQuery;
        }

        String historyText = history.stream()
                .map(m -> {
                    String role = StringUtils.hasText(m.getRole()) ? m.getRole() : "user";
                    return switch (role) {
                        case "user" -> "用户: " + m.getContent();
                        case "assistant" -> "助手: " + m.getContent();
                        default -> role + ": " + m.getContent();
                    };
                })
                .collect(Collectors.joining("\n"));

        String userPrompt = "对话历史：\n" + historyText
                + "\n\n用户最新问题：\n" + originalQuery
                + "\n\n改写后检索文本：";

        try {
            OpenAICompatibleClient client = modelService.getDefaultClient();
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(REWRITE_SYSTEM),
                            ChatMessage.user(userPrompt)))
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();
            ChatResponse response = client.chat(request);

            if (response == null || !StringUtils.hasText(response.getContent())) {
                return originalQuery;
            }
            return response.getContent().trim();
        } catch (Exception e) {
            log.warn("查询改写失败，降级返回原 query: {}", e.getMessage());
            return originalQuery;
        }
    }
}
