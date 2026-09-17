package cn.cangjiecloud.core.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人工审批请求（run 挂起时产出，前端据此渲染审批卡片）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    /** 审批单 ID */
    private String approvalId;

    /** 所属 run */
    private String runId;

    /** 关联 step */
    private String stepId;

    private String sessionId;

    private String applicationId;

    /** 待执行的工具函数名 */
    private String toolName;

    /** 工具类型 */
    private String toolType;

    /** 待执行参数（JSON） */
    private String arguments;

    /** 触发审批的原因 */
    private String reason;

    /** 风险等级：low / medium / high */
    private String riskLevel;

    /** 恢复执行的一次性令牌 */
    private String resumeToken;

    /** 过期时间戳（毫秒） */
    private long expireAt;
}
