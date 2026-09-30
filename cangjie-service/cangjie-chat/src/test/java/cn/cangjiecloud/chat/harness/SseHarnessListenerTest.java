package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.LocalToolCall;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.core.harness.ToolOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link SseHarnessListener} 单元测试：覆盖内部格式与 OpenAI 兼容格式下
 * 各类事件帧的发送、工具事件开关、发送异常处理，以及静态帧字符串构造。
 */
@ExtendWith(MockitoExtension.class)
class SseHarnessListenerTest {

    /** SSE 发射器桩 */
    @Mock
    private SseEmitter emitter;

    /**
     * 构造被测试监听器
     */
    private SseHarnessListener listener(boolean openAi, boolean toolEvents) {
        return new SseHarnessListener(emitter, openAi, "chatcmpl-1", "gpt-test",
                1700000000L, "session-1",
                List.of(Map.of("title", "来源文档")), toolEvents);
    }

    @Nested
    class StaticFrames {

        /** 普通分片 JSON 包含 id、模型、正文，finish_reason 缺省为 null */
        @Test
        void shouldBuildChunkWithContent() {
            String json = SseHarnessListener.openAiChunk("id1", "m1", 100L, "hi", null);

            assertThat(json).contains("\"id\":\"id1\"").contains("\"model\":\"m1\"")
                    .contains("\"content\":\"hi\"").contains("\"finish_reason\":null");
        }

        /** 指定 finishReason 时输出带引号的枚举值 */
        @Test
        void shouldBuildChunkWithStopReason() {
            String json = SseHarnessListener.openAiChunk("id1", "m1", 100L, "", "stop");

            assertThat(json).contains("\"finish_reason\":\"stop\"");
        }

        /** 正文中的特殊字符被转义 */
        @Test
        void shouldEscapeSpecialCharacters() {
            String json = SseHarnessListener.openAiChunk("i", "m", 1L, "a\"b\\c\n", null);

            assertThat(json).contains("\\\"").contains("\\\\").contains("\\n");
        }

        /** 错误帧：null 消息回落 internal_error，普通消息做转义 */
        @Test
        void shouldBuildErrorFrame() {
            assertThat(SseHarnessListener.openAiError(null)).contains("internal_error");
            assertThat(SseHarnessListener.openAiError("错\"误\n"))
                    .contains("\\\"").contains("\\n");
        }
    }

    @Nested
    class SendInit {

        /** 内部格式发送 init 事件并携带会话与来源 */
        @Test
        void shouldSendInitInInternalFormat() throws IOException {
            listener(false, false).sendInit();

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        /** OpenAI 格式不发送 init */
        @Test
        void shouldSkipInitInOpenAiFormat() throws IOException {
            listener(true, false).sendInit();

            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Nested
    class OnDelta {

        /** null 增量直接忽略 */
        @Test
        void shouldIgnoreNullDelta() throws IOException {
            listener(false, false).onDelta(null);

            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        }

        /** 内部格式以 message 帧发送 delta/done=false */
        @Test
        void shouldSendInternalDeltaFrame() throws IOException {
            listener(false, false).onDelta("你好");

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        /** OpenAI 格式以 message 帧发送分片 JSON */
        @Test
        void shouldSendOpenAiDeltaFrame() throws IOException {
            listener(true, false).onDelta("你好");

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        /** 发送抛 IOException 时包装为 IllegalStateException（OpenAI 路径的 sendRaw 不吞异常） */
        @Test
        void shouldWrapIoExceptionOnDelta() throws IOException {
            doThrow(new IOException("broken"))
                    .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            assertThatThrownBy(() -> listener(true, false).onDelta("x"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class ToolEvents {

        /** 工具事件开关关闭时，开始/结束均不发送 */
        @Test
        void shouldSkipToolEventsWhenDisabled() throws IOException {
            SseHarnessListener listener = listener(false, false);
            ToolInvocation invocation = ToolInvocation.builder().callName("t").build();

            listener.onToolStart(invocation);
            listener.onToolFinish(invocation, ToolOutcome.success("ok", 5L));

            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        }

        /** 开关开启时发送 tool_start 与 tool_finish，结束帧带状态与耗时 */
        @Test
        void shouldSendToolEventsWhenEnabled() throws IOException {
            SseHarnessListener listener = listener(false, true);
            ToolInvocation invocation = ToolInvocation.builder()
                    .callName("t").round(1).argumentsJson("{}").build();

            listener.onToolStart(invocation);
            listener.onToolFinish(invocation, ToolOutcome.success("ok", 12L));

            verify(emitter, org.mockito.Mockito.times(2))
                    .send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    /** 审批挂起帧携带审批单、风险、过期时间与一次性恢复令牌 */
    @Test
    void shouldSendApprovalRequiredFrame() throws IOException {
        ApprovalRequest request = ApprovalRequest.builder()
                .approvalId("a1").runId("r1").toolName("del")
                .arguments("{}").reason("原因").riskLevel("high")
                .resumeToken("token-1").expireAt(999L)
                .build();

        listener(false, false).onWaitingApproval(request);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    /** 本地工具挂起帧携带调用信息与一次性恢复令牌 */
    @Test
    void shouldSendLocalToolRequiredFrame() throws IOException {
        LocalToolCall call = LocalToolCall.builder()
                .runId("r1").callId("c1").toolName("fs_read")
                .arguments("{}").resumeToken("token-2")
                .build();

        listener(false, false).onWaitingLocalTool(call);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Nested
    class OnComplete {

        /** 仅失败状态推送错误帧，错误消息缺失时给通用文案 */
        @Test
        void shouldPushErrorOnlyForFailed() throws IOException {
            SseHarnessListener listener = listener(false, false);

            listener.onComplete(HarnessOutcome.builder()
                    .status(RunStatus.FAILED).build());

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        /** 非失败状态或 outcome 为 null 时不发送 */
        @Test
        void shouldIgnoreNonFailure() throws IOException {
            SseHarnessListener listener = listener(false, false);

            listener.onComplete(null);
            listener.onComplete(HarnessOutcome.builder().status(RunStatus.COMPLETED).build());

            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    /** pushError 在 OpenAI 格式下发送错误分片，IO 异常被吞掉不抛出 */
    @Test
    void shouldPushOpenAiErrorAndSwallowIoFailure() throws IOException {
        doThrow(new IOException("x"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        assertThatCode(() -> listener(true, false).pushError("出错了"))
                .doesNotThrowAnyException();
    }

    @Nested
    class SendTerminal {

        /** 内部格式发送 done 帧，finishReason 固定为 stop */
        @Test
        void shouldSendInternalDoneFrame() throws IOException {
            listener(false, false).sendTerminal();

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        /** OpenAI 格式发送 stop 分片与 [DONE] 两帧 */
        @Test
        void shouldSendOpenAiTerminalFrames() throws IOException {
            listener(true, false).sendTerminal();

            verify(emitter, org.mockito.Mockito.times(2))
                    .send(any(SseEmitter.SseEventBuilder.class));
        }

        /** 终帧发送 IO 异常被吞掉 */
        @Test
        void shouldSwallowIoFailureOnTerminal() throws IOException {
            doThrow(new IOException("closed"))
                    .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            assertThatCode(() -> listener(false, false).sendTerminal())
                    .doesNotThrowAnyException();
        }
    }
}
