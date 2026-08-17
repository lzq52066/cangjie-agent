package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库检索节点 — RAG 检索
 * <p>
 * config 参数：
 * - knowledgeBaseIds: 知识库 ID 列表（List<String> 或逗号分隔字符串）
 * - topK: 返回条数（默认 5）
 * <p>
 * 从 inputs 中取 query / question / input 作为检索关键词
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeNode implements WorkflowNode {

    private final HybridRetriever hybridRetriever;

    @Override
    public String getType() {
        return "knowledge";
    }

    @Override
    public String getName() {
        return "知识库";
    }

    @Override
    public String getDescription() {
        return "从知识库中检索相关段落";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
        Map<String, Object> safeConfig = config != null ? config : Map.of();
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();

        // 获取检索 query
        String query = getStringFromInputs(safeInputs, "query", "question", "input");
        if (query == null || query.isEmpty()) {
            query = "";
        }

        // 获取知识库 ID 列表
        List<String> knowledgeBaseIds = resolveKnowledgeBaseIds(safeConfig);

        // topK
        int topK = safeConfig.containsKey("topK")
                ? ((Number) safeConfig.get("topK")).intValue()
                : 5;

        if (knowledgeBaseIds.isEmpty()) {
            log.warn("KnowledgeNode: 未配置知识库 ID");
            return Map.of("knowledge_output", "", "knowledge_results", List.of());
        }

        List<RetrievalResult> results = hybridRetriever.retrieve(query, knowledgeBaseIds, topK);

        // 拼接检索结果文本
        String contextText = results.stream()
                .map(RetrievalResult::getContent)
                .collect(Collectors.joining("\n\n"));

        log.info("KnowledgeNode: 检索到 {} 条结果, query='{}'", results.size(), query);

        Map<String, Object> output = new HashMap<>();
        output.put("knowledge_output", contextText);
        output.put("knowledge_results", results.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("content", r.getContent());
            m.put("score", r.getFinalScore());
            m.put("documentId", r.getDocumentId());
            return m;
        }).collect(Collectors.toList()));
        return output;
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveKnowledgeBaseIds(Map<String, Object> config) {
        Object ids = config.get("knowledgeBaseIds");
        if (ids instanceof List) {
            return ((List<?>) ids).stream().map(Object::toString).collect(Collectors.toList());
        }
        if (ids instanceof String s && !s.isEmpty()) {
            return List.of(s.split(","));
        }
        return List.of();
    }

    private String getStringFromInputs(Map<String, Object> inputs, String... keys) {
        for (String key : keys) {
            Object val = inputs.get(key);
            if (val != null && !val.toString().isEmpty()) {
                return val.toString();
            }
        }
        return null;
    }
}
