package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认嵌入提供者
 * <p>
 * 优先使用 OpenAI 兼容 API（支持通义/智谱等兼容接口）。
 * 若未配置 API Key，降级为确定性哈希伪向量（仅用于开发测试，不可用于生产检索）。
 */
@Slf4j
@Component
public class DefaultEmbeddingProvider implements EmbeddingProvider {

    @Value("${cangjie.embedding.api-key:}")
    private String apiKey;

    @Value("${cangjie.embedding.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${cangjie.embedding.model:text-embedding-3-small}")
    private String model;

    @Value("${cangjie.embedding.dimension:1536}")
    private int dimension;

    private volatile dev.langchain4j.model.embedding.EmbeddingModel delegate;

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimension];
        }
        if (isConfigured()) {
            return embedViaApi(text);
        }
        return pseudoEmbed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private float[] embedViaApi(String text) {
        try {
            dev.langchain4j.model.embedding.EmbeddingModel m = getDelegate();
            dev.langchain4j.data.embedding.Embedding embedding = m.embed(text).content();
            return embedding.vector();
        } catch (Exception e) {
            log.warn("API 嵌入失败，降级为伪向量: {}", e.getMessage());
            return pseudoEmbed(text);
        }
    }

    private dev.langchain4j.model.embedding.EmbeddingModel getDelegate() {
        if (delegate == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = dev.langchain4j.model.openai.OpenAiEmbeddingModel.builder()
                            .apiKey(apiKey)
                            .baseUrl(baseUrl)
                            .modelName(model)
                            .dimensions(dimension)
                            .build();
                    log.info("Embedding 模型已初始化: {} @ {}", model, baseUrl);
                }
            }
        }
        return delegate;
    }

    /**
     * 伪向量（开发测试用）
     * 基于文本哈希生成确定性向量，不保证语义相似性。
     */
    private float[] pseudoEmbed(String text) {
        float[] vec = new float[dimension];
        int hash = text.hashCode();
        java.util.Random rng = new java.util.Random(hash);
        float norm = 0;
        for (int i = 0; i < dimension; i++) {
            vec[i] = (float) rng.nextGaussian();
            norm += vec[i] * vec[i];
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                vec[i] /= norm;
            }
        }
        return vec;
    }
}
