package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AbsToolHandler} 单元测试：验证 function calling 名称解析规则
 * （自定义 functionName 优先，非法/保留名回退 tool_&lt;id&gt;）与批量转换行为。
 */
@DisplayName("工具处理器基类 - AbsToolHandler")
class AbsToolHandlerTest {

    /** 暴露 protected specName 的测试子类 */
    private static final TestHandler HANDLER = new TestHandler();

    static class TestHandler extends AbsToolHandler {

        String callName(ToolEntity entity) {
            return specName(entity);
        }

        @Override
        public ToolSpecification buildToolSpecification(ToolEntity entity) {
            return ToolSpecification.builder()
                    .toolId(entity.getId())
                    .name(specName(entity))
                    .toolType("TEST")
                    .build();
        }

        @Override
        public ToolExecuteResultDTO execute(ToolEntity entity, Map<String, Object> params) {
            return ToolExecuteResultDTO.builder().success(true).output(params).build();
        }
    }

    private static ToolEntity tool(String id, String functionName) {
        ToolEntity entity = new ToolEntity();
        entity.setId(id);
        entity.setName("工具-" + id);
        entity.setFunctionName(functionName);
        return entity;
    }

    @Nested
    @DisplayName("specName 名称解析")
    class SpecName {

        @Test
        @DisplayName("合法且非保留的函数名原样使用")
        void shouldUseCustomFunctionName() {
            assertThat(HANDLER.callName(tool("t1", "my_tool"))).isEqualTo("my_tool");
        }

        @Test
        @DisplayName("函数名首尾空白被裁剪后使用")
        void shouldTrimFunctionName() {
            assertThat(HANDLER.callName(tool("t1", "  my_tool  "))).isEqualTo("my_tool");
        }

        @Test
        @DisplayName("未配置函数名回退为 tool_<id>")
        void shouldFallbackWhenFunctionNameMissing() {
            assertThat(HANDLER.callName(tool("abc", null))).isEqualTo("tool_abc");
            assertThat(HANDLER.callName(tool("abc", "   "))).isEqualTo("tool_abc");
        }

        @Test
        @DisplayName("非法函数名（以数字开头/含非法字符）回退为 tool_<id>")
        void shouldFallbackWhenFunctionNameInvalid() {
            assertThat(HANDLER.callName(tool("abc", "1bad"))).isEqualTo("tool_abc");
            assertThat(HANDLER.callName(tool("abc", "bad name"))).isEqualTo("tool_abc");
        }

        @Test
        @DisplayName("保留前缀与保留全名回退为 tool_<id>")
        void shouldFallbackWhenFunctionNameReserved() {
            assertThat(HANDLER.callName(tool("abc", "tool_x"))).isEqualTo("tool_abc");
            assertThat(HANDLER.callName(tool("abc", "skill_x"))).isEqualTo("tool_abc");
            assertThat(HANDLER.callName(tool("abc", "search_knowledge_base"))).isEqualTo("tool_abc");
        }
    }

    @Nested
    @DisplayName("buildToolSpecifications 批量转换")
    class BuildSpecifications {

        @Test
        @DisplayName("按输入顺序逐条转换为规格")
        void shouldConvertAllEntitiesInOrder() {
            List<ToolSpecification> specs = HANDLER.buildToolSpecifications(List.of(
                    tool("t1", "first"),
                    tool("t2", null),
                    tool("t3", "third")));

            assertThat(specs).hasSize(3);
            assertThat(specs).extracting(ToolSpecification::getName)
                    .containsExactly("first", "tool_t2", "third");
            assertThat(specs).extracting(ToolSpecification::getToolId)
                    .containsExactly("t1", "t2", "t3");
        }

        @Test
        @DisplayName("空列表返回空结果")
        void shouldReturnEmptyForEmptyInput() {
            assertThat(HANDLER.buildToolSpecifications(List.of())).isEmpty();
        }
    }
}
