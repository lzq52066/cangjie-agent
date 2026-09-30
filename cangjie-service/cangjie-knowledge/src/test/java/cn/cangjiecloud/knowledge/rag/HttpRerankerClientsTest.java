package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.Reranker;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BgeRerankerClient} 与 {@link CohereReranker} 单元测试。
 * <p>
 * 通过静态 mock hutool {@code HttpRequest} 模拟 HTTP 调用，覆盖：
 * 空候选/未配置短路、正常排序重组、越界 index 跳过、空响应与调用异常的降级返回。
 */
@ExtendWith(MockitoExtension.class)
class HttpRerankerClientsTest {

    private static RetrievalResult candidate(String paragraphId, String content) {
        return RetrievalResult.builder()
                .paragraphId(paragraphId)
                .documentId("doc-" + paragraphId)
                .content(content)
                .finalScore(0.1)
                .build();
    }

    private static List<RetrievalResult> threeCandidates() {
        return List.of(candidate("p1", "内容一"), candidate("p2", "内容二"), candidate("p3", "内容三"));
    }

    /** 模拟一次 HTTP 调用并返回指定响应体，执行真实重排序逻辑 */
    private static List<RetrievalResult> rerankWithBody(Reranker reranker, String url,
                                                       List<RetrievalResult> candidates, int topK, String body) {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        try (MockedStatic<HttpRequest> mocked = mockStatic(HttpRequest.class)) {
            mocked.when(() -> HttpRequest.post(url)).thenReturn(request);
            stubRequestChain(request);
            when(request.execute()).thenReturn(response);
            when(response.body()).thenReturn(body);
            return reranker.rerank("查询", candidates, topK);
        }
    }

    /** hutool 请求对象为链式调用：显式打桩 header/body/timeout 返回请求对象自身 */
    private static void stubRequestChain(HttpRequest request) {
        when(request.header(anyString(), anyString())).thenReturn(request);
        when(request.body(anyString())).thenReturn(request);
        when(request.timeout(anyInt())).thenReturn(request);
    }

    @Nested
    class Bge客户端 {

        private BgeRerankerClient bge;

        @BeforeEach
        void setUp() {
            bge = new BgeRerankerClient();
            ReflectionTestUtils.setField(bge, "baseUrl", "http://rerank.local:8081");
        }

        @Test
        void getType与空候选应快速返回() {
            assertThat(bge.getType()).isEqualTo("bge");
            assertThat(bge.rerank("查询", null, 3)).isEmpty();
            assertThat(bge.rerank("查询", List.of(), 3)).isEmpty();
        }

        @Test
        void baseUrl未配置时应截断返回原候选() {
            ReflectionTestUtils.setField(bge, "baseUrl", "  ");
            List<RetrievalResult> candidates = threeCandidates();

            List<RetrievalResult> result = bge.rerank("查询", candidates, 2);

            assertThat(result).containsExactly(candidates.get(0), candidates.get(1));
            assertThat(result).allSatisfy(r -> assertThat(r.getFinalScore()).isEqualTo(0.1));
        }

        @Test
        void 正常响应应按score降序截断并更新分数() {
            String json = "[{\"index\":2,\"score\":0.91},{\"index\":0,\"score\":0.77},{\"index\":1,\"score\":0.55}]";
            List<RetrievalResult> candidates = threeCandidates();
            HttpRequest request = mock(HttpRequest.class);
            HttpResponse response = mock(HttpResponse.class);
            try (MockedStatic<HttpRequest> mocked = mockStatic(HttpRequest.class)) {
                mocked.when(() -> HttpRequest.post("http://rerank.local:8081/rerank")).thenReturn(request);
                stubRequestChain(request);
                when(request.execute()).thenReturn(response);
                when(response.body()).thenReturn(json);

                List<RetrievalResult> result = bge.rerank("查询", candidates, 2);

                assertThat(result).extracting(RetrievalResult::getContent).containsExactly("内容三", "内容一");
                assertThat(result.get(0).getFinalScore()).isEqualTo(0.91);
                assertThat(result.get(1).getFinalScore()).isEqualTo(0.77);
                ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
                verify(request).body(bodyCaptor.capture());
                assertThat(bodyCaptor.getValue())
                        .contains("\"query\":\"查询\"")
                        .contains("\"top_n\":2")
                        .contains("内容一");
            }
        }

        @Test
        void 越界index应被跳过只返回有效结果() {
            String json = "[{\"index\":9,\"score\":0.99},{\"index\":1,\"score\":0.42}]";

            List<RetrievalResult> result = rerankWithBody(bge, "http://rerank.local:8081/rerank",
                    threeCandidates(), 3, json);

            assertThat(result).extracting(RetrievalResult::getContent).containsExactly("内容二");
            assertThat(result.get(0).getFinalScore()).isEqualTo(0.42);
        }

        @Test
        void 响应为空或空数组时应降级返回粗排结果() {
            List<RetrievalResult> candidates = threeCandidates();

            assertThat(rerankWithBody(bge, "http://rerank.local:8081/rerank", candidates, 2, null))
                    .containsExactly(candidates.get(0), candidates.get(1));
            assertThat(rerankWithBody(bge, "http://rerank.local:8081/rerank", candidates, 2, "[]"))
                    .containsExactly(candidates.get(0), candidates.get(1));
        }

        @Test
        void HTTP调用异常时应降级返回粗排结果() {
            List<RetrievalResult> candidates = threeCandidates();
            HttpRequest request = mock(HttpRequest.class);
            try (MockedStatic<HttpRequest> mocked = mockStatic(HttpRequest.class)) {
                mocked.when(() -> HttpRequest.post("http://rerank.local:8081/rerank")).thenReturn(request);
                stubRequestChain(request);
                when(request.execute()).thenThrow(new RuntimeException("连接被拒绝"));

                List<RetrievalResult> result = bge.rerank("查询", candidates, 2);

                assertThat(result).containsExactly(candidates.get(0), candidates.get(1));
            }
        }
    }

    @Nested
    class Cohere客户端 {

        private CohereReranker cohere;

        @BeforeEach
        void setUp() {
            cohere = new CohereReranker();
            ReflectionTestUtils.setField(cohere, "baseUrl", "https://cohere.test");
            ReflectionTestUtils.setField(cohere, "apiKey", "test-key");
            ReflectionTestUtils.setField(cohere, "model", "rerank-multilingual-v3.0");
        }

        @Test
        void getType与空候选应快速返回() {
            assertThat(cohere.getType()).isEqualTo("cohere");
            assertThat(cohere.rerank("查询", null, 3)).isEmpty();
            assertThat(cohere.rerank("查询", List.of(), 3)).isEmpty();
        }

        @Test
        void apiKey未配置时应截断返回原候选() {
            ReflectionTestUtils.setField(cohere, "apiKey", " ");
            List<RetrievalResult> candidates = threeCandidates();

            List<RetrievalResult> result = cohere.rerank("查询", candidates, 2);

            assertThat(result).containsExactly(candidates.get(0), candidates.get(1));
            assertThat(result).allSatisfy(r -> assertThat(r.getFinalScore()).isEqualTo(0.1));
        }

        @Test
        void 正常响应应按返回顺序重组并更新分数() {
            String json = "{\"results\":[{\"index\":2,\"relevance_score\":0.95},{\"index\":1,\"relevance_score\":0.60}]}";
            List<RetrievalResult> candidates = threeCandidates();
            HttpRequest request = mock(HttpRequest.class);
            HttpResponse response = mock(HttpResponse.class);
            try (MockedStatic<HttpRequest> mocked = mockStatic(HttpRequest.class)) {
                mocked.when(() -> HttpRequest.post("https://cohere.test/v1/rerank")).thenReturn(request);
                stubRequestChain(request);
                when(request.execute()).thenReturn(response);
                when(response.body()).thenReturn(json);

                List<RetrievalResult> result = cohere.rerank("查询", candidates, 2);

                assertThat(result).extracting(RetrievalResult::getContent).containsExactly("内容三", "内容二");
                assertThat(result.get(0).getFinalScore()).isEqualTo(0.95);
                assertThat(result.get(1).getFinalScore()).isEqualTo(0.60);
                ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
                verify(request).body(bodyCaptor.capture());
                assertThat(bodyCaptor.getValue())
                        .contains("\"model\":\"rerank-multilingual-v3.0\"")
                        .contains("\"documents\":[\"内容一\",\"内容二\",\"内容三\"]");
                verify(request).header("Authorization", "Bearer test-key");
            }
        }

        @Test
        void results缺失时应降级返回粗排结果() {
            String json = "{\"meta\":{\"billed_units\":{}}}";
            List<RetrievalResult> candidates = threeCandidates();

            List<RetrievalResult> result = rerankWithBody(cohere, "https://cohere.test/v1/rerank",
                    candidates, 2, json);

            assertThat(result).containsExactly(candidates.get(0), candidates.get(1));
        }

        @Test
        void 越界index应被跳过() {
            String json = "{\"results\":[{\"index\":7,\"relevance_score\":0.9}]}";

            List<RetrievalResult> result = rerankWithBody(cohere, "https://cohere.test/v1/rerank",
                    threeCandidates(), 3, json);

            assertThat(result).isEmpty();
        }

        @Test
        void 响应为空或调用异常时应降级返回粗排结果() {
            List<RetrievalResult> candidates = threeCandidates();

            assertThat(rerankWithBody(cohere, "https://cohere.test/v1/rerank", candidates, 2, ""))
                    .containsExactly(candidates.get(0), candidates.get(1));

            HttpRequest request = mock(HttpRequest.class);
            try (MockedStatic<HttpRequest> mocked = mockStatic(HttpRequest.class)) {
                mocked.when(() -> HttpRequest.post("https://cohere.test/v1/rerank")).thenReturn(request);
                stubRequestChain(request);
                when(request.execute()).thenThrow(new RuntimeException("网关超时"));

                assertThat(cohere.rerank("查询", candidates, 2))
                        .containsExactly(candidates.get(0), candidates.get(1));
            }
        }
    }
}
