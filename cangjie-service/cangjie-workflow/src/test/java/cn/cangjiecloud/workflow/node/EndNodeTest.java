package cn.cangjiecloud.workflow.node;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EndNode} 单元测试：按 outputVariable 抽取输出 / 全量透传 / 空值边界。
 */
class EndNodeTest {

    private final EndNode node = new EndNode();

    @Test
    // 覆盖场景：节点元信息
    void metadataShouldDescribeEndNode() {
        assertThat(node.getType()).isEqualTo("end");
        assertThat(node.getName()).isEqualTo("结束");
        assertThat(node.getDescription()).contains("工作流出口");
    }

    @Test
    // 覆盖场景：指定 outputVariable 且变量存在 —— 只输出该变量 + __end__ 标记
    void executeShouldExtractConfiguredOutputVariable() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("llm_output", "答案");
        inputs.put("other", "忽略");

        Map<String, Object> output = node.execute(inputs, Map.of("outputVariable", "llm_output"));

        assertThat(output).containsOnlyKeys("llm_output", "__end__");
        assertThat(output).containsEntry("llm_output", "答案").containsEntry("__end__", true);
    }

    @Test
    // 覆盖场景：指定 outputVariable 但上下文无该变量 —— 仅返回 __end__
    void executeShouldOnlyMarkEndWhenOutputVariableMissing() {
        Map<String, Object> output = node.execute(new HashMap<>(), Map.of("outputVariable", "missing"));

        assertThat(output).containsOnlyKeys("__end__");
    }

    @Test
    // 覆盖场景：outputVariable 为空串 —— 走全量透传分支
    void executeShouldPassThroughAllWhenOutputVariableBlank() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("a", 1);
        inputs.put("b", 2);

        Map<String, Object> output = node.execute(inputs, Map.of("outputVariable", ""));

        assertThat(output).containsEntry("a", 1).containsEntry("b", 2).containsEntry("__end__", true);
    }

    @Test
    // 覆盖场景：config 为 null —— 全量透传分支
    void executeShouldPassThroughAllWhenConfigNull() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("k", "v");

        assertThat(node.execute(inputs, null)).containsEntry("k", "v").containsEntry("__end__", true);
    }

    @Test
    // 覆盖场景：outputVariable 指向值为 null 的变量 —— 不写入输出
    void executeShouldSkipWhenVariableValueIsNull() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("nullable", null);

        Map<String, Object> output = node.execute(inputs, Map.of("outputVariable", "nullable"));

        assertThat(output).containsOnlyKeys("__end__");
    }
}
