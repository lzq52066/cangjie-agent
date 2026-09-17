package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.service.IToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolNode} 单元测试：toolId 校验、入参优先级、成功/失败输出结构。
 */
class ToolNodeTest {

    private IToolService toolService;
    private ToolNode node;

    @BeforeEach
    void setUp() {
        toolService = mock(IToolService.class);
        node = new ToolNode(toolService);
    }

    @Test
    // 覆盖场景：节点元信息
    void metadataShouldDescribeToolNode() {
        assertThat(node.getType()).isEqualTo("tool");
        assertThat(node.getName()).isEqualTo("工具");
        assertThat(node.getDescription()).contains("工具");
    }

    @Test
    // 覆盖场景：toolId 缺失（config 为空 / toolId 空串 / config 为 null）—— 抛 ApiException
    void executeShouldThrowWhenToolIdMissing() {
        assertThatThrownBy(() -> node.execute(Map.of(), Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ToolNode: 未配置 toolId");
        assertThatThrownBy(() -> node.execute(Map.of(), Map.of("toolId", "")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> node.execute(Map.of(), null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    // 覆盖场景：config.toolInput 优先于上下文 —— 仅传工具入参，成功结果不含 tool_error
    void executeShouldPreferConfiguredToolInput() {
        when(toolService.executeTool(eq("t1"), anyMap()))
                .thenReturn(ToolExecuteResultDTO.builder()
                        .success(true).output("查询结果").executionTime(5L).build());

        Map<String, Object> output = node.execute(
                Map.of("irrelevant", "x"),
                Map.of("toolId", "t1", "toolInput", Map.of("city", "杭州")));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(toolService).executeTool(eq("t1"), captor.capture());
        assertThat(captor.getValue()).containsEntry("city", "杭州").hasSize(1);

        assertThat(output)
                .containsOnlyKeys("tool_output", "tool_success")
                .containsEntry("tool_output", "查询结果")
                .containsEntry("tool_success", true);
    }

    @Test
    // 覆盖场景：未配置 toolInput —— 透传全部 inputs（防御性拷贝）
    void executeShouldPassAllInputsWhenToolInputAbsent() {
        when(toolService.executeTool(eq("t2"), anyMap()))
                .thenReturn(ToolExecuteResultDTO.builder().success(true).output("ok").build());
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("a", 1);
        inputs.put("b", 2);

        node.execute(inputs, Map.of("toolId", "t2"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(toolService).executeTool(eq("t2"), captor.capture());
        assertThat(captor.getValue()).containsEntry("a", 1).containsEntry("b", 2);
        assertThat(captor.getValue()).isNotSameAs(inputs);
    }

    @Test
    // 覆盖场景：工具执行失败 —— 输出包含 tool_success=false 与 tool_error
    void executeShouldIncludeToolErrorWhenFailed() {
        when(toolService.executeTool(eq("t3"), anyMap()))
                .thenReturn(ToolExecuteResultDTO.builder()
                        .success(false).output(null).error("连接超时").build());

        Map<String, Object> output = node.execute(Map.of(), Map.of("toolId", "t3"));

        assertThat(output)
                .containsEntry("tool_success", false)
                .containsEntry("tool_error", "连接超时")
                .containsKey("tool_output");
    }
}
