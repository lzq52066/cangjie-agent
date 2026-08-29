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

    @Value("${cangjie.embedding.dimension-enabled:true}")
    private boolean dimensionEnabled;

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
            int dim = dimensionEnabled ? dimension : 1536;
            return new float[dim];
        }
        return embedViaApi(text);
    }

    /** 单次批量嵌入请求的文本条数上限（兼顾 API 限制与失败重试粒度） */
    private static final int EMBED_BATCH_SIZE = 16;

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Embedding API Key 未配置。请在 application.yml 中设置 cangjie.embedding.api-key");
        }
        List<float[]> results = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += EMBED_BATCH_SIZE) {
            int to = Math.min(from + EMBED_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(from, to);
            results.addAll(embedBatchViaApi(batch));
        }
        return results;
    }

    /**
     * 单批批量嵌入：整批失败时回退为逐条调用，隔离坏数据影响
     */
    private List<float[]> embedBatchViaApi(List<String> batch) {
        List<dev.langchain4j.data.segment.TextSegment> segments = new ArrayList<>(batch.size());
        for (String text : batch) {
            segments.add(dev.langchain4j.data.segment.TextSegment.from(text == null ? "" : text));
        }
        try {
            var response = getDelegate().embedAll(segments);
            List<float[]> vectors = new ArrayList<>(response.content().size());
            for (dev.langchain4j.data.embedding.Embedding embedding : response.content()) {
                vectors.add(embedding.vector());
            }
            return vectors;
        } catch (Exception e) {
            log.warn("批量嵌入失败，回退为逐条嵌入 ({} 条): {}", batch.size(), e.getMessage());
            List<float[]> vectors = new ArrayList<>(batch.size());
            for (String text : batch) {
                vectors.add(embed(text));
            }
            return vectors;
        }
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
                    var builder = dev.langchain4j.model.openai.OpenAiEmbeddingModel.builder()
                            .apiKey(apiKey)
                            .baseUrl(baseUrl)
                            .modelName(model);
                    // dimension-enabled=false 时不传 dimensions 参数（兼容 BGE-M3 等不支持该参数的模型）
                    if (dimensionEnabled) {
                        builder.dimensions(dimension);
                    }
                    delegate = builder.build();
                    log.info("Embedding 模型已初始化: {} @ {} (dimension={})", model, baseUrl,
                            dimensionEnabled ? dimension : "auto");
                }
            }
        }
        return delegate;
    }
}
