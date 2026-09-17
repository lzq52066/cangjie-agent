package cn.cangjiecloud.core.plugin;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PluginRegistry} 单元测试：按 name 注册、null 防护、按 type 过滤。
 */
class PluginRegistryTest {

    private Plugin plugin(String name, String type) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        when(plugin.getType()).thenReturn(type);
        return plugin;
    }

    @Test
    void constructorShouldCollectPluginsByName() {
        Plugin calculator = plugin("calculator", "tool");
        Plugin mailer = plugin("mailer", "channel");

        PluginRegistry registry = new PluginRegistry(List.of(calculator, mailer));

        assertThat(registry.get("calculator")).isSameAs(calculator);
        assertThat(registry.get("mailer")).isSameAs(mailer);
    }

    @Test
    void nullPluginListShouldBeSafe() {
        PluginRegistry registry = new PluginRegistry(null);

        assertThat(registry.list()).isEmpty();
        assertThat(registry.get("any")).isNull();
        assertThat(registry.listByType("tool")).isEmpty();
    }

    @Test
    void emptyPluginListShouldBeSafe() {
        assertThat(new PluginRegistry(List.of()).list()).isEmpty();
    }

    @Test
    void getShouldReturnNullForUnknownName() {
        PluginRegistry registry = new PluginRegistry(List.of(plugin("a", "tool")));

        assertThat(registry.get("missing")).isNull();
    }

    @Test
    void getShouldRejectNullNameDueToConcurrentHashMapSemantics() {
        PluginRegistry registry = new PluginRegistry(List.of(plugin("a", "tool")));

        assertThatThrownBy(() -> registry.get(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldRejectPluginWithoutName() {
        Plugin anonymous = plugin(null, "tool");

        // ConcurrentHashMap 不允许 null key，注册期即失败
        assertThatThrownBy(() -> new PluginRegistry(List.of(anonymous)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerShouldAddNewPlugin() {
        PluginRegistry registry = new PluginRegistry(List.of());
        Plugin parser = plugin("markdown-parser", "parser");

        registry.register(parser);

        assertThat(registry.get("markdown-parser")).isSameAs(parser);
        assertThat(registry.list()).containsExactly(parser);
    }

    @Test
    void registerShouldOverwriteSameNameKeepingSingleEntry() {
        Plugin v1 = plugin("dup", "tool");
        Plugin v2 = plugin("dup", "processor");
        PluginRegistry registry = new PluginRegistry(List.of(v1));

        registry.register(v2);

        assertThat(registry.get("dup")).isSameAs(v2);
        assertThat(registry.list()).containsExactly(v2);
    }

    @Test
    void registerShouldIgnoreNullPlugin() {
        Plugin kept = plugin("kept", "tool");
        PluginRegistry registry = new PluginRegistry(List.of(kept));

        registry.register(null);

        assertThat(registry.list()).containsExactly(kept);
    }

    @Test
    void registerShouldIgnorePluginWithNullName() {
        PluginRegistry registry = new PluginRegistry(List.of());

        registry.register(plugin(null, "tool"));

        assertThat(registry.list()).isEmpty();
    }

    @Test
    void listByTypeShouldFilterExactly() {
        Plugin tool1 = plugin("t1", "tool");
        Plugin tool2 = plugin("t2", "tool");
        Plugin channel = plugin("c1", "channel");
        PluginRegistry registry = new PluginRegistry(Arrays.asList(tool1, tool2, channel));

        assertThat(registry.listByType("tool")).containsExactlyInAnyOrder(tool1, tool2);
        assertThat(registry.listByType("channel")).containsExactly(channel);
    }

    @Test
    void listByTypeShouldReturnAllWhenTypeIsNull() {
        Plugin a = plugin("a", "tool");
        Plugin b = plugin("b", "channel");
        PluginRegistry registry = new PluginRegistry(Arrays.asList(a, b));

        assertThat(registry.listByType(null)).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void listByTypeShouldReturnEmptyForUnknownType() {
        PluginRegistry registry = new PluginRegistry(List.of(plugin("a", "tool")));

        assertThat(registry.listByType("function")).isEmpty();
    }

    @Test
    void listByTypeShouldMatchPluginsWithNullTypeOnlyAgainstNull() {
        Plugin untyped = plugin("u", null);
        PluginRegistry registry = new PluginRegistry(List.of(untyped));

        assertThat(registry.listByType(null)).containsExactly(untyped);
        assertThat(registry.listByType("tool")).isEmpty();
    }

    @Test
    void listShouldReturnImmutableSnapshot() {
        PluginRegistry registry = new PluginRegistry(List.of(plugin("a", "tool")));

        assertThatThrownBy(() -> registry.list().add(plugin("b", "tool")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void registeredPluginShouldStillExecute() {
        Plugin calculator = plugin("calculator", "tool");
        PluginContext context = PluginContext.builder().build();
        when(calculator.execute(context)).thenReturn(6);

        PluginRegistry registry = new PluginRegistry(List.of(calculator));

        assertThat(registry.get("calculator").execute(context)).isEqualTo(6);
        assertThat(registry.get("calculator").getType()).isEqualTo("tool");
    }
}
