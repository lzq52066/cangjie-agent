package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeNode} 单元测试：知识库 ID 解析、query 优先级、topK 默认值、结果拼接与映射。
 */
class KnowledgeNodeTest {

    private HybridRetriever retriever;
    private KnowledgeNode node;

    @BeforeEach
    void setUp() {
        retriever = mock(HybridRetriever.class);
        node = new KnowledgeNode(retriever);
    }

    @Test
    // 覆盖场景：节点元信息
    void metadataShouldDescribeKnowledgeNode() {
        assertThat(node.getType()).isEqualTo("knowledge");
        assertThat(node.getName()).isEqualTo("知识库");
        assertThat(node.getDescription()).contains("检索");
    }

    @Test
    // 覆盖场景：未配置知识库 ID（缺失/空串/空 List/config null）—— 返回空结果且不触发检索
    void executeShouldReturnEmptyWhenNoKnowledgeBaseIds() {
        assertThat(node.execute(Map.of("query", "q"), Map.of()))
                .containsOnlyKeys("knowledge_output", "knowledge_results")
                .containsEntry("knowledge_output", "")
                .containsEntry("knowledge_results", List.of());
        assertThat(node.execute(Map.of(), Map.of("knowledgeBaseIds", "")))
                .containsEntry("knowledge_output", "");
        assertThat(node.execute(Map.of(), Map.of("knowledgeBaseIds", List.of())))
                .containsEntry("knowledge_results", List.of());
        assertThat(node.execute(null, null))
                .containsEntry("knowledge_output", "");
        verifyNoInteractions(retriever);
    }

    @Test
    // 覆盖场景：List 型 knowledgeBaseIds + 显式 topK —— 参数原样传递给检索器
    void executeShouldPassListIdsAndExplicitTopK() {
        when(retriever.retrieve(any(), anyList(), anyInt())).thenReturn(List.of());

        node.execute(Map.of("query", "问题"),
                Map.of("knowledgeBaseIds", List.of("kb1", 2), "topK", 3));

        verify(retriever).retrieve("问题", List.of("kb1", "2"), 3);
    }

    @Test
    // 覆盖场景：逗号分隔字符串型 knowledgeBaseIds —— 拆分为列表
    void executeShouldSplitCommaSeparatedIds() {
        when(retriever.retrieve(any(), anyList(), anyInt())).thenReturn(List.of());

        node.execute(Map.of("question", "问句"), Map.of("knowledgeBaseIds", "kb-a,kb-b"));

        verify(retriever).retrieve("问句", List.of("kb-a", "kb-b"), 5);
    }

    @Test
    // 覆盖场景：query 取值优先级 query > question > input，全缺失时以空串检索
    void executeShouldResolveQueryByPriority() {
        when(retriever.retrieve(anyString(), anyList(), anyInt())).thenReturn(List.of());
        Map<String, Object> config = Map.of("knowledgeBaseIds", "kb1");

        node.execute(Map.of("query", "Q", "question", "qq", "input", "ii"), config);
        node.execute(Map.of("question", "qq", "input", "ii"), config);
        node.execute(Map.of("input", "ii"), config);
        node.execute(Map.of(), config);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(retriever, org.mockito.Mockito.times(4))
                .retrieve(captor.capture(), anyList(), anyInt());
        assertThat(captor.getAllValues()).containsExactly("Q", "qq", "ii", "");
    }

    @Test
    // 覆盖场景：检索成功 —— content 以空行拼接，结果映射含 content/score/documentId
    void executeShouldJoinContentsAndMapResults() {
        when(retriever.retrieve(any(), anyList(), anyInt())).thenReturn(List.of(
                RetrievalResult.builder().content("段落A").finalScore(0.9).documentId("doc1").build(),
                RetrievalResult.builder().content("段落B").finalScore(0.5).documentId("doc2").build()));

        Map<String, Object> output = node.execute(
                Map.of("query", "查什么"),
                Map.of("knowledgeBaseIds", "kb1"));

        assertThat(output).containsEntry("knowledge_output", "段落A\n\n段落B");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("knowledge_results");
        assertThat(results).hasSize(2);
        assertThat(results.get(0))
                .containsEntry("content", "段落A")
                .containsEntry("score", 0.9)
                .containsEntry("documentId", "doc1");
        assertThat(results.get(1))
                .containsEntry("content", "段落B")
                .containsEntry("score", 0.5)
                .containsEntry("documentId", "doc2");
    }
}
