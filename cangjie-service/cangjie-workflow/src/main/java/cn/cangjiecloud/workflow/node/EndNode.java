package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.core.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 结束节点 — 提取指定变量作为最终输出
 */
@Component
public class EndNode implements WorkflowNode {

    @Override
    public String getType() {
        return "end";
    }

    @Override
    public String getName() {
        return "结束";
    }

    @Override
    public String getDescription() {
        return "工作流出口，收集指定变量作为输出";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
        Map<String, Object> output = new HashMap<>();
        String outputVar = config != null ? (String) config.get("outputVariable") : null;

        if (outputVar != null && !outputVar.isEmpty()) {
            // 提取指定变量
            Object value = inputs.get(outputVar);
            if (value != null) {
                output.put(outputVar, value);
            }
        } else {
            // 未指定则透传全部
            output.putAll(inputs != null ? inputs : Map.of());
        }
        output.put("__end__", true);
        return output;
    }
}
