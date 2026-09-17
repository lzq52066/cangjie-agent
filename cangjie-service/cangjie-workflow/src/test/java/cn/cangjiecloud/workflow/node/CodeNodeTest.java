package cn.cangjiecloud.workflow.node;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CodeNode} 单元测试：空脚本、真实 Groovy 执行成功（含沙箱变量绑定）、执行失败产生 code_error。
 */
class CodeNodeTest {

    private final CodeNode node = new CodeNode();

    @Test
    // 覆盖场景：节点元信息
    void metadataShouldDescribeCodeNode() {
        assertThat(node.getType()).isEqualTo("code");
        assertThat(node.getName()).isEqualTo("代码");
        assertThat(node.getDescription()).contains("Groovy");
    }

    @Test
    // 覆盖场景：script 缺失/空串/config 为 null —— 直接返回空 code_output
    void executeShouldReturnEmptyOutputWhenScriptBlank() {
        assertThat(node.execute(Map.of(), Map.of())).containsOnlyKeys("code_output")
                .containsEntry("code_output", "");
        assertThat(node.execute(Map.of(), Map.of("script", "")))
                .containsEntry("code_output", "");
        assertThat(node.execute(Map.of(), null)).containsEntry("code_output", "");
    }

    @Test
    // 覆盖场景：真实执行 Groovy —— 通过 input 整体访问上下文变量
    void executeShouldRunGroovyWithInputBinding() {
        Map<String, Object> output = node.execute(
                Map.of("a", 1, "b", 2),
                Map.of("script", "input.a + input.b"));

        assertThat(output).containsOnlyKeys("code_output");
        assertThat(output.get("code_output")).isEqualTo(3);
    }

    @Test
    // 覆盖场景：真实执行 Groovy —— 逐个暴露变量名（top 变量直接可用）
    void executeShouldExposeIndividualVariables() {
        Map<String, Object> output = node.execute(
                Map.of("name", "  cangjie  "),
                Map.of("script", "name.trim()"));

        assertThat(output.get("code_output")).isEqualTo("cangjie");
        assertThat(output).doesNotContainKey("code_error");
    }

    @Test
    // 覆盖场景：脚本运行时异常 —— code_output 为空且带 code_error
    void executeShouldReportErrorOnRuntimeFailure() {
        Map<String, Object> output = node.execute(Map.of(), Map.of("script", "1 / 0"));

        assertThat(output).containsEntry("code_output", "");
        assertThat((String) output.get("code_error")).isNotEmpty();
    }

    @Test
    // 覆盖场景：脚本语法错误（编译失败）—— 同样进入 code_error 分支
    void executeShouldReportErrorOnCompileFailure() {
        Map<String, Object> output = node.execute(Map.of(), Map.of("script", "def broken( {"));

        assertThat(output).containsEntry("code_output", "");
        assertThat((String) output.get("code_error")).isNotEmpty();
    }
}
