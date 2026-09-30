package cn.cangjiecloud.chat.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatSessionEntity} 单元测试：验证会话各字段的读写与 Lombok 相等性。
 */
class ChatSessionEntityTest {

    /** 各字段可正常读写 */
    @Test
    void shouldReadAndWriteAllFields() {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId("row-1");
        entity.setApplicationId("app-1");
        entity.setSessionId("session-1");
        entity.setTitle("会话标题");
        entity.setUserId("user-1");
        entity.setSource("web");
        entity.setModelId("model-1");
        entity.setStatus("active");
        entity.setMessageCount(6);
        entity.setTokensUsed(1000);
        entity.setMetadata("{}");
        entity.setSummary("滚动摘要内容");
        entity.setSummaryMsgCount(4);

        assertThat(entity.getApplicationId()).isEqualTo("app-1");
        assertThat(entity.getSessionId()).isEqualTo("session-1");
        assertThat(entity.getTitle()).isEqualTo("会话标题");
        assertThat(entity.getUserId()).isEqualTo("user-1");
        assertThat(entity.getSource()).isEqualTo("web");
        assertThat(entity.getModelId()).isEqualTo("model-1");
        assertThat(entity.getStatus()).isEqualTo("active");
        assertThat(entity.getMessageCount()).isEqualTo(6);
        assertThat(entity.getTokensUsed()).isEqualTo(1000);
        assertThat(entity.getMetadata()).isEqualTo("{}");
        assertThat(entity.getSummary()).isEqualTo("滚动摘要内容");
        assertThat(entity.getSummaryMsgCount()).isEqualTo(4);
    }

    /** 两个字段相同的实体相等 */
    @Test
    void shouldBeEqualWhenSameValues() {
        ChatSessionEntity first = new ChatSessionEntity();
        first.setSessionId("s");
        ChatSessionEntity second = new ChatSessionEntity();
        second.setSessionId("s");

        assertThat(first).isEqualTo(second);
    }
}
