package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.Reranker;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.hutool.http.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BGE Reranker 客户端（兼容 TEI / HuggingFace Inference Endpoints）
 * <p>
 * 调用本地或远程 BGE 重排服务精排候选结果。
 * 典型服务：HuggingFace TEI / text-embeddings-inference
 * API: POST /rerank
 */
@Slf4j
@Component
public class BgeRerankerClient implements Reranker {

    @Value("${cangjie.retrieval.rerank.bge.base-url:http://localhost:8081}")
    private String baseUrl;

    @Override
    public String getType() {
        return "bge";
    }

    @Override
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("BGE Reranker base-url 未配置，跳过重排序");
            return candidates.stream().limit(topK).collect(Collectors.toList());
        }

        List<String> texts = candidates.stream()
                .map(RetrievalResult::getContent)
                .collect(Collectors.toList());

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("query", query);
        requestBody.put("texts", texts);
        requestBody.put("top_n", topK);
        requestBody.put("return_documents", true);

        try {
            String response = HttpRequest.post(baseUrl + "/rerank")
                    .header("Content-Type", "application/json")
                    .body(JsonUtils.toJSONString(requestBody))
                    .timeout(30_000)
                    .execute()
                    .body();

            if (response == null || response.isBlank()) {
                log.warn("BGE Rerank 返回空响应");
                return candidates.stream().limit(topK).collect(Collectors.toList());
            }

            ArrayNode results = JsonUtils.parseArray(response);
            if (results == null || results.isEmpty()) {
                return candidates.stream().limit(topK).collect(Collectors.toList());
            }

            // 按 score 降序
            List<JsonNode> sorted = new ArrayList<>();
            results.forEach(sorted::add);
            sorted.sort(Comparator.comparingDouble(o -> -o.path("score").asDouble()));

            List<RetrievalResult> reranked = new ArrayList<>(Math.min(topK, sorted.size()));
            for (JsonNode result : sorted) {
                int index = result.path("index").asInt();
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                RetrievalResult r = candidates.get(index);
                r.setFinalScore(result.path("score").asDouble());
                reranked.add(r);
                if (reranked.size() >= topK) {
                    break;
                }
            }
            return reranked;

        } catch (Exception e) {
            log.warn("BGE Rerank 调用异常，降级返回粗排结果: {}", e.getMessage());
            return candidates.stream().limit(topK).collect(Collectors.toList());
        }
    }
}