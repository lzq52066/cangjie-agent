package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CustomToolHandler 单元测试
 * <p>
 * 自定义脚本工具处理器，内部直接执行真实 Groovy 脚本（不依赖 Spring 容器、不访问数据库）。
 */
@DisplayName("自定义脚本工具处理器 - CustomToolHandler")
class CustomToolHandlerTest {

    private CustomToolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CustomToolHandler();
    }

    /**
     * 工具实体构造辅助方法
     */
    private ToolEntity entity(String id, String functionName, String config) {
        ToolEntity entity = new ToolEntity();
        entity.setId(id);
        entity.setFunctionName(functionName);
        entity.setConfig(config);
        return entity;
    }

    @Nested
    @DisplayName("buildToolSpecification - 构建工具规格")
    class BuildToolSpecification {

        /**
         * 验证完整字段映射：toolId、name、description、toolType、parameters 均正确
         */
        @Test
        void shouldBuildSpecWithAllFields() {
            ToolEntity entity = entity("t1", "my_script", "1 + 1");
            entity.setDescription("自定义脚本工具");
            entity.setParameters("{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"number\"}}}");

            ToolSpecification spec = handler.buildToolSpecification(entity);

            assertThat(spec.getToolId()).isEqualTo("t1");
            assertThat(spec.getName()).isEqualTo("my_script");
            assertThat(spec.getDescription()).isEqualTo("自定义脚本工具");
            assertThat(spec.getToolType()).isEqualTo(ToolConstants.ToolType.CUSTOM);
            assertThat(spec.getParameters()).containsKey("type");
            assertThat(spec.getParameters()).containsKey("properties");
        }

        /**
         * 描述为空时回退到工具名称
         */
        @Test
        void shouldFallbackDescriptionToName() {
            ToolEntity entity = entity("t2", "script_two", "1");
            entity.setName("脚本二");

            ToolSpecification spec = handler.buildToolSpecification(entity);

            assertThat(spec.getDescription()).isEqualTo("脚本二");
        }

        /**
         * 参数 JSON 非法时解析结果为空 Map，不应抛异常
         */
        @Test
        void shouldReturnEmptyParametersWhenJsonInvalid() {
            ToolEntity entity = entity("t3", "bad_params", "1");
            entity.setParameters("{不是合法 JSON");

            ToolSpecification spec = handler.buildToolSpecification(entity);

            assertThat(spec.getParameters()).isEmpty();
        }

        /**
         * 未配置 functionName 时名称回退为 tool_<id>
         */
        @Test
        void shouldFallbackNameToToolId() {
            ToolEntity entity = entity("t4", null, "1");

            ToolSpecification spec = handler.buildToolSpecification(entity);

            assertThat(spec.getName()).isEqualTo("tool_t4");
        }
    }

    @Nested
    @DisplayName("execute - 执行脚本")
    class Execute {

        /**
         * 简单表达式脚本应执行成功并返回结果
         */
        @Test
        void shouldExecuteSimpleScriptSuccessfully() {
            ToolExecuteResultDTO result = handler.execute(entity("t1", "calc", "1 + 1"), Map.of());

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo(2);
            assertThat(result.getError()).isNull();
            assertThat(result.getExecutionTime()).isGreaterThanOrEqualTo(0L);
        }

        /**
         * 参数应注入为 Groovy 变量，脚本可直接引用
         */
        @Test
        void shouldInjectParametersIntoScript() {
            ToolExecuteResultDTO result = handler.execute(
                    entity("t2", "sum", "a + b"), Map.of("a", 1, "b", 2));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo(3);
        }

        /**
         * 脚本返回字符串时 output 原样透出
         */
        @Test
        void shouldReturnStringOutput() {
            ToolExecuteResultDTO result = handler.execute(
                    entity("t3", "greet", "'hello ' + name"), Map.of("name", "cangjie"));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("hello cangjie");
        }

        /**
         * 脚本运行期抛出异常时应返回失败结果与错误信息
         */
        @Test
        void shouldReturnFailureWhenScriptThrows() {
            ToolExecuteResultDTO result = handler.execute(
                    entity("t4", "boom", "throw new RuntimeException('脚本炸了')"), Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).contains("脚本炸了");
            assertThat(result.getOutput()).isNull();
        }

        /**
         * 语法错误脚本应返回失败结果而非抛出异常
         */
        @Test
        void shouldReturnFailureWhenScriptHasSyntaxError() {
            ToolExecuteResultDTO result = handler.execute(entity("t5", "syntax", "def def def"), Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isNotBlank();
        }
    }
}
