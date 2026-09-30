package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.QueryRewriter;
import cn.cangjiecloud.core.rag.QueryRewriterFactory;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.knowledge.api.dto.RetrievalQueryDTO;
import cn.cangjiecloud.knowledge.api.dto.RetrievalResultDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.rag.PgVectorStore;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import cn.cangjiecloud.knowledge.service.IKnowledgeProblemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RetrievalServiceImpl} 单元测试。
 * <p>
 * 覆盖数据权限校验、单库/多库检索路由、检索模式解析与降级、查询改写开关、
 * 问题路召回合并与异常吞没，不连数据库、不发起真实检索。
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

    private static KnowledgeBaseEntity knowledgeBase(String searchMode) {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId("kb1");
        kb.setSearchMode(searchMode);
        return kb;
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
            // 正常场景：单库检索走 4 参重载，结果映射为 DTO（含 documentName）
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            when(hybridRetriever.retrieve("问题", "kb1", 5, 0.0))
                    .thenReturn(List.of(result("p1")));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            verify(knowledgeBaseService).checkAccess("kb1");
            assertThat(results).hasSize(1);
            RetrievalResultDTO first = results.get(0);
            assertThat(first.getParagraphId()).isEqualTo("p1");
            assertThat(first.getDocumentId()).isEqualTo("doc-p1");
            assertThat(first.getDocumentName()).isEqualTo("标题-p1");
            assertThat(first.getMatchedProblem()).isNull();
        }

        @Test
        void shouldCheckAccessForEachBaseWhenMultiple() {
            // 正常场景：多库检索逐一校验数据权限，走 5 参重载
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseIds(List.of("kb1", "kb2"));
            when(hybridRetriever.retrieve("问题", List.of("kb1", "kb2"), 5, 0.0, "simple"))
                    .thenReturn(List.of(result("p1")));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            verify(knowledgeBaseService).checkAccess("kb1");
            verify(knowledgeBaseService).checkAccess("kb2");
            assertThat(results).hasSize(1);
        }

        @Test
        void shouldResolveTwoStageModeFromBaseConfig() {
            // 正常场景：指定 kbId 且配置 two_stage 时透传检索模式
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            dto.setKnowledgeBaseIds(List.of("kb1", "kb2"));
            when(knowledgeBaseService.getById("kb1")).thenReturn(knowledgeBase("two_stage"));
            when(hybridRetriever.retrieve("问题", List.of("kb1", "kb2"), 5, 0.0, "two_stage"))
                    .thenReturn(List.of(result("p1")));

            service.retrieve(dto);

            verify(hybridRetriever).retrieve("问题", List.of("kb1", "kb2"), 5, 0.0, "two_stage");
            // 列表校验 + 单库校验各触发一次
            verify(knowledgeBaseService, times(2)).checkAccess("kb1");
            verify(knowledgeBaseService).checkAccess("kb2");
        }

        @Test
        void shouldFallbackToSimpleWhenSearchModeLookupFails() {
            // 异常场景：读取知识库配置抛异常时降级 simple，不影响主检索
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            dto.setKnowledgeBaseIds(List.of("kb1", "kb2"));
            when(knowledgeBaseService.getById("kb1")).thenThrow(new RuntimeException("配置服务不可用"));
            when(hybridRetriever.retrieve("问题", List.of("kb1", "kb2"), 5, 0.0, "simple"))
                    .thenReturn(List.of(result("p1")));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            assertThat(results).hasSize(1);
        }

        @Test
        void shouldUseDefaultTopKAndThresholdWhenNull() {
            // 边界场景：topK/相似度阈值显式为 null 时取默认 5/0.0
            RetrievalQueryDTO dto = query("问题");
            dto.setKnowledgeBaseId("kb1");
            dto.setTopK(null);
            dto.setSimilarityThreshold(null);
            when(hybridRetriever.retrieve("问题", "kb1", 5, 0.0))
                    .thenReturn(List.of(result("p1")));

            service.retrieve(dto);

            verify(hybridRetriever).retrieve("问题", "kb1", 5, 0.0);
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
            when(hybridRetriever.retrieve("RAG 怎么配置", "kb1", 5, 0.0))
                    .thenReturn(List.of(result("p1")));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            assertThat(results).hasSize(1);
            verify(hybridRetriever).retrieve("RAG 怎么配置", "kb1", 5, 0.0);
        }

        @Test
        void shouldNotRewriteWhenDisabled() {
            // 边界场景：未开启改写时直接用原始 query，不触达改写器工厂
            RetrievalQueryDTO dto = query("原始问题");
            dto.setKnowledgeBaseId("kb1");
            when(hybridRetriever.retrieve("原始问题", "kb1", 5, 0.0))
                    .thenReturn(List.of(result("p1")));

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
            when(hybridRetriever.retrieve("问题", "kb1", 5, 0.0))
                    .thenReturn(List.of(result("p1")));
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
            when(hybridRetriever.retrieve("问题", "kb1", 5, 0.0))
                    .thenReturn(List.of(result("p1")));
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
            when(hybridRetriever.retrieve("问题", "kb1", 5, 0.0))
                    .thenReturn(List.of(result("p1")));
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
            when(hybridRetriever.retrieve("问题", "kb1", 5, 0.0))
                    .thenReturn(List.of(result("p1")));

            List<RetrievalResultDTO> results = service.retrieve(dto);

            assertThat(results).hasSize(1);
            verifyNoInteractions(knowledgeProblemService);
        }
    }
}
