package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.Reranker;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.hutool.http.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cohere Rerank 重排序器
 * <p>
 * 调用 Cohere Rerank API 对候选结果精排。
 * API: POST /v1/rerank
 */
@Slf4j
@Component
public class CohereReranker implements Reranker {

    @Value("${cangjie.retrieval.rerank.cohere.base-url:https://api.cohere.com}")
    private String baseUrl;

    @Value("${cangjie.retrieval.rerank.cohere.api-key:}")
    private String apiKey;

    @Value("${cangjie.retrieval.rerank.cohere.model:rerank-multilingual-v3.0}")
    private String model;

    @Override
    public String getType() {
        return "cohere";
    }

    @Override
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Cohere API Key 未配置，跳过重排序");
            return candidates.stream().limit(topK).collect(Collectors.toList());
        }

        List<String> documents = candidates.stream()
                .map(RetrievalResult::getContent)
                .collect(Collectors.toList());

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("query", query);
        requestBody.put("documents", documents);
        requestBody.put("top_n", topK);

        try {
            String response = HttpRequest.post(baseUrl + "/v1/rerank")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(JsonUtils.toJSONString(requestBody))
                    .timeout(30_000)
                    .execute()
                    .body();

            if (response == null || response.isBlank()) {
                log.warn("Cohere Rerank 返回空响应");
                return candidates.stream().limit(topK).collect(Collectors.toList());
            }

            JsonNode json = JsonUtils.parseObject(response);
            JsonNode results = json == null ? null : json.get("results");
            if (results == null || results.isEmpty()) {
                return candidates.stream().limit(topK).collect(Collectors.toList());
            }

            // 按 API 返回顺序（已按相关性排序）重组结果
            List<RetrievalResult> reranked = new ArrayList<>(Math.min(topK, results.size()));
            for (JsonNode result : results) {
                int index = result.path("index").asInt();
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                RetrievalResult r = candidates.get(index);
                r.setFinalScore(result.path("relevance_score").asDouble());
                reranked.add(r);
                if (reranked.size() >= topK) {
                    break;
                }
            }
            return reranked;

        } catch (Exception e) {
            log.warn("Cohere Rerank 调用异常，降级返回粗排结果: {}", e.getMessage());
            return candidates.stream().limit(topK).collect(Collectors.toList());
        }
    }
}