package cn.cangjiecloud.chat.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatMessageEntity} 单元测试：验证字段读写与继承自 {@code BaseEntity} 的属性。
 */
class ChatMessageEntityTest {

    /** 各字段可正常读写，equals/hashCode 按 Lombok 约定工作 */
    @Test
    void shouldReadAndWriteAllFields() {
        LocalDateTime annotateTime = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId("msg-1");
        entity.setSessionId("session-1");
        entity.setApplicationId("app-1");
        entity.setRole("assistant");
        entity.setContent("你好");
        entity.setTokens(42);
        entity.setRetrievalSources("[]");
        entity.setMetadata("{}");
        entity.setDuration(120L);
        entity.setFeedback("like");
        entity.setAnnotation("修正答案");
        entity.setAnnotateBy("admin");
        entity.setAnnotateTime(annotateTime);

        assertThat(entity.getId()).isEqualTo("msg-1");
        assertThat(entity.getSessionId()).isEqualTo("session-1");
        assertThat(entity.getApplicationId()).isEqualTo("app-1");
        assertThat(entity.getRole()).isEqualTo("assistant");
        assertThat(entity.getContent()).isEqualTo("你好");
        assertThat(entity.getTokens()).isEqualTo(42);
        assertThat(entity.getRetrievalSources()).isEqualTo("[]");
        assertThat(entity.getMetadata()).isEqualTo("{}");
        assertThat(entity.getDuration()).isEqualTo(120L);
        assertThat(entity.getFeedback()).isEqualTo("like");
        assertThat(entity.getAnnotation()).isEqualTo("修正答案");
        assertThat(entity.getAnnotateBy()).isEqualTo("admin");
        assertThat(entity.getAnnotateTime()).isEqualTo(annotateTime);
    }

    /** 两个字段相同的实体相等 */
    @Test
    void shouldBeEqualWhenSameValues() {
        ChatMessageEntity first = new ChatMessageEntity();
        first.setId("x");
        ChatMessageEntity second = new ChatMessageEntity();
        second.setId("x");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
