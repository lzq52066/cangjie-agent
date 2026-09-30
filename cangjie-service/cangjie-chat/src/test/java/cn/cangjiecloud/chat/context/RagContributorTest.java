package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.QueryRewriter;
import cn.cangjiecloud.core.rag.QueryRewriterFactory;
import cn.cangjiecloud.core.rag.RetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RagContributor} 单元测试：常规检索产出片段、空结果/异常回落空片段、查询改写分支。
 */
class RagContributorTest {

    private HybridRetriever hybridRetriever;
    private QueryRewriterFactory queryRewriterFactory;
    private TraceCollector traceCollector;
    private RagContributor contributor;

    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        hybridRetriever = mock(HybridRetriever.class);
        queryRewriterFactory = mock(QueryRewriterFactory.class);
        traceCollector = mock(TraceCollector.class);
        contributor = new RagContributor(hybridRetriever, queryRewriterFactory);
        ReflectionTestUtils.setField(contributor, "traceCollector", traceCollector);
        ReflectionTestUtils.setField(contributor, "queryRewriteEnabled", false);
        ReflectionTestUtils.setField(contributor, "queryRewriteType", "none");
        attributes = new HashMap<>();
    }

    private ContextRequest request(String ragMode) {
        return ContextRequest.builder()
                .userQuery("检索词").knowledgeBaseIds(List.of("kb-1")).ragMode(ragMode)
                .traceId("trace-1").attributes(attributes)
                .build();
    }

    private static RetrievalResult result(String content, String documentId, String title) {
        return RetrievalResult.builder()
                .paragraphId("p-1").documentId(documentId).knowledgeBaseId("kb-1")
                .content(content).finalScore(0.87)
                .metadata(title == null ? null : Map.of("title", title))
                .build();
    }

    @Test
    void metadataShouldDeclareRagSlot() {
        assertThat(contributor.name()).isEqualTo("rag");
        assertThat(contributor.slot()).isEqualTo(ContextSlot.RAG);
    }

    @Nested
    class Supports {

        @Test
        void shouldSupportWhenKnowledgeBasesPresentAndNotAgentic() {
            assertThat(contributor.supports(request("simple"))).isTrue();
        }

        @Test
        void shouldNotSupportWhenKnowledgeBasesMissing() {
            ContextRequest request = ContextRequest.builder()
                    .attributes(attributes).ragMode("simple").build();
            assertThat(contributor.supports(request)).isFalse();
        }

        @Test
        void shouldNotSupportWhenAgenticMode() {
            assertThat(contributor.supports(request("agentic"))).isFalse();
        }
    }

    @Nested
    class Contribute {

        @Test
        void shouldProduceRagFragmentAndCollectSources() {
            List<Map<String, Object>> sources = new java.util.ArrayList<>();
            attributes.put(ContextAttrs.RETRIEVAL_SOURCES, sources);
            when(hybridRetriever.retrieve(anyString(), any(List.class), anyInt()))
                    .thenReturn(List.of(result("片段正文", "doc-1", "产品手册")));

            ContextFragment fragment = contributor.contribute(request("simple"));

            assertThat(fragment.isEmpty()).isFalse();
            String content = fragment.getMessages().get(0).getContent();
            assertThat(content).contains("知识库").contains("产品手册").contains("片段正文");
            assertThat(sources).hasSize(1);
            assertThat(sources.get(0))
                    .containsEntry("documentName", "产品手册")
                    .containsEntry("paragraphId", "p-1")
                    .containsEntry("score", 0.87);
            verify(traceCollector).record(anyString(), anyString(), anyString(),
                    org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString());
        }

        @Test
        void shouldFallBackToDocumentIdWhenTitleMetadataMissing() {
            when(hybridRetriever.retrieve(anyString(), any(List.class), anyInt()))
                    .thenReturn(List.of(result("正文", "doc-9", null)));

            ContextFragment fragment = contributor.contribute(request("simple"));

            assertThat(fragment.getMessages().get(0).getContent()).contains("doc-9");
        }

        @Test
        void shouldReturnEmptyWhenRetrieverReturnsNoResults() {
            when(hybridRetriever.retrieve(anyString(), any(List.class), anyInt()))
                    .thenReturn(List.of());

            assertThat(contributor.contribute(request("simple")).isEmpty()).isTrue();
        }

        @Test
        void shouldReturnEmptyWhenRetrieverThrows() {
            when(hybridRetriever.retrieve(anyString(), any(List.class), anyInt()))
                    .thenThrow(new RuntimeException("检索服务不可用"));

            assertThat(contributor.contribute(request("simple")).isEmpty()).isTrue();
        }

        @Test
        void shouldRewriteQueryWhenRewriteEnabled() {
            ReflectionTestUtils.setField(contributor, "queryRewriteEnabled", true);
            ReflectionTestUtils.setField(contributor, "queryRewriteType", "llm");
            QueryRewriter rewriter = mock(QueryRewriter.class);
            when(queryRewriterFactory.get("llm")).thenReturn(rewriter);
            when(rewriter.rewrite(anyString(), any())).thenReturn("改写后的检索词");
            when(hybridRetriever.retrieve(anyString(), any(List.class), anyInt()))
                    .thenReturn(List.of());

            contributor.contribute(request("simple"));

            verify(rewriter).rewrite(eq("检索词"), any());
            verify(hybridRetriever).retrieve(eq("改写后的检索词"), any(List.class), anyInt());
        }

        @Test
        void shouldSkipTraceCollectionWhenTraceCollectorAbsent() {
            ReflectionTestUtils.setField(contributor, "traceCollector", null);
            when(hybridRetriever.retrieve(anyString(), any(List.class), anyInt()))
                    .thenReturn(List.of());

            assertThat(contributor.contribute(request("simple")).isEmpty()).isTrue();
            verify(traceCollector, never()).record(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        }
    }
}
