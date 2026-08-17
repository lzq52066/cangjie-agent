package cn.cangjiecloud.core.workflow;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 工作流节点注册中心
 * <p>
 * 自动收集 Spring 容器中所有 {@link WorkflowNode} 实现，按 type 注册。
 */
@Component
public class WorkflowNodeRegistry {

    private final Map<String, WorkflowNode> nodes = new ConcurrentHashMap<>();

    public WorkflowNodeRegistry(List<WorkflowNode> nodeList) {
        if (nodeList != null) {
            for (WorkflowNode node : nodeList) {
                if (node.getType() != null) {
                    nodes.put(node.getType(), node);
                }
            }
        }
    }

    /**
     * 注册节点
     */
    public void register(WorkflowNode node) {
        if (node == null || node.getType() == null) {
            return;
        }
        nodes.put(node.getType(), node);
    }

    /**
     * 按 type 获取节点
     */
    public WorkflowNode get(String type) {
        return nodes.get(type);
    }

    /**
     * 列出全部可用节点类型
     */
    public List<String> listTypes() {
        return List.copyOf(nodes.keySet());
    }

    /**
     * 列出全部节点
     */
    public List<WorkflowNode> listAll() {
        return nodes.values().stream().collect(Collectors.toList());
    }
}
