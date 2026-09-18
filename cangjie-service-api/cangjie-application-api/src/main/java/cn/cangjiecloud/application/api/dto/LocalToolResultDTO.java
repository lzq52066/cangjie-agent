package cn.cangjiecloud.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 浏览器侧本地工具执行结果回传请求。
 * <p>
 * run 在引擎遇到 LOCAL 工具时挂起，SSE 下发 local_tool_required 事件（含 runId/callId/resumeToken），
 * 浏览器在用户授权目录内执行后，凭一次性令牌把结果回传以恢复运行。
 */
@Data
public class LocalToolResultDTO {

    /** 挂起中的 run */
    @NotBlank(message = "runId 不能为空")
    private String runId;

    /** 挂起时下发的 tool_call id */
    @NotBlank(message = "callId 不能为空")
    private String callId;

    /** 一次性恢复令牌 */
    @NotBlank(message = "恢复令牌不能为空")
    private String resumeToken;

    /** 所属会话（匿名网页路径无凭证，用它与 run 交叉校验） */
    private String sessionId;

    /** 是否执行失败：false=result 为成功结果，true=errorMessage 回喂模型 */
    private boolean failed;

    /** 成功时的执行结果（JSON 字符串，原样回填给模型） */
    private String result;

    /** 失败时的错误信息（回填给模型，可空） */
    private String errorMessage;
}
