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
 * 使用 OpenAI 兼容 API（支持通义/智谱/硅基流动等兼容接口）。
 * 未配置 API Key 或 API 调用失败时直接抛出异常，不降级。
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
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Embedding API Key 未配置。请在 application.yml 中设置 cangjie.embedding.api-key");
        }
        if (text == null || text.isBlank()) {
            return new float[dimension];
        }
        return embedViaApi(text);
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
        dev.langchain4j.model.embedding.EmbeddingModel m = getDelegate();
        dev.langchain4j.data.embedding.Embedding embedding = m.embed(text).content();
        return embedding.vector();
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
}
