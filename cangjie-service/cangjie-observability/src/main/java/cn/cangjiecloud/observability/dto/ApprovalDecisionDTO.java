package cn.cangjiecloud.observability.dto;

import lombok.Data;

/**
 * 审批决策入参
 */
@Data
public class ApprovalDecisionDTO {

    /** approved / rejected */
    private String status;

    /** 决策备注（拒绝时会作为原因回喂模型） */
    private String remark;

    /** 一次性恢复令牌 */
    private String resumeToken;

    /** 决策后是否立即恢复执行 */
    private Boolean resume = true;
}
