package cn.cangjiecloud.knowledge.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultEmbeddingProvider} 单元测试。
 * <p>
 * 覆盖：未配置 API Key 异常、空白文本零向量、模型委托调用、16 条分片批处理与整批失败逐条回退。
 */
@ExtendWith(MockitoExtension.class)
class DefaultEmbeddingProviderTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private DefaultEmbeddingProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultEmbeddingProvider();
        ReflectionTestUtils.setField(provider, "apiKey", "test-key");
        ReflectionTestUtils.setField(provider, "baseUrl", "http://localhost:9999/v1");
        ReflectionTestUtils.setField(provider, "model", "text-embedding-3-small");
        ReflectionTestUtils.setField(provider, "dimensionEnabled", true);
        ReflectionTestUtils.setField(provider, "dimension", 1536);
        ReflectionTestUtils.setField(provider, "delegate", embeddingModel);
    }

    @Nested
    class 基础行为 {

        @Test
        void 未配置ApiKey时embed与embedBatch均应抛出异常() {
            DefaultEmbeddingProvider unconfigured = new DefaultEmbeddingProvider();

            assertThatThrownBy(() -> unconfigured.embed("hello"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("api-key");
            assertThatThrownBy(() -> unconfigured.embedBatch(List.of("hello")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Embedding API Key 未配置");
        }

        @Test
        void 空白文本应返回配置维度的零向量且不调用模型() {
            float[] fromNull = provider.embed(null);
            float[] fromBlank = provider.embed("   ");

            assertThat(fromNull).hasSize(1536).containsOnly(0f);
            assertThat(fromBlank).hasSize(1536).containsOnly(0f);
            verifyNoInteractions(embeddingModel);
        }

        @Test
        void dimension关闭时空白文本仍返回1536维() {
            ReflectionTestUtils.setField(provider, "dimensionEnabled", false);
            ReflectionTestUtils.setField(provider, "dimension", 768);

            assertThat(provider.embed("")).hasSize(1536);
            assertThat(provider.dimension()).isEqualTo(768);
        }

        @Test
        void dimension应返回配置维度() {
            assertThat(provider.dimension()).isEqualTo(1536);
        }
    }

    @Nested
    class 委托与批量 {

        @Test
        void embed应委托模型并返回向量() {
            when(embeddingModel.embed("你好"))
                    .thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));

            assertThat(provider.embed("你好")).containsExactly(0.1f, 0.2f);
            verify(embeddingModel).embed("你好");
        }

        @Test
        void embedBatch空输入应返回空列表且不调用模型() {
            assertThat(provider.embedBatch(null)).isEmpty();
            assertThat(provider.embedBatch(List.of())).isEmpty();
            verifyNoInteractions(embeddingModel);
        }

        @Test
        void embedBatch应按16条分片调用模型() {
            List<String> texts = IntStream.range(0, 17)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.toList());
            when(embeddingModel.embedAll(anyList())).thenAnswer(invocation -> {
                List<TextSegment> segments = invocation.getArgument(0);
                List<Embedding> embeddings = new ArrayList<>(segments.size());
                for (TextSegment segment : segments) {
                    embeddings.add(Embedding.from(new float[]{Float.parseFloat(segment.text())}));
                }
                return Response.from(embeddings);
            });

            List<float[]> vectors = provider.embedBatch(texts);

            assertThat(vectors).hasSize(17);
            assertThat(vectors.get(0)).containsExactly(0f);
            assertThat(vectors.get(16)).containsExactly(16f);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingModel, times(2)).embedAll(captor.capture());
            assertThat(captor.getAllValues().get(0)).hasSize(16);
            assertThat(captor.getAllValues().get(1)).hasSize(1);
            assertThat(captor.getAllValues().get(1).get(0).text()).isEqualTo("16");
        }

        @Test
        void embedBatch整批失败应回退为逐条嵌入() {
            when(embeddingModel.embedAll(anyList())).thenThrow(new RuntimeException("批量接口超时"));
            when(embeddingModel.embed("a"))
                    .thenReturn(Response.from(Embedding.from(new float[]{1f})));
            when(embeddingModel.embed("b"))
                    .thenReturn(Response.from(Embedding.from(new float[]{2f})));

            List<float[]> vectors = provider.embedBatch(List.of("a", "b"));

            assertThat(vectors).hasSize(2);
            assertThat(vectors.get(0)).containsExactly(1f);
            assertThat(vectors.get(1)).containsExactly(2f);
            verify(embeddingModel).embedAll(anyList());
            verify(embeddingModel).embed("a");
            verify(embeddingModel).embed("b");
        }
    }
}
