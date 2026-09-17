package cn.cangjiecloud.core.workflow;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowNodeRegistry} 单元测试：构造收集、null 防护、register、查询与列举。
 */
class WorkflowNodeRegistryTest {

    private WorkflowNode node(String type, String name) {
        WorkflowNode node = mock(WorkflowNode.class);
        when(node.getType()).thenReturn(type);
        when(node.getName()).thenReturn(name);
        return node;
    }

    @Test
    void constructorShouldCollectNodesByType() {
        WorkflowNode llm = node("llm", "大模型");
        WorkflowNode start = node("start", "开始");

        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(List.of(llm, start));

        assertThat(registry.get("llm")).isSameAs(llm);
        assertThat(registry.get("start")).isSameAs(start);
    }

    @Test
    void constructorShouldSkipNodesWithoutType() {
        WorkflowNode typed = node("tool", "工具");
        WorkflowNode untyped = node(null, "无名");

        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(Arrays.asList(typed, untyped));

        assertThat(registry.get("tool")).isSameAs(typed);
        assertThat(registry.listTypes()).containsExactly("tool");
        assertThat(registry.listAll()).containsExactly(typed);
    }

    @Test
    void nullNodeListShouldBeSafe() {
        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(null);

        assertThat(registry.listTypes()).isEmpty();
        assertThat(registry.listAll()).isEmpty();
        assertThat(registry.get("llm")).isNull();
    }

    @Test
    void emptyNodeListShouldBeSafe() {
        assertThat(new WorkflowNodeRegistry(List.of()).listAll()).isEmpty();
    }

    @Test
    void getShouldReturnNullForUnknownType() {
        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(List.of(node("llm", "大模型")));

        assertThat(registry.get("unknown")).isNull();
    }

    @Test
    void getShouldRejectNullTypeDueToConcurrentHashMapSemantics() {
        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(List.of(node("llm", "大模型")));

        assertThatThrownBy(() -> registry.get(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerShouldAddNewNode() {
        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(List.of());
        WorkflowNode condition = node("condition", "分支");

        registry.register(condition);

        assertThat(registry.get("condition")).isSameAs(condition);
        assertThat(registry.listTypes()).containsExactly("condition");
    }

    @Test
    void registerShouldOverwriteExistingType() {
        WorkflowNode original = node("llm", "v1");
        WorkflowNode replacement = node("llm", "v2");
        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(List.of(original));

        registry.register(replacement);

        assertThat(registry.get("llm")).isSameAs(replacement);
        assertThat(registry.listTypes()).hasSize(1);
    }

    @Test
    void registerShouldIgnoreNullNode() {
        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(List.of(node("end", "结束")));

        registry.register(null);

        assertThat(registry.listAll()).hasSize(1);
    }

    @Test
    void registerShouldIgnoreNodeWithNullType() {
        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(List.of());

        registry.register(node(null, "无效节点"));

        assertThat(registry.listAll()).isEmpty();
        assertThat(registry.listTypes()).isEmpty();
    }

    @Test
    void listAllShouldExposeEveryRegisteredNode() {
        WorkflowNode a = node("a", "A");
        WorkflowNode b = node("b", "B");
        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(List.of(a, b));

        assertThat(registry.listAll()).containsExactlyInAnyOrder(a, b);
        assertThat(registry.listTypes()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void listTypesShouldReturnImmutableSnapshot() {
        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(List.of(node("a", "A")));

        assertThatThrownBy(() -> registry.listTypes().add("b")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void registeredNodeShouldStillExecute() {
        WorkflowNode llm = node("llm", "大模型");
        Map<String, Object> output = Map.of("text", "hi");
        when(llm.execute(Map.of("prompt", "p"), Map.of("model", "gpt"))).thenReturn(output);

        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(List.of(llm));

        assertThat(registry.get("llm").execute(Map.of("prompt", "p"), Map.of("model", "gpt"))).isEqualTo(output);
        assertThat(registry.get("llm").getName()).isEqualTo("大模型");
    }
}
