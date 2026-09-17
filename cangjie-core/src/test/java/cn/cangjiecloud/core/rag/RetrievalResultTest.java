package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RetrievalResult} 单元测试，含分数边界值。
 */
class RetrievalResultTest {

    @Test
    void builderShouldPopulateAllFields() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("page", 3);

        RetrievalResult result = RetrievalResult.builder()
                .paragraphId("p-1")
                .documentId("d-1")
                .knowledgeBaseId("kb-1")
                .content("段落内容")
                .vectorScore(0.91)
                .fullTextScore(0.34)
                .finalScore(0.032)
                .metadata(metadata)
                .build();

        assertThat(result.getParagraphId()).isEqualTo("p-1");
        assertThat(result.getDocumentId()).isEqualTo("d-1");
        assertThat(result.getKnowledgeBaseId()).isEqualTo("kb-1");
        assertThat(result.getContent()).isEqualTo("段落内容");
        assertThat(result.getVectorScore()).isEqualTo(0.91);
        assertThat(result.getFullTextScore()).isEqualTo(0.34);
        assertThat(result.getFinalScore()).isEqualTo(0.032);
        assertThat(result.getMetadata()).containsEntry("page", 3);
    }

    @Test
    void emptyBuilderShouldZeroAllScoresAndNullAllObjects() {
        RetrievalResult result = RetrievalResult.builder().build();

        assertThat(result.getVectorScore()).isZero();
        assertThat(result.getFullTextScore()).isZero();
        assertThat(result.getFinalScore()).isZero();
        assertThat(result.getParagraphId()).isNull();
        assertThat(result.getContent()).isNull();
        assertThat(result.getMetadata()).isNull();
    }

    @Test
    void noArgsConstructorAndSettersShouldRoundTrip() {
        RetrievalResult result = new RetrievalResult();
        Map<String, Object> metadata = new HashMap<>();
        result.setParagraphId("p");
        result.setDocumentId("d");
        result.setKnowledgeBaseId("k");
        result.setContent("c");
        result.setVectorScore(1.0);
        result.setFullTextScore(2.0);
        result.setFinalScore(3.0);
        result.setMetadata(metadata);

        assertThat(result.getParagraphId()).isEqualTo("p");
        assertThat(result.getDocumentId()).isEqualTo("d");
        assertThat(result.getKnowledgeBaseId()).isEqualTo("k");
        assertThat(result.getContent()).isEqualTo("c");
        assertThat(result.getVectorScore()).isEqualTo(1.0);
        assertThat(result.getFullTextScore()).isEqualTo(2.0);
        assertThat(result.getFinalScore()).isEqualTo(3.0);
        assertThat(result.getMetadata()).isSameAs(metadata);
    }

    @Test
    void allArgsConstructorShouldFollowFieldOrder() {
        Map<String, Object> metadata = Map.of("k", "v");
        RetrievalResult result = new RetrievalResult("p", "d", "kb", "c", 0.1, 0.2, 0.3, metadata);

        assertThat(result.getParagraphId()).isEqualTo("p");
        assertThat(result.getDocumentId()).isEqualTo("d");
        assertThat(result.getKnowledgeBaseId()).isEqualTo("kb");
        assertThat(result.getContent()).isEqualTo("c");
        assertThat(result.getVectorScore()).isEqualTo(0.1);
        assertThat(result.getFullTextScore()).isEqualTo(0.2);
        assertThat(result.getFinalScore()).isEqualTo(0.3);
        assertThat(result.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void scoreBoundariesShouldBeRepresentable() {
        RetrievalResult extreme = RetrievalResult.builder()
                .vectorScore(0.0).fullTextScore(-1.0).finalScore(Double.MAX_VALUE).build();

        assertThat(extreme.getVectorScore()).isZero();
        assertThat(extreme.getFullTextScore()).isEqualTo(-1.0);
        assertThat(extreme.getFinalScore()).isGreaterThan(1.0);
    }

    @Test
    void equalsAndHashCodeShouldBeValueBasedOnAllFields() {
        RetrievalResult a = RetrievalResult.builder().paragraphId("p").finalScore(0.5).build();
        RetrievalResult b = RetrievalResult.builder().paragraphId("p").finalScore(0.5).build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(RetrievalResult.builder().paragraphId("p").finalScore(0.6).build());
        assertThat(a).isNotEqualTo(RetrievalResult.builder().paragraphId("q").finalScore(0.5).build());
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo(42);
    }

    @Test
    void toStringShouldContainIdentifiersAndScores() {
        String text = RetrievalResult.builder().paragraphId("p1").knowledgeBaseId("kb1")
                .finalScore(0.25).build().toString();

        assertThat(text).contains("paragraphId=p1").contains("knowledgeBaseId=kb1").contains("finalScore=0.25");
    }
}
