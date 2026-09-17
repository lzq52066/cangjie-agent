package cn.cangjiecloud.observability.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 工具调用审批单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent_approval", autoResultMap = true)
public class AgentApprovalEntity extends BaseEntity {

    private String runId;

    private String stepId;

    private String sessionId;

    private String appId;

    private String userId;

    private String toolName;

    private String toolType;

    private String arguments;

    private String reason;

    private String riskLevel;

    /** pending / approved / rejected / expired / cancelled */
    private String status;

    /** 一次性恢复令牌 */
    private String resumeToken;

    private String decidedBy;

    private String decideRemark;

    private LocalDateTime decideTime;

    private LocalDateTime expireTime;
}
