package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.workflow.WorkflowNode;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.service.IToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具节点 — 调用已注册的工具
 * <p>
 * config 参数：
 * - toolId: 工具 ID（必填）
 * - toolInput: 工具入参（Map，可选，支持变量占位符替换）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolNode implements WorkflowNode {

    private final IToolService toolService;

    @Override
    public String getType() {
        return "tool";
    }

    @Override
    public String getName() {
        return "工具";
    }

    @Override
    public String getDescription() {
        return "调用已注册的工具";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
        Map<String, Object> safeConfig = config != null ? config : Map.of();
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();

        String toolId = (String) safeConfig.get("toolId");
        if (toolId == null || toolId.isEmpty()) {
            throw new ApiException("ToolNode: 未配置 toolId");
        }

        // 工具入参：优先使用 config 中的 toolInput，否则使用全部 inputs
        @SuppressWarnings("unchecked")
        Map<String, Object> toolInput = safeConfig.containsKey("toolInput") && safeConfig.get("toolInput") instanceof Map
                ? new HashMap<>((Map<String, Object>) safeConfig.get("toolInput"))
                : new HashMap<>(safeInputs);

        ToolExecuteResultDTO result = toolService.executeTool(toolId, toolInput);

        log.info("ToolNode: 工具 {} 执行{}, 耗时 {}ms",
                toolId,
                Boolean.TRUE.equals(result.getSuccess()) ? "成功" : "失败",
                result.getExecutionTime());

        Map<String, Object> output = new HashMap<>();
        output.put("tool_output", result.getOutput());
        output.put("tool_success", result.getSuccess());
        if (result.getError() != null) {
            output.put("tool_error", result.getError());
        }
        return output;
    }
}
