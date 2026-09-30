package cn.cangjiecloud.core.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatTraceContext} 单元测试，覆盖 listener attributes 双向转换。
 */
class ChatTraceContextTest {

    @Test
    void toListenerAttributesShouldOnlyIncludeNonNullFields() {
        ChatTraceContext context = ChatTraceContext.builder()
                .traceId("trace-1").runId("run-1").modelName("gpt-4o")
                .build();

        Map<Object, Object> attributes = context.toListenerAttributes();

        assertThat(attributes)
                .containsEntry(ChatTraceContext.KEY_TRACE_ID, "trace-1")
                .containsEntry(ChatTraceContext.KEY_RUN_ID, "run-1")
                .containsEntry(ChatTraceContext.KEY_MODEL_NAME, "gpt-4o")
                .hasSize(3);
    }

    @Test
    void fromListenerAttributesShouldRestoreContext() {
        Map<Object, Object> attributes = ChatTraceContext.builder()
                .traceId("trace-1").sessionId("sess-1").appId("app-1")
                .build()
                .toListenerAttributes();

        ChatTraceContext context = ChatTraceContext.fromListenerAttributes(attributes);

        assertThat(context).isNotNull();
        assertThat(context.getTraceId()).isEqualTo("trace-1");
        assertThat(context.getSessionId()).isEqualTo("sess-1");
        assertThat(context.getAppId()).isEqualTo("app-1");
    }

    @Test
    void roundTripShouldPreserveAllSetFields() {
        ChatTraceContext original = ChatTraceContext.builder()
                .traceId("t").runId("r").requestId("req").appId("a").appName("n")
                .sessionId("s").userId("u").modelId("m").modelName("mn")
                .build();

        ChatTraceContext restored = ChatTraceContext.fromListenerAttributes(original.toListenerAttributes());

        assertThat(restored).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void fromListenerAttributesShouldReturnNullForNullEmptyOrNoBusinessFields() {
        assertThat(ChatTraceContext.fromListenerAttributes(null)).isNull();
        assertThat(ChatTraceContext.fromListenerAttributes(Map.of())).isNull();
        assertThat(ChatTraceContext.fromListenerAttributes(Map.of("unrelated", "x"))).isNull();
    }
}
