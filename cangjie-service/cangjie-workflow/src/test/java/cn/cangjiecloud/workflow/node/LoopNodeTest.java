package cn.cangjiecloud.workflow.node;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LoopNode} 单元测试：List 遍历、maxIterations 截断、非 List 单值、缺失变量与空值边界。
 */
class LoopNodeTest {

    private final LoopNode node = new LoopNode();

    @Test
    // 覆盖场景：节点元信息
    void metadataShouldDescribeLoopNode() {
        assertThat(node.getType()).isEqualTo("loop");
        assertThat(node.getName()).isEqualTo("循环");
        assertThat(node.getDescription()).contains("循环");
    }

    @Test
    // 覆盖场景：loopVariable 指向 List —— 全量遍历，loop_current 为最后一项
    void executeShouldIterateListVariable() {
        Map<String, Object> output = node.execute(
                Map.of("items", List.of("a", "b", "c")),
                Map.of("loopVariable", "items"));

        assertThat(output)
                .containsEntry("loop_items", List.of("a", "b", "c"))
                .containsEntry("loop_count", 3)
                .containsEntry("loop_current", "c");
    }

    @Test
    // 覆盖场景：maxIterations 小于列表长度 —— 截断到上限
    void executeShouldTruncateByMaxIterations() {
        List<Integer> five = IntStream.rangeClosed(1, 5).boxed().toList();

        Map<String, Object> output = node.execute(
                Map.of("items", five),
                Map.of("loopVariable", "items", "maxIterations", 2));

        assertThat(output)
                .containsEntry("loop_items", List.of(1, 2))
                .containsEntry("loop_count", 2)
                .containsEntry("loop_current", 2);
    }

    @Test
    // 覆盖场景：maxIterations 为 0 —— 一次都不执行，current 为 null
    void executeShouldProduceNothingWhenMaxIterationsZero() {
        Map<String, Object> output = node.execute(
                Map.of("items", List.of(1, 2)),
                Map.of("loopVariable", "items", "maxIterations", 0));

        assertThat(output)
                .containsEntry("loop_items", List.of())
                .containsEntry("loop_count", 0)
                .containsEntry("loop_current", null);
    }

    @Test
    // 覆盖场景：循环变量不是 List —— 作为单元素处理
    void executeShouldWrapSingleValueWhenNotList() {
        Map<String, Object> output = node.execute(
                Map.of("item", "only"),
                Map.of("loopVariable", "item"));

        assertThat(output)
                .containsEntry("loop_items", List.of("only"))
                .containsEntry("loop_count", 1)
                .containsEntry("loop_current", "only");
    }

    @Test
    // 覆盖场景：loopVariable 缺失 / 变量在 inputs 中不存在 —— 空循环
    void executeShouldReturnEmptyWhenLoopVariableMissing() {
        assertThat(node.execute(Map.of("x", 1), Map.of()))
                .containsEntry("loop_count", 0);
        assertThat(node.execute(Map.of("x", 1), Map.of("loopVariable", "absent")))
                .containsEntry("loop_count", 0);
        // 变量存在但值为 null
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("maybe", null);
        assertThat(node.execute(inputs, Map.of("loopVariable", "maybe")))
                .containsEntry("loop_count", 0);
    }

    @Test
    // 覆盖场景：inputs 与 config 均为 null —— 默认 maxIterations=10 的空循环
    void executeShouldSurviveNullInputsAndConfig() {
        Map<String, Object> output = node.execute(null, null);

        assertThat(output)
                .containsEntry("loop_items", List.of())
                .containsEntry("loop_count", 0);
    }
}
