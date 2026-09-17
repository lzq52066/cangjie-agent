package cn.cangjiecloud.workflow.node;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StartNode} 单元测试：开始节点仅做输入透传，无需任何外部依赖。
 */
class StartNodeTest {

    private final StartNode node = new StartNode();

    @Test
    // 覆盖场景：节点元信息
    void metadataShouldDescribeStartNode() {
        assertThat(node.getType()).isEqualTo("start");
        assertThat(node.getName()).isEqualTo("开始");
        assertThat(node.getDescription()).contains("工作流入口");
    }

    @Test
    // 覆盖场景：输入原样透传，且返回的是副本而非同一引用
    void executeShouldPassThroughInputsAsCopy() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("question", "hello");
        inputs.put("count", 3);

        Map<String, Object> output = node.execute(inputs, new LinkedHashMap<>());

        assertThat(output).containsOnlyKeys("question", "count");
        assertThat(output).containsEntry("question", "hello").containsEntry("count", 3);
        assertThat(output).isNotSameAs(inputs);
    }

    @Test
    // 覆盖场景：inputs 与 config 均为 null 时返回空 Map（不抛 NPE）
    void executeShouldReturnEmptyMapWhenInputsAndConfigNull() {
        assertThat(node.execute(null, null)).isEmpty();
    }

    @Test
    // 覆盖场景：空输入
    void executeShouldReturnEmptyMapWhenInputsEmpty() {
        assertThat(node.execute(Map.of(), Map.of("anything", 1))).isEmpty();
    }
}
