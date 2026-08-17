package cn.cangjiecloud.core.workflow;

import java.util.Map;

/**
 * 工作流节点统一接口
 * <p>
 * 所有工作流节点（start/llm/knowledge/tool/condition/loop/end/api/code 等）
 * 均可实现此接口，由 {@link WorkflowNodeRegistry} 自动收集并按 type 注册。
 */
public interface WorkflowNode {

    /**
     * 节点类型：start / llm / knowledge / tool / condition / loop / end / api / code
     */
    String getType();

    /**
     * 节点名称
     */
    String getName();

    /**
     * 节点描述
     */
    String getDescription();

    /**
     * 执行节点
     *
     * @param inputs 节点输入参数
     * @param config 节点配置
     * @return 节点输出结果
     */
    Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config);
}
