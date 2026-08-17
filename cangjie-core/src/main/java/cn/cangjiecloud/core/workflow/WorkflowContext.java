package cn.cangjiecloud.core.workflow;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流执行上下文
 * <p>
 * 在一次工作流执行的生命周期内传递变量、输入与输出。
 */
@Data
public class WorkflowContext {

    /** 执行 ID */
    private String executionId;

    /** 上下文变量（节点间共享） */
    private Map<String, Object> variables = new HashMap<>();

    /** 工作流输入 */
    private Map<String, Object> inputs;

    /** 工作流输出 */
    private Map<String, Object> outputs;
}
