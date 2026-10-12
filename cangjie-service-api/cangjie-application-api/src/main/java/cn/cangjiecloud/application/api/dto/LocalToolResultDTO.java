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

    /**
     * 用户在浏览器二次确认卡片上的决策：true 允许 / false 拒绝 / null 无需确认（只读工具自动执行）。
     * <p>
     * 与 {@code failed} 不是一回事：允许之后执行照样可能抛错（文件被占用等），那时是
     * {@code confirmed=true, failed=true}。留痕以本字段为准。
     */
    private Boolean confirmed;

    /** 确认卡片判定的风险等级（low / high），服务端只透传展示，不作为放行依据 */
    private String riskLevel;

    /** 确认卡片标题（如「删除文件 c.txt」），落审批单备注 */
    private String actionSummary;

    /** 待执行的本地工具名；服务端优先取挂起时已落的步骤记录，这里仅作兜底展示 */
    private String toolName;
}
