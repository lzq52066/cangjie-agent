package cn.cangjiecloud.core.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 一次工具调用的请求描述（由模型返回的 tool_call 解析而来）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocation {

    /** 所属 run */
    private String runId;

    /** 模型分配的 tool_call id，回填 tool 消息时使用 */
    private String callId;

    /** function calling 中的函数名 */
    private String callName;

    /** 原始参数 JSON 字符串（留痕用，保留模型输出的原样内容） */
    private String argumentsJson;

    /** 解析后的参数 */
    private Map<String, Object> arguments;

    /** 所属轮次 */
    private int round;

    /** 全局步骤序号 */
    private int stepNo;

    /** 工具 ID（解析成功时填充） */
    private String toolId;

    /** 工具类型：HTTP / CUSTOM / MCP / SKILL / PLUGIN / retrieval / workflow */
    private String toolType;

    /** 风险等级（解析工具元信息后填充：low / medium / high） */
    private String riskLevel;

    /** 工具元信息要求调用前人工审批 */
    private boolean requireApproval;

    /** 工具级超时（秒，未配置为 null 时回落到循环策略） */
    private Integer timeoutSeconds;

    /** 工具输出回喂模型前的最大字符数（未配置为 null 时回落到全局默认） */
    private Integer maxOutputChars;

    public String arg(String key) {
        if (arguments == null) {
            return null;
        }
        Object v = arguments.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
