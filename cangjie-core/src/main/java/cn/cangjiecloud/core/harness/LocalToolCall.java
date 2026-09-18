package cn.cangjiecloud.core.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地工具调用请求（run 因等待调用方环境执行而挂起时产出，前端据此在浏览器侧执行）。
 * <p>
 * 与 {@link ApprovalRequest} 的区别：不需要人做放行决策，执行器是调用方环境本身
 * （如浏览器 File System Access API），执行完把结果回传即可恢复运行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalToolCall {

    /** 所属 run */
    private String runId;

    private String sessionId;

    private String applicationId;

    /** 待执行的工具函数名 */
    private String toolName;

    /** 模型给出的 tool_call id，结果回填时用于关联 */
    private String callId;

    /** 待执行参数（JSON 原文） */
    private String arguments;

    /** 恢复执行的一次性令牌 */
    private String resumeToken;
}
