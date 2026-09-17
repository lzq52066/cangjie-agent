package cn.cangjiecloud.core.tool;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolSpecification} 单元测试。
 */
class ToolSpecificationTest {

    private Map<String, Object> sampleSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", java.util.List.of("city"));
        return schema;
    }

    @Test
    void builderShouldPopulateAllFields() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("get_weather")
                .description("查询天气")
                .parameters(sampleSchema())
                .toolId("tool-1")
                .toolType("HTTP")
                .build();

        assertThat(spec.getName()).isEqualTo("get_weather");
        assertThat(spec.getDescription()).isEqualTo("查询天气");
        assertThat(spec.getParameters()).containsEntry("type", "object");
        assertThat(spec.getToolId()).isEqualTo("tool-1");
        assertThat(spec.getToolType()).isEqualTo("HTTP");
    }

    @Test
    void emptyBuilderShouldLeaveAllFieldsNullSinceNoDefaultsDeclared() {
        ToolSpecification spec = ToolSpecification.builder().build();

        assertThat(spec.getName()).isNull();
        assertThat(spec.getDescription()).isNull();
        assertThat(spec.getParameters()).isNull();
        assertThat(spec.getToolId()).isNull();
        assertThat(spec.getToolType()).isNull();
    }

    @Test
    void noArgsConstructorAndSettersShouldRoundTrip() {
        ToolSpecification spec = new ToolSpecification();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "string");
        spec.setName("n");
        spec.setDescription("d");
        spec.setParameters(parameters);
        spec.setToolId("id");
        spec.setToolType("MCP");

        assertThat(spec.getName()).isEqualTo("n");
        assertThat(spec.getDescription()).isEqualTo("d");
        assertThat(spec.getParameters()).isSameAs(parameters);
        assertThat(spec.getToolId()).isEqualTo("id");
        assertThat(spec.getToolType()).isEqualTo("MCP");
    }

    @Test
    void allArgsConstructorShouldFollowFieldOrder() {
        Map<String, Object> parameters = sampleSchema();
        ToolSpecification spec = new ToolSpecification("name", "desc", parameters, "tid", "PLUGIN");

        assertThat(spec.getName()).isEqualTo("name");
        assertThat(spec.getDescription()).isEqualTo("desc");
        assertThat(spec.getParameters()).isEqualTo(parameters);
        assertThat(spec.getToolId()).isEqualTo("tid");
        assertThat(spec.getToolType()).isEqualTo("PLUGIN");
    }

    @Test
    void equalsAndHashCodeShouldBeValueBased() {
        ToolSpecification a = ToolSpecification.builder().name("t").toolId("1").build();
        ToolSpecification b = ToolSpecification.builder().name("t").toolId("1").build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(
                ToolSpecification.builder().name("t").toolId("2").build());
        assertThat(a).isNotEqualTo(ToolSpecification.builder().name("t").toolId("1").toolType("HTTP").build());
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("x");
    }

    @Test
    void toStringShouldContainIdentityFields() {
        String text = ToolSpecification.builder().name("fn").toolId("42").toolType("SKILL").build().toString();

        assertThat(text).contains("name=fn").contains("toolId=42").contains("toolType=SKILL");
    }

    @Test
    void emptyParameterMapShouldNotEqualNullParameters() {
        ToolSpecification empty = ToolSpecification.builder().parameters(Map.of()).build();
        ToolSpecification nullParams = ToolSpecification.builder().build();

        assertThat(empty.getParameters()).isEmpty();
        assertThat(empty).isNotEqualTo(nullParams);
    }
}
