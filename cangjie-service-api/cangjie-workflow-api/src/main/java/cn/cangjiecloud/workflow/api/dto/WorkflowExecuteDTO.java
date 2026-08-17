package cn.cangjiecloud.workflow.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 工作流执行请求 DTO
 */
@Data
public class WorkflowExecuteDTO {

    /** 工作流 ID */
    private String workflowId;

    /** 执行输入参数 */
    private Map<String, Object> inputs;
}
