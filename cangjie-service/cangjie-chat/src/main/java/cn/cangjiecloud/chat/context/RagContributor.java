package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.QueryRewriterFactory;
import cn.cangjiecloud.core.rag.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索（RAG 槽位，可压缩）。
 * <p>
 * 改造前检索文本内嵌在系统消息里；独立成槽后既能在留痕中看到检索占用，也让预算裁剪
 * 可以整段丢弃知识库内容而不影响人设。查询改写（默认关闭）在此完成，历史经
 * {@link ContextAttrs#HISTORY_MESSAGES} 复用不再二次查库；agentic 模式跳过预处理检索，
 * 由 LLM 自主调用检索工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagContributor implements ContextContributor {

    private static final int DEFAULT_TOP_K = 5;

    private final HybridRetriever hybridRetriever;
    private final QueryRewriterFactory queryRewriterFactory;

    @Autowired(required = false)
    private TraceCollector traceCollector;

    /** 查询改写开关（默认关闭，关闭时行为与改造前完全一致） */
    @Value("${cangjie.rag.query-rewrite.enabled:false}")
    private boolean queryRewriteEnabled;

    /** 查询改写器类型（none / llm） */
    @Value("${cangjie.rag.query-rewrite.type:none}")
    private String queryRewriteType;

    @Override
    public String name() {
        return "rag";
    }

    @Override
    public ContextSlot slot() {
        return ContextSlot.RAG;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean supports(ContextRequest request) {
        return request.getKnowledgeBaseIds() != null && !request.getKnowledgeBaseIds().isEmpty()
                && !"agentic".equals(request.getRagMode());
    }

    @Override
    @SuppressWarnings("unchecked")
    public ContextFragment contribute(ContextRequest request) {
        String query = request.getUserQuery();
        if (queryRewriteEnabled) {
            Object history = request.getAttributes().get(ContextAttrs.HISTORY_MESSAGES);
            query = queryRewriterFactory.get(queryRewriteType)
                    .rewrite(query, history instanceof List<?> list ? (List<ChatMessage>) list : List.of());
        }

        long retrievalStart = System.currentTimeMillis();
        List<RetrievalResult> results;
        try {
            results = hybridRetriever.retrieve(query, request.getKnowledgeBaseIds(), DEFAULT_TOP_K);
        } catch (Exception e) {
            recordTrace(request.getTraceId(), System.currentTimeMillis() - retrievalStart, "fail",
                    "知识库检索失败: " + e.getMessage());
            log.warn("知识库检索失败: {}", e.getMessage());
            return ContextFragment.empty(slot(), name());
        }
        recordTrace(request.getTraceId(), System.currentTimeMillis() - retrievalStart, "success",
                "知识库检索: " + results.size() + " 条结果");
        if (results.isEmpty()) {
            return ContextFragment.empty(slot(), name());
        }

        StringBuilder prompt = new StringBuilder("以下是从知识库中检索到的相关内容：\n\n");
        Object sources = request.getAttributes().get(ContextAttrs.RETRIEVAL_SOURCES);
        List<Map<String, Object>> retrievalSources =
                sources instanceof List<?> list ? (List<Map<String, Object>>) list : null;
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            prompt.append("【片段").append(i + 1).append("】")
                    .append("来源：").append(getDocumentName(r)).append("\n")
                    .append("内容：").append(r.getContent()).append("\n\n");

            if (retrievalSources != null) {
                Map<String, Object> source = new HashMap<>();
                source.put("paragraphId", r.getParagraphId());
                source.put("documentId", r.getDocumentId());
                source.put("knowledgeBaseId", r.getKnowledgeBaseId());
                source.put("content", r.getContent());
                source.put("score", r.getFinalScore());
                source.put("documentName", getDocumentName(r));
                retrievalSources.add(source);
            }
        }
        return ContextFragment.of(slot(), name(), List.of(ChatMessage.system(prompt.toString().trim())));
    }

    private void recordTrace(String traceId, long duration, String status, String detail) {
        if (traceCollector != null) {
            traceCollector.record("retrieval", "search", traceId, duration, status, detail);
        }
    }

    private String getDocumentName(RetrievalResult r) {
        if (r.getMetadata() != null) {
            Object name = r.getMetadata().get("title");
            if (name != null) {
                return name.toString();
            }
        }
        return r.getDocumentId() != null ? r.getDocumentId() : "未知文档";
    }
}
