package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.QueryRewriter;
import cn.cangjiecloud.core.rag.QueryRewriterFactory;
import cn.cangjiecloud.core.rag.RetrievalOptions;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.knowledge.api.dto.RetrievalQueryDTO;
import cn.cangjiecloud.knowledge.api.dto.RetrievalResultDTO;
import cn.cangjiecloud.knowledge.rag.PgVectorStore;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import cn.cangjiecloud.knowledge.service.IKnowledgeProblemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RetrievalServiceImpl} 单元测试。
 * <p>
 * 覆盖数据权限校验、检索口径组装（知识库范围 / topK / 阈值 / 模式透传）、查询改写开关、
 * 问题路召回合并与异常吞没，不连数据库、不发起真实检索。
 * <p>
 * 检索模式解析已下沉到 {@code HybridRetrieverImpl}（按知识库配置 + 本地缓存），这一层不再
 * 自己查库，因此本测只断言「调用方给了就透传、没给就留 null」。
 */
@ExtendWith(MockitoExtension.class)
class RetrievalServiceImplTest {

    @Mock
    private HybridRetriever hybridRetriever;
    @Mock
    private QueryRewriterFactory queryRewriterFactory;
    @Mock
    private IKnowledgeBaseService knowledgeBaseService;
    @Mock
    private IKnowledgeProblemService knowledgeProblemService;
    @Mock
    private PgVectorStore pgVectorStore;
    @Mock
    private QueryRewriter queryRewriter;

    private RetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RetrievalServiceImpl(hybridRetriever, queryRewriterFactory,
                knowledgeBaseService, knowledgeProblemService, pgVectorStore);
        ReflectionTestUtils.setField(service, "queryRewriteType", "none");
    }

    private static RetrievalQueryDTO query(String text) {
        RetrievalQueryDTO dto = new RetrievalQueryDTO();
        dto.setQuery(text);
        return dto;
    }

    private static RetrievalResult result(String paragraphId) {
        return RetrievalResult.builder()
                .paragraphId(paragraphId)
                .documentId("doc-" + paragraphId)
                .knowledgeBaseId("kb1")
                .content("内容-" + paragraphId)
                .vectorScore(0.8)
                .fullTextScore(0.5)
                .finalScore(0.7)
                .metadata(Map.of("title", "标题-" + paragraphId))
                .build();
    }

    private void stubRetriever(String queryText, RetrievalResult... results) {
        when(hybridRetriever.retrieve(eq(queryText), any(RetrievalOptions.class)))
                .thenReturn(List.of(results));
    }

    private RetrievalOptions capturedOptions() {
        ArgumentCaptor<RetrievalOptions> captor = ArgumentCaptor.forClass(RetrievalOptions.class);
        verify(hybridRetriever).retrieve(anyString(), captor.capture());
        return captor.getValue();
    }

    @Nested
    class AccessAndRouting {

        @Test
        void shouldThrowWhenNoKnowledgeBaseSpecified() {
            // 异常场景：既未传单库 ID 也未传多库列表
            RetrievalQueryDTO dto = query("问题");

            assertThatThrownBy(() -> service.retrieve(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("必须指定");
            verifyNoInteractions(hybridRetriever);
        }

        @Test
        void shouldCheckAccessAndMapSingleBaseResult() {
            // 正常场景：单库检索组装成完整口径，结果映射为 DTO（含 documentName）
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            stubRetriever("问题", result("p1"));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            verify(knowledgeBaseService).checkAccess("kb1");
            assertThat(capturedOptions().getKnowledgeBaseIds()).containsExactly("kb1");
            assertThat(results).hasSize(1);
            RetrievalResultDTO first = results.get(0);
            assertThat(first.getParagraphId()).isEqualTo("p1");
            assertThat(first.getDocumentId()).isEqualTo("doc-p1");
            assertThat(first.getDocumentName()).isEqualTo("标题-p1");
            assertThat(first.getMatchedProblem()).isNull();
        }

        @Test
        void shouldCheckAccessForEachBaseWhenMultiple() {
            // 正常场景：多库检索逐一校验数据权限，知识库列表整体交给检索器
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseIds(List.of("kb1", "kb2"));
            stubRetriever("问题", result("p1"));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            verify(knowledgeBaseService).checkAccess("kb1");
            verify(knowledgeBaseService).checkAccess("kb2");
            assertThat(capturedOptions().getKnowledgeBaseIds()).containsExactly("kb1", "kb2");
            assertThat(results).hasSize(1);
        }

        @Test
        void shouldPassExplicitSearchModeThroughWithoutResolvingItHere() {
            // 调用方显式指定模式（命中测试对比 simple / two_stage）时原样透传
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseIds(List.of("kb1", "kb2"));
            dto.setSearchMode("two_stage");
            stubRetriever("问题", result("p1"));

            service.retrieve(dto);

            RetrievalOptions options = capturedOptions();
            assertThat(options.getSearchMode()).isEqualTo("two_stage");
            // 不重复查知识库配置：解析归检索侧（多库一致才启用两级）
            verify(knowledgeBaseService, never()).getById(anyString());
        }

        @Test
        void shouldLeaveSearchModeUnsetWhenCallerOmitsIt() {
            // 边界场景：没传模式时留 null，由检索侧按知识库配置解析，而不是在这里写死 simple
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            stubRetriever("问题", result("p1"));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            assertThat(capturedOptions().getSearchMode()).isNull();
            assertThat(results).hasSize(1);
        }

        @Test
        void shouldUseDefaultTopKAndThresholdWhenNull() {
            // 边界场景：topK/相似度阈值显式为 null 时取默认 5/0.0
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            dto.setTopK(null);
            dto.setSimilarityThreshold(null);
            stubRetriever("问题", result("p1"));

            service.retrieve(dto);

            RetrievalOptions options = capturedOptions();
            assertThat(options.getTopK()).isEqualTo(5);
            assertThat(options.getSimilarityThreshold()).isEqualTo(0.0);
        }
    }

    @Nested
    class QueryRewrite {

        @Test
        void shouldRewriteQueryWhenEnabled() {
            // 正常场景：开启改写后使用改写结果检索
            RetrievalQueryDTO dto = query("它怎么配置");
            dto.setKnowledgeBaseId("kb1");
            dto.setRewrite(true);
            when(queryRewriterFactory.get("none")).thenReturn(queryRewriter);
            when(queryRewriter.rewrite("它怎么配置", List.of())).thenReturn("RAG 怎么配置");
            stubRetriever("RAG 怎么配置", result("p1"));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            assertThat(results).hasSize(1);
            verify(hybridRetriever).retrieve(eq("RAG 怎么配置"), any(RetrievalOptions.class));
        }

        @Test
        void shouldNotRewriteWhenDisabled() {
            // 边界场景：未开启改写时直接用原始 query，不触达改写器工厂
            RetrievalQueryDTO dto = query("原始问题");
            dto.setKnowledgeBaseId("kb1");
            stubRetriever("原始问题", result("p1"));

            service.retrieve(dto);

            verifyNoInteractions(queryRewriterFactory);
        }
    }

    @Nested
    class ProblemRecallMerge {

        @Test
        void shouldMergeProblemParagraphsAndMarkMatched() {
            // 正常场景：已命中段落补问题标记，遗漏段落按问题路补入且 finalScore=1.0
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            stubRetriever("问题", result("p1"));
            Map<String, String> recall = new LinkedHashMap<>();
            recall.put("p1", "常见问题一");
            recall.put("p2", "常见问题二");
            when(knowledgeProblemService.recallParagraphs(List.of("kb1"), "问题", 5)).thenReturn(recall);
            when(pgVectorStore.getByParagraphIds(List.of("p2"), 1))
                    .thenReturn(List.of(RetrievalResult.builder()
                            .paragraphId("p2").documentId("doc-p2").knowledgeBaseId("kb1")
                            .content("内容-p2").finalScore(0.3).build()));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getParagraphId()).isEqualTo("p1");
            assertThat(results.get(0).getMatchedProblem()).isEqualTo("常见问题一");
            assertThat(results.get(1).getParagraphId()).isEqualTo("p2");
            assertThat(results.get(1).getMatchedProblem()).isEqualTo("常见问题二");
            assertThat(results.get(1).getFinalScore()).isEqualTo(1.0);
        }

        @Test
        void shouldNotQueryVectorStoreWhenAllRecalledParagraphsSeen() {
            // 边界场景：问题路命中的段落全部已出现在主检索结果中，无需补查向量库
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            stubRetriever("问题", result("p1"));
            when(knowledgeProblemService.recallParagraphs(List.of("kb1"), "问题", 5))
                    .thenReturn(Map.of("p1", "常见问题一"));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMatchedProblem()).isEqualTo("常见问题一");
            verify(pgVectorStore, never()).getByParagraphIds(anyList(), anyInt());
        }

        @Test
        void shouldSwallowProblemRecallFailure() {
            // 异常场景：问题路召回异常被吞掉，主检索结果照常返回
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            stubRetriever("问题", result("p1"));
            when(knowledgeProblemService.recallParagraphs(List.of("kb1"), "问题", 5))
                    .thenThrow(new RuntimeException("问题服务超时"));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMatchedProblem()).isNull();
        }

        @Test
        void shouldSkipProblemRecallWhenDisabled() {
            // 边界场景：enableProblem=false 时完全跳过问题路召回
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            dto.setEnableProblem(false);
            stubRetriever("问题", result("p1"));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            assertThat(results).hasSize(1);
            verifyNoInteractions(knowledgeProblemService);
        }
    }
}
