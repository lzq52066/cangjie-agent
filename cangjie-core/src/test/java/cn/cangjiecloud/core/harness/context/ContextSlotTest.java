package cn.cangjiecloud.core.harness.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContextSlot} 单元测试。
 */
class ContextSlotTest {

    @Test
    void orderShouldFollowDeclarationSequence() {
        assertThat(ContextSlot.SYSTEM_PRE.order()).isZero();
        assertThat(ContextSlot.SYSTEM.order()).isEqualTo(1);
        assertThat(ContextSlot.RAG.order()).isEqualTo(2);
        assertThat(ContextSlot.MEMORY.order()).isEqualTo(3);
        assertThat(ContextSlot.HISTORY.order()).isEqualTo(4);
        assertThat(ContextSlot.TOOL_RESULT.order()).isEqualTo(5);
        assertThat(ContextSlot.USER.order()).isEqualTo(6);
    }

    @Test
    void compressibleShouldMarkSystemAndUserAsFixed() {
        // 系统槽位与本轮输入不可压缩
        assertThat(ContextSlot.SYSTEM_PRE.compressible()).isFalse();
        assertThat(ContextSlot.SYSTEM.compressible()).isFalse();
        assertThat(ContextSlot.USER.compressible()).isFalse();
        // 其余槽位可压缩
        assertThat(ContextSlot.RAG.compressible()).isTrue();
        assertThat(ContextSlot.MEMORY.compressible()).isTrue();
        assertThat(ContextSlot.HISTORY.compressible()).isTrue();
        assertThat(ContextSlot.TOOL_RESULT.compressible()).isTrue();
    }

    @Test
    void keyShouldBeLowerSnakeCaseOfName() {
        assertThat(ContextSlot.SYSTEM_PRE.key()).isEqualTo("system_pre");
        assertThat(ContextSlot.RAG.key()).isEqualTo("rag");
        assertThat(ContextSlot.TOOL_RESULT.key()).isEqualTo("tool_result");
    }

    @Test
    void ofShouldParseByEnumNameOrKeyIgnoringCaseAndWhitespace() {
        assertThat(ContextSlot.of("RAG")).isEqualTo(ContextSlot.RAG);
        assertThat(ContextSlot.of("rag")).isEqualTo(ContextSlot.RAG);
        assertThat(ContextSlot.of(" SYSTEM_PRE ")).isEqualTo(ContextSlot.SYSTEM_PRE);
        assertThat(ContextSlot.of("tool_result")).isEqualTo(ContextSlot.TOOL_RESULT);
    }

    @Test
    void ofShouldReturnNullForNullOrUnknownValue() {
        assertThat(ContextSlot.of(null)).isNull();
        assertThat(ContextSlot.of("")).isNull();
        assertThat(ContextSlot.of("no-such-slot")).isNull();
    }
}
