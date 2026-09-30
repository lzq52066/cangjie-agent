package cn.cangjiecloud.core.harness.impl;

import cn.cangjiecloud.core.harness.AgentRunRecorder;
import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.core.harness.ApprovalStore;
import cn.cangjiecloud.core.harness.AssistantTurn;
import cn.cangjiecloud.core.harness.HarnessException;
import cn.cangjiecloud.core.harness.HarnessListener;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.LocalToolCall;
import cn.cangjiecloud.core.harness.LoopPolicy;
import cn.cangjiecloud.core.harness.ModelGateway;
import cn.cangjiecloud.core.harness.ResumeResolver;
import cn.cangjiecloud.core.harness.ResumeState;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.core.harness.ToolGateway;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.core.harness.ToolOutcome;
import cn.cangjiecloud.core.harness.ToolStatus;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultAgentHarness} 单元测试。
 * <p>
 * 全部协作者（模型网关、工具网关、留痕器、审批存储、恢复解析器）均以 Mockito 桩替代，
 * 不加载 Spring 上下文、不连数据库。
 */
class DefaultAgentHarnessTest {

    private ModelGateway modelGateway;
    private ToolGateway toolGateway;
    private AgentRunRecorder recorder;
    private ApprovalStore approvalStore;
    private ResumeResolver resumeResolver;

    private DefaultAgentHarness harness;

    @BeforeEach
    void setUp() {
        modelGateway = mock(ModelGateway.class);
        toolGateway = mock(ToolGateway.class);
        recorder = mock(AgentRunRecorder.class);
        approvalStore = mock(ApprovalStore.class);
        resumeResolver = mock(ResumeResolver.class);

        harness = new DefaultAgentHarness(modelGateway, toolGateway, recorder, approvalStore, resumeResolver);
    }

    // ---------- 辅助构造 ----------

    private static ChatResponse.ToolCall toolCall(String id, String name) {
        return ChatResponse.ToolCall.builder().id(id).name(name).arguments("{}").build();
    }

    private static AssistantTurn textTurn(String content) {
        return AssistantTurn.builder().content(content).finishReason("stop").build();
    }

    private static AssistantTurn toolTurn(String id, String name) {
        return AssistantTurn.builder()
                .content("")
                .finishReason("tool_calls")
                .toolCalls(List.of(toolCall(id, name)))
                .build();
    }

    private static HarnessRequest requestWithoutTools() {
        return HarnessRequest.builder().runId("run-1").build();
    }

    private static HarnessRequest requestWithTools() {
        return HarnessRequest.builder()
                .runId("run-1")
                .tools(List.of(ToolSpecification.builder().name("search").build()))
                .build();
    }

    private static ChatMessage assistantWithToolCall(String id, String name) {
        return ChatMessage.assistant("", List.of(ChatMessage.ToolCallRef.of(id, name, "{}")));
    }

    // ==================== run：正常完成 ====================

    @Nested
    class RunCompletion {

        @Test
        void shouldCompleteWhenModelReturnsTextWithoutToolCalls() {
            when(modelGateway.exchange(any(), any(), any())).thenReturn(textTurn("final answer"));

            HarnessOutcome outcome = harness.run(requestWithoutTools(), null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.COMPLETED);
            assertThat(outcome.isSuccess()).isTrue();
            assertThat(outcome.getFinalText()).isEqualTo("final answer");
            assertThat(outcome.getFinishReason()).isEqualTo("stop");
            assertThat(outcome.getRounds()).isEqualTo(1);
            verify(recorder).finishRun(eq("run-1"), any(HarnessOutcome.class));
            verify(modelGateway, times(1)).exchange(any(), any(), any());
        }

        @Test
        void shouldStartRunViaRecorderWhenRunIdMissing() {
            HarnessRequest request = HarnessRequest.builder().build();
            when(recorder.startRun(request)).thenReturn("generated-run");
            when(modelGateway.exchange(any(), any(), any())).thenReturn(textTurn("hi"));

            HarnessOutcome outcome = harness.run(request, HarnessListener.NOOP);

            assertThat(outcome.getRunId()).isEqualTo("generated-run");
            verify(recorder).startRun(request);
        }

        @Test
        void shouldFeedToolResultBackAndCompleteOnSecondRound() {
            when(modelGateway.exchange(any(), any(), any()))
                    .thenReturn(toolTurn("call-1", "search"))
                    .thenReturn(textTurn("after-tool answer"));
            when(toolGateway.invoke(any(), any())).thenReturn(ToolOutcome.success("found data", 3L));

            HarnessOutcome outcome = harness.run(requestWithTools(), null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.COMPLETED);
            assertThat(outcome.getFinalText()).isEqualTo("after-tool answer");
            assertThat(outcome.getToolCallCount()).isEqualTo(1);
            verify(toolGateway).invoke(any(ToolInvocation.class), any());
            verify(modelGateway, times(2)).exchange(any(), any(), any());
            // 工具结果消息已追加到会话
            assertThat(outcome.getConversation())
                    .anyMatch(m -> "tool".equals(m.getRole()) && m.getContent().contains("found data"));
        }
    }

    // ==================== run：上下文留痕 ====================

    @Test
    void shouldRecordContextWhenFragmentsPresent() {
        HarnessRequest request = HarnessRequest.builder()
                .runId("run-1")
                .contextFragments(List.of(ContextFragment.empty(ContextSlot.RAG, "rag")))
                .build();
        when(modelGateway.exchange(any(), any(), any())).thenReturn(textTurn("ok"));

        harness.run(request, null);

        verify(recorder).recordContext(eq("run-1"), any());
    }

    // ==================== run：审批挂起 ====================

    @Nested
    class ApprovalSuspension {

        @Test
        void shouldSuspendWithWaitingApprovalAndCreateApproval() {
            when(modelGateway.exchange(any(), any(), any()))
                    .thenReturn(toolTurn("call-1", "delete"));
            when(toolGateway.invoke(any(), any()))
                    .thenReturn(ToolOutcome.waitingApproval("risky", "high"));
            when(recorder.checkpoint(any(), eq(RunStatus.WAITING_APPROVAL))).thenReturn("resume-token");
            when(approvalStore.create(any())).thenAnswer(inv -> {
                ApprovalRequest req = inv.getArgument(0);
                req.setApprovalId("approval-1");
                return req;
            });

            HarnessOutcome outcome = harness.run(requestWithTools(), null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.WAITING_APPROVAL);
            assertThat(outcome.getPendingApproval()).isNotNull();
            assertThat(outcome.getPendingApproval().getApprovalId()).isEqualTo("approval-1");
            assertThat(outcome.getPendingApproval().getRiskLevel()).isEqualTo("high");
            assertThat(outcome.getPendingApproval().getResumeToken()).isEqualTo("resume-token");
            verify(recorder).checkpoint(any(), eq(RunStatus.WAITING_APPROVAL));
            // 挂起时 run 尚未结束
            verify(recorder, never()).finishRun(anyString(), any());
        }

        @Test
        void shouldDenyAndContinueWhenApprovalStoreMissing() {
            DefaultAgentHarness noApproval = new DefaultAgentHarness(
                    modelGateway, toolGateway, recorder, null, resumeResolver);
            when(modelGateway.exchange(any(), any(), any()))
                    .thenReturn(toolTurn("call-1", "delete"))
                    .thenReturn(textTurn("done"));
            when(toolGateway.invoke(any(), any()))
                    .thenReturn(ToolOutcome.waitingApproval("risky", "high"));

            HarnessOutcome outcome = noApproval.run(requestWithTools(), null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.COMPLETED);
            assertThat(outcome.getConversation())
                    .anyMatch(m -> "tool".equals(m.getRole()) && m.getContent().contains("审批能力未启用"));
        }
    }

    // ==================== resume：审批恢复 ====================

    @Nested
    class ResumeAfterApproval {

        @Test
        void approvedShouldContinuePendingToolThenComplete() {
            // 检查点：assistant 已发起工具调用，尚未回填结果
            ResumeState state = ResumeState.builder()
                    .runId("run-1")
                    .messages(List.of(assistantWithToolCall("call-1", "search")))
                    .pendingToolCalls(List.of(toolCall("call-1", "search")))
                    .build();
            AgentRunRecorder.PausedRun paused = AgentRunRecorder.PausedRun.builder()
                    .state(state).build();
            when(recorder.loadPaused("run-1", "resume-token")).thenReturn(paused);
            when(resumeResolver.resolve(eq(paused), eq(true), any()))
                    .thenReturn(requestWithTools());
            when(toolGateway.invoke(any(), any())).thenReturn(ToolOutcome.success("data", 1L));
            when(modelGateway.exchange(any(), any(), any())).thenReturn(textTurn("final"));

            HarnessOutcome outcome = harness.resume("run-1", true, "admin", "ok",
                    "resume-token", null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.COMPLETED);
            assertThat(outcome.getFinalText()).isEqualTo("final");
            // 挂起的工具已补执行
            verify(toolGateway).invoke(any(ToolInvocation.class), any());
            verify(modelGateway).exchange(any(), any(), any());
        }

        @Test
        void rejectedShouldDenyPendingCallsAndComplete() {
            ResumeState state = ResumeState.builder()
                    .runId("run-1")
                    .messages(List.of(assistantWithToolCall("call-1", "delete")))
                    .pendingToolCalls(List.of(toolCall("call-1", "delete")))
                    .build();
            AgentRunRecorder.PausedRun paused = AgentRunRecorder.PausedRun.builder()
                    .state(state).build();
            when(recorder.loadPaused("run-1", "t")).thenReturn(paused);
            when(resumeResolver.resolve(eq(paused), eq(false), any()))
                    .thenReturn(requestWithTools());
            when(modelGateway.exchange(any(), any(), any())).thenReturn(textTurn("ack"));

            HarnessOutcome outcome = harness.resume("run-1", false, "admin", "not allowed",
                    "t", null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.COMPLETED);
            assertThat(outcome.getConversation())
                    .anyMatch(m -> "tool".equals(m.getRole()) && m.getContent().contains("not allowed"));
            // 拒绝时工具不应执行
            verify(toolGateway, never()).invoke(any(), any());
        }

        @Test
        void shouldThrowWhenPausedRunMissing() {
            when(recorder.loadPaused("run-x", "t")).thenReturn(null);

            assertThatThrownBy(() -> harness.resume("run-x", true, null, null, "t", null))
                    .isInstanceOf(HarnessException.class)
                    .hasMessageContaining("运行不存在");
        }

        @Test
        void shouldThrowWhenResumeResolverMissing() {
            DefaultAgentHarness noResolver = new DefaultAgentHarness(
                    modelGateway, toolGateway, recorder, approvalStore, null);
            ResumeState state = ResumeState.builder().build();
            AgentRunRecorder.PausedRun paused = AgentRunRecorder.PausedRun.builder().state(state).build();
            when(recorder.loadPaused("run-1", "t")).thenReturn(paused);

            assertThatThrownBy(() -> noResolver.resume("run-1", true, null, null, "t", null))
                    .isInstanceOf(HarnessException.class)
                    .hasMessageContaining("未配置 ResumeResolver");
        }
    }

    // ==================== 本地工具挂起与回传 ====================

    @Nested
    class LocalTool {

        @Test
        void shouldSuspendWithWaitingLocal() {
            when(modelGateway.exchange(any(), any(), any()))
                    .thenReturn(toolTurn("call-1", "fs_read"));
            when(toolGateway.invoke(any(), any())).thenReturn(ToolOutcome.waitingLocal());
            when(recorder.checkpoint(any(), eq(RunStatus.WAITING_LOCAL))).thenReturn("local-token");

            HarnessOutcome outcome = harness.run(requestWithTools(), null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.WAITING_LOCAL);
            LocalToolCall local = outcome.getPendingLocalTool();
            assertThat(local).isNotNull();
            assertThat(local.getCallId()).isEqualTo("call-1");
            assertThat(local.getToolName()).isEqualTo("fs_read");
            assertThat(local.getResumeToken()).isEqualTo("local-token");
            verify(recorder).checkpoint(any(), eq(RunStatus.WAITING_LOCAL));
        }

        @Test
        void completeLocalToolShouldAppendResultThenContinue() {
            ResumeState state = ResumeState.builder()
                    .runId("run-1")
                    .messages(List.of(assistantWithToolCall("call-1", "fs_read")))
                    .pendingToolCalls(List.of(toolCall("call-1", "fs_read")))
                    .build();
            AgentRunRecorder.PausedRun paused = AgentRunRecorder.PausedRun.builder()
                    .state(state).build();
            when(recorder.loadPaused("run-1", "local-token")).thenReturn(paused);
            when(resumeResolver.resolve(eq(paused), eq(true), any()))
                    .thenReturn(requestWithTools());
            when(modelGateway.exchange(any(), any(), any())).thenReturn(textTurn("final"));

            HarnessOutcome outcome = harness.completeLocalTool("run-1", "call-1",
                    "{\"content\":\"file body\"}", false, "local-token", null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.COMPLETED);
            assertThat(outcome.getFinalText()).isEqualTo("final");
            assertThat(outcome.getConversation())
                    .anyMatch(m -> "tool".equals(m.getRole()) && m.getContent().contains("file body"));
            verify(modelGateway).exchange(any(), any(), any());
        }

        @Test
        void completeLocalToolShouldRecordFailureWhenFailedFlagSet() {
            ResumeState state = ResumeState.builder()
                    .runId("run-1")
                    .messages(List.of(assistantWithToolCall("call-1", "fs_read")))
                    .pendingToolCalls(List.of(toolCall("call-1", "fs_read")))
                    .build();
            AgentRunRecorder.PausedRun paused = AgentRunRecorder.PausedRun.builder()
                    .state(state).build();
            when(recorder.loadPaused("run-1", "t")).thenReturn(paused);
            when(resumeResolver.resolve(any(), eq(true), any())).thenReturn(requestWithTools());
            when(modelGateway.exchange(any(), any(), any())).thenReturn(textTurn("ok"));

            HarnessOutcome outcome = harness.completeLocalTool("run-1", "call-1",
                    "read error", true, "t", null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.COMPLETED);
            assertThat(outcome.getConversation())
                    .anyMatch(m -> "tool".equals(m.getRole()) && m.getContent().contains("read error"));
        }

        @Test
        void completeLocalToolShouldThrowWhenPausedMissing() {
            when(recorder.loadPaused("run-x", "t")).thenReturn(null);

            assertThatThrownBy(() -> harness.completeLocalTool("run-x", "c", null, false, "t", null))
                    .isInstanceOf(HarnessException.class);
        }
    }

    // ==================== 失败 / 超时 / 取消 ====================

    @Nested
    class FailureHandling {

        @Test
        void shouldFinishAsFailedWhenModelThrows() {
            when(modelGateway.exchange(any(), any(), any()))
                    .thenThrow(new RuntimeException("model exploded"));

            HarnessOutcome outcome = harness.run(requestWithoutTools(), null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.FAILED);
            assertThat(outcome.getErrorMessage()).isEqualTo("model exploded");
            verify(recorder).finishRun(eq("run-1"), any(HarnessOutcome.class));
        }

        @Test
        void shouldFinishAsFailedOnTimeout() {
            LoopPolicy oneSecond = LoopPolicy.of(5, 1);
            HarnessRequest request = HarnessRequest.builder()
                    .runId("run-1").loopPolicy(oneSecond)
                    .tools(List.of(ToolSpecification.builder().name("search").build()))
                    .build();
            // 第一轮模型交换耗时超过总超时；返回工具调用后，下一轮开头的截止检查才会触发
            when(modelGateway.exchange(any(), any(), any())).thenAnswer(inv -> {
                Thread.sleep(1_100);
                return toolTurn("call-1", "search");
            });
            when(toolGateway.invoke(any(), any())).thenReturn(ToolOutcome.success("data", 1L));

            HarnessOutcome outcome = harness.run(request, null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.FAILED);
            assertThat(outcome.getErrorMessage()).contains("对话处理超时");
        }

        @Test
        void shouldFinishAsCancelledWhenCancelFlagSet() {
            HarnessRequest request = HarnessRequest.builder().runId("run-1").build();
            request.getCancelFlag().set(true);

            HarnessOutcome outcome = harness.run(request, null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.CANCELLED);
            assertThat(outcome.getFinalText()).isNull();
            verify(recorder).finishRun(eq("run-1"), any(HarnessOutcome.class));
        }

        @Test
        void shouldPreservePartialContentFromHarnessExceptionOnFailure() {
            when(modelGateway.exchange(any(), any(), any()))
                    .thenThrow(new HarnessException("stream broke", "partial stream text", null));

            HarnessOutcome outcome = harness.run(requestWithoutTools(), null);

            assertThat(outcome.getStatus()).isEqualTo(RunStatus.FAILED);
            assertThat(outcome.getFinalText()).isEqualTo("partial stream text");
        }
    }

    // ==================== 轮次耗尽 ====================

    @Test
    void shouldCompleteWithMaxRoundsWhenToolsKeepReturning() {
        LoopPolicy twoRounds = LoopPolicy.of(2, 300);
        HarnessRequest request = HarnessRequest.builder()
                .runId("run-1")
                .loopPolicy(twoRounds)
                .tools(List.of(ToolSpecification.builder().name("search").build()))
                .build();
        when(modelGateway.exchange(any(), any(), any())).thenReturn(toolTurn("call-1", "search"));
        when(toolGateway.invoke(any(), any())).thenReturn(ToolOutcome.success("data", 1L));

        HarnessOutcome outcome = harness.run(request, null);

        assertThat(outcome.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(outcome.getFinishReason()).isEqualTo("max_rounds");
        assertThat(outcome.getRounds()).isEqualTo(2);
        // 每轮一次工具
        verify(toolGateway, times(2)).invoke(any(), any());
    }

    // ==================== 监听器事件 ====================

    @Test
    void shouldEmitLifecycleEventsToListener() {
        HarnessListener listener = mock(HarnessListener.class);
        when(modelGateway.exchange(any(), any(), any())).thenReturn(textTurn("final"));

        harness.run(requestWithTools(), listener);

        verify(listener).onRoundStart(1);
        verify(listener).onAssistantTurn(any(AssistantTurn.class));
        verify(listener).onComplete(any(HarnessOutcome.class));
    }

    @Test
    void shouldEmitToolEventsAroundToolInvocation() {
        HarnessListener listener = mock(HarnessListener.class);
        when(modelGateway.exchange(any(), any(), any()))
                .thenReturn(toolTurn("call-1", "search"))
                .thenReturn(textTurn("final"));
        when(toolGateway.invoke(any(), any())).thenReturn(ToolOutcome.success("data", 1L));

        harness.run(requestWithTools(), listener);

        verify(listener).onToolStart(any(ToolInvocation.class));
        verify(listener).onToolFinish(any(ToolInvocation.class), any(ToolOutcome.class));
    }
}
