package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolHandlerRegistry} 单元测试：验证按 toolType 注册/查询/包含判断，
 * 以及 {@code getAll()} 返回不可变快照、空值注册被忽略等边界行为。
 */
@DisplayName("工具处理器注册中心 - ToolHandlerRegistry")
class ToolHandlerRegistryTest {

    private ToolHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolHandlerRegistry();
    }

    /** 测试用最小处理器实现，仅用于标识实例 */
    static class StubHandler extends AbsToolHandler {

        private final String tag;

        StubHandler(String tag) {
            this.tag = tag;
        }

        @Override
        public ToolSpecification buildToolSpecification(ToolEntity entity) {
            return ToolSpecification.builder().toolId(entity.getId()).toolType(tag).build();
        }

        @Override
        public ToolExecuteResultDTO execute(ToolEntity entity, Map<String, Object> params) {
            return ToolExecuteResultDTO.builder().success(true).output(tag).build();
        }
    }

    @Nested
    @DisplayName("注册与查询")
    class RegisterAndGet {

        @Test
        @DisplayName("注册后可按 toolType 取回同一实例")
        void shouldReturnSameInstance() {
            StubHandler handler = new StubHandler("http");

            registry.register("HTTP", handler);

            assertThat(registry.get("HTTP")).isSameAs(handler);
            assertThat(registry.contains("HTTP")).isTrue();
        }

        @Test
        @DisplayName("未注册的类型返回 null 且 contains 为 false")
        void shouldReturnNullForUnknownType() {
            assertThat(registry.get("MCP")).isNull();
            assertThat(registry.contains("MCP")).isFalse();
        }

        @Test
        @DisplayName("同一类型重复注册时以最后一次为准")
        void shouldOverwriteOnReRegister() {
            StubHandler first = new StubHandler("first");
            StubHandler second = new StubHandler("second");

            registry.register("CUSTOM", first);
            registry.register("CUSTOM", second);

            assertThat(registry.get("CUSTOM")).isSameAs(second);
        }

        @Test
        @DisplayName("toolType 为 null 时忽略注册")
        void shouldIgnoreNullToolType() {
            registry.register(null, new StubHandler("x"));

            assertThat(registry.getAll()).isEmpty();
        }

        @Test
        @DisplayName("handler 为 null 时忽略注册")
        void shouldIgnoreNullHandler() {
            registry.register("HTTP", null);

            assertThat(registry.contains("HTTP")).isFalse();
        }
    }

    @Nested
    @DisplayName("getAll 快照")
    class GetAll {

        @Test
        @DisplayName("返回全部已注册处理器")
        void shouldReturnAllHandlers() {
            StubHandler http = new StubHandler("http");
            StubHandler mcp = new StubHandler("mcp");
            registry.register("HTTP", http);
            registry.register("MCP", mcp);

            assertThat(registry.getAll())
                    .hasSize(2)
                    .containsEntry("HTTP", http)
                    .containsEntry("MCP", mcp);
        }

        @Test
        @DisplayName("getAll 返回不可变副本，修改副本不影响注册中心")
        void shouldReturnImmutableCopy() {
            registry.register("HTTP", new StubHandler("http"));

            Map<String, AbsToolHandler> snapshot = registry.getAll();

            assertThat(snapshot).hasSize(1);
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> snapshot.put("MCP", new StubHandler("mcp")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
