package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.core.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 开始节点 — 将输入参数透传到上下文
 */
@Component
public class StartNode implements WorkflowNode {

    @Override
    public String getType() {
        return "start";
    }

    @Override
    public String getName() {
        return "开始";
    }

    @Override
    public String getDescription() {
        return "工作流入口，将用户输入传递到后续节点";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
        // 直接透传输入
        return new HashMap<>(inputs != null ? inputs : Map.of());
    }
}
