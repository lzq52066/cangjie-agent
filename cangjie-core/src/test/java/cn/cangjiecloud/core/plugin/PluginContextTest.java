package cn.cangjiecloud.core.plugin;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PluginContext} 单元测试。
 */
class PluginContextTest {

    @Test
    void builderShouldPopulateBothMaps() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "hello");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("toolId", "t-1");

        PluginContext context = PluginContext.builder().params(params).metadata(metadata).build();

        assertThat(context.getParams()).containsEntry("q", "hello");
        assertThat(context.getMetadata()).containsEntry("toolId", "t-1");
    }

    @Test
    void emptyBuilderShouldLeaveBothMapsNull() {
        PluginContext context = PluginContext.builder().build();

        assertThat(context.getParams()).isNull();
        assertThat(context.getMetadata()).isNull();
    }

    @Test
    void noArgsConstructorAndSettersShouldRoundTrip() {
        PluginContext context = new PluginContext();
        Map<String, Object> params = Map.of("a", 1);
        context.setParams(params);
        context.setMetadata(Map.of());

        assertThat(context.getParams()).isSameAs(params);
        assertThat(context.getMetadata()).isEmpty();
    }

    @Test
    void allArgsConstructorShouldFollowFieldOrder() {
        Map<String, Object> params = Map.of("p", 1);
        Map<String, Object> metadata = Map.of("m", 2);
        PluginContext context = new PluginContext(params, metadata);

        assertThat(context.getParams()).isEqualTo(params);
        assertThat(context.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void equalsAndHashCodeShouldBeValueBased() {
        PluginContext a = new PluginContext(Map.of("k", "v"), Map.of());
        PluginContext b = new PluginContext(Map.of("k", "v"), Map.of());

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(new PluginContext(Map.of("k", "other"), Map.of()));
        assertThat(a).isNotEqualTo(new PluginContext(Map.of("k", "v"), null));
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }

    @Test
    void toStringShouldMentionBothProperties() {
        String text = new PluginContext(Map.of("x", 1), Map.of("y", 2)).toString();

        assertThat(text).contains("params=").contains("metadata=").contains("x=1").contains("y=2");
    }
}
