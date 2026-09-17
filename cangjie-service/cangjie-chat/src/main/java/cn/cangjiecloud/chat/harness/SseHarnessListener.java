package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.core.harness.HarnessListener;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.core.harness.ToolOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 事件到 SSE 帧的适配器。
 * <p>
 * 输出的事件名、字段结构与改造前逐字节一致：
 * <ul>
 *   <li>内部格式：{@code init} → {@code message}{delta,done} → {@code done}{delta,done,finishReason}</li>
 *   <li>OpenAI 兼容：{@code message}{chunk JSON} → {@code message}[DONE]</li>
 * </ul>
 * 因此新增的 {@code tool_start}/{@code tool_finish}/{@code approval_required} 事件默认关闭，
 * 只在 {@code cangjie.harness.sse-tool-events=true} 时追加，老前端不受影响。
 * <p>
 * 成功收尾帧由 {@link #sendTerminal(HarnessOutcome)} 发送，业务侧在消息落库与统计更新之后调用，
 * 保持"先落库、后关闭连接"的既有顺序。
 */
@Slf4j
public class SseHarnessListener implements HarnessListener {

    private final SseEmitter emitter;
    private final boolean openAiFormat;
    private final String requestId;
    private final String modelName;
    private final long created;
    private final String sessionId;
    private final List<Map<String, Object>> sources;
    private final boolean toolEvents;

    public SseHarnessListener(SseEmitter emitter, boolean openAiFormat, String requestId, String modelName,
                              long created, String sessionId, List<Map<String, Object>> sources,
                              boolean toolEvents) {
        this.emitter = emitter;
        this.openAiFormat = openAiFormat;
        this.requestId = requestId;
        this.modelName = modelName;
        this.created = created;
        this.sessionId = sessionId;
        this.sources = sources;
        this.toolEvents = toolEvents;
    }

    /**
     * 发送 init 事件（内部格式），与改造前"构建会话后、进入循环前"的时机一致
     */
    public void sendInit() {
        if (openAiFormat) {
            return;
        }
        Map<String, Object> initPayload = new HashMap<>();
        initPayload.put("sessionId", sessionId);
        initPayload.put("sources", sources);
        send("init", initPayload, "SSE init 事件推送失败");
    }

    @Override
    public void onDelta(String delta) {
        if (delta == null) {
            return;
        }
        try {
            if (openAiFormat) {
                sendRaw("message", openAiChunk(requestId, modelName, created, delta, null));
            } else {
                Map<String, Object> payload = new HashMap<>();
                payload.put("delta", delta);
                payload.put("done", false);
                send("message", payload, "SSE 推送失败");
            }
        } catch (IOException e) {
            log.warn("SSE 推送失败: {}", e.getMessage());
            throw new IllegalStateException("SSE 推送失败", e);
        }
    }

    @Override
    public void onToolStart(ToolInvocation invocation) {
        if (!toolEvents) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "tool_start");
        payload.put("round", invocation.getRound());
        payload.put("tool", invocation.getCallName());
        payload.put("arguments", invocation.getArgumentsJson());
        send("tool_start", payload, "SSE tool_start 推送失败");
    }

    @Override
    public void onToolFinish(ToolInvocation invocation, ToolOutcome outcome) {
        if (!toolEvents) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "tool_finish");
        payload.put("round", invocation.getRound());
        payload.put("tool", invocation.getCallName());
        payload.put("status", outcome.getStatus() == null ? null : outcome.getStatus().value());
        payload.put("durationMs", outcome.getDurationMs());
        send("tool_finish", payload, "SSE tool_finish 推送失败");
    }

    @Override
    public void onWaitingApproval(ApprovalRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "approval_required");
        payload.put("approvalId", request.getApprovalId());
        payload.put("runId", request.getRunId());
        payload.put("tool", request.getToolName());
        payload.put("arguments", request.getArguments());
        payload.put("reason", request.getReason());
        payload.put("riskLevel", request.getRiskLevel());
        payload.put("expireAt", request.getExpireAt());
        // 决策接口需要一次性恢复令牌，不下发前端就无法恢复运行
        payload.put("resumeToken", request.getResumeToken());
        send("approval_required", payload, "SSE approval_required 推送失败");
    }

    /**
     * 失败时按既有格式推送错误帧；成功与挂起由 {@link #sendTerminal(HarnessOutcome)} 与业务侧处理
     */
    @Override
    public void onComplete(HarnessOutcome outcome) {
        if (outcome == null || outcome.getStatus() != RunStatus.FAILED) {
            return;
        }
        pushError(outcome.getErrorMessage() == null ? "对话处理失败" : outcome.getErrorMessage());
    }

    /**
     * 推送错误事件（OpenAI 格式走 message 帧，内部格式走 error 帧）
     */
    public void pushError(String error) {
        try {
            if (openAiFormat) {
                sendRaw("message", openAiError(error));
            } else {
                Map<String, Object> errorPayload = new HashMap<>();
                errorPayload.put("delta", "");
                errorPayload.put("done", true);
                errorPayload.put("error", error);
                send("error", errorPayload, "SSE 错误推送失败");
            }
        } catch (IOException e) {
            log.warn("SSE 错误推送失败: {}", e.getMessage());
        }
    }

    /**
     * 成功收尾帧：OpenAI 为 stop 分片 + [DONE]，内部格式为 done 事件
     */
    public void sendTerminal() {
        try {
            if (openAiFormat) {
                sendRaw("message", openAiChunk(requestId, modelName, created, "", "stop"));
                sendRaw("message", "[DONE]");
            } else {
                Map<String, Object> donePayload = new HashMap<>();
                donePayload.put("delta", "");
                donePayload.put("done", true);
                // 改造前固定发送 stop，保持兼容
                donePayload.put("finishReason", "stop");
                send("done", donePayload, "SSE 完成事件推送失败");
            }
        } catch (IOException e) {
            log.warn("SSE 完成事件推送失败: {}", e.getMessage());
        }
    }

    // ==================== 帧构造（与改造前逐字符一致） ====================

    public static String openAiChunk(String id, String model, long created, String delta, String finishReason) {
        return String.format(
                "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d,\"model\":\"%s\","
                        + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"%s\"},\"finish_reason\":%s}]}",
                id, created, model,
                escapeContent(delta),
                finishReason == null ? "null" : "\"" + finishReason + "\"");
    }

    public static String openAiError(String message) {
        String safe = message == null ? "internal_error"
                : message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"error\":{\"message\":\"" + safe + "\",\"type\":\"internal_error\"}}";
    }

    private static String escapeContent(String delta) {
        return delta == null ? "" : delta.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private void send(String event, Object data, String warnMessage) {
        try {
            sendRaw(event, data);
        } catch (IOException e) {
            log.warn("{}: {}", warnMessage, e.getMessage());
        }
    }

    private void sendRaw(String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }
}
