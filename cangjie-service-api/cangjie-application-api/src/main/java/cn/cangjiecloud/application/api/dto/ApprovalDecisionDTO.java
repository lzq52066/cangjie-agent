package cn.cangjiecloud.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工具审批决策请求。
 * <p>
 * resumeToken 由审批创建时随 approval_required 事件下发，决策时必须回传：
 * 审批单与 agent_run 各存一份令牌，两者一致才允许恢复运行。
 */
@Data
public class ApprovalDecisionDTO {

    /** 决策结论：true=放行待执行的工具调用，false=拒绝并把原因回喂模型 */
    private boolean approved;

    /** 一次性恢复令牌 */
    @NotBlank(message = "恢复令牌不能为空")
    private String resumeToken;

    /** 所属会话（匿名网页路径无凭证，用它与审批单交叉校验；API Key 路径可空） */
    private String sessionId;

    /** 决策备注（拒绝时作为回喂模型的原因，可空） */
    private String remark;

    /** 决策人（可空，为空时取当前登录用户） */
    private String decidedBy;
}
