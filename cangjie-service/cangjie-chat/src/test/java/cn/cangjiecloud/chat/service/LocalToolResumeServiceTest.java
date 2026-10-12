package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.application.api.dto.LocalToolResultDTO;
import cn.cangjiecloud.application.api.dto.LocalToolResumeDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.harness.AgentHarness;
import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.LocalToolCall;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import cn.cangjiecloud.observability.service.IAgentApprovalService;
import cn.cangjiecloud.observability.service.IAgentRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LocalToolResumeService} 单元测试：覆盖等待 run 的状态/令牌/会话校验，
 * 结果回传后的完成、再次挂起、审批挂起与失败分支及收尾落库，
 * 以及浏览器二次确认的审批单留痕。
 */
@ExtendWith(MockitoExtension.class)
class LocalToolResumeServiceTest {

    /** run 服务桩 */
    @Mock
    private IAgentRunService agentRunService;
    /** 审批单存储桩 */
    @Mock
    private IAgentApprovalService approvalStore;
    /** Agent 引擎桩 */
    @Mock
    private AgentHarness agentHarness;
    /** 应用服务桩 */
    @Mock
    private IApplicationService applicationService;
    /** 会话服务桩 */
    @Mock
    private IChatSessionService chatSessionService;
    /** 消息服务桩 */
    @Mock
    private IChatMessageService chatMessageService;
    /** 摘要服务桩 */
    @Mock
    private SessionSummaryService sessionSummaryService;

    /** 被测试本地工具恢复服务 */
    private LocalToolResumeService service;

    @BeforeEach
    void setUp() {
        service = new LocalToolResumeService(agentRunService, approvalStore, agentHarness,
                applicationService, chatSessionService, chatMessageService, sessionSummaryService);
    }

    /**
     * 构造结果回传 DTO
     */
    private LocalToolResultDTO body(boolean failed) {
        LocalToolResultDTO body = new LocalToolResultDTO();
        body.setRunId("run-1");
        body.setCallId("call-1");
        body.setResumeToken("token-1");
        body.setSessionId("s1");
        body.setFailed(failed);
        body.setResult("执行成功");
        body.setErrorMessage("执行失败原因");
        return body;
    }

    /**
     * 构造等待本地工具的 run
     */
    private AgentRunEntity waitingRun(String status) {
        AgentRunEntity run = new AgentRunEntity();
        run.setId("run-1");
        run.setStatus(status);
        run.setResumeToken("token-1");
        run.setSessionId("s1");
        run.setAppId("app-1");
        return run;
    }

    @Nested
    class RequireWaitingRun {

        /** run 不存在时抛异常 */
        @Test
        void shouldThrowWhenRunMissing() {
            when(agentRunService.getById("run-1")).thenReturn(null);

            assertThatThrownBy(() -> service.requireWaitingRun(body(false)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("运行不存在");
        }

        /** run 不在等待本地工具状态时抛异常 */
        @Test
        void shouldThrowWhenStatusNotWaitingLocal() {
            when(agentRunService.getById("run-1"))
                    .thenReturn(waitingRun(RunStatus.COMPLETED.value()));

            assertThatThrownBy(() -> service.requireWaitingRun(body(false)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("运行不在等待本地工具执行的状态，请勿重复提交");
        }

        /** 恢复令牌不匹配时抛异常 */
        @Test
        void shouldThrowWhenTokenMismatch() {
            AgentRunEntity run = waitingRun(RunStatus.WAITING_LOCAL.value());
            run.setResumeToken("other-token");
            when(agentRunService.getById("run-1")).thenReturn(run);

            assertThatThrownBy(() -> service.requireWaitingRun(body(false)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("恢复令牌无效或已失效");
        }

        /** 会话与 run 不匹配时抛异常 */
        @Test
        void shouldThrowWhenSessionMismatch() {
            AgentRunEntity run = waitingRun(RunStatus.WAITING_LOCAL.value());
            run.setSessionId("other-session");
            when(agentRunService.getById("run-1")).thenReturn(run);

            assertThatThrownBy(() -> service.requireWaitingRun(body(false)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("会话与运行不匹配");
        }

        /** 全部校验通过时返回 run */
        @Test
        void shouldReturnRunWhenValid() {
            when(agentRunService.getById("run-1"))
                    .thenReturn(waitingRun(RunStatus.WAITING_LOCAL.value()));

            assertThat(service.requireWaitingRun(body(false)).getId()).isEqualTo("run-1");
        }
    }

    @Nested
    class Complete {

        /** 成功回传：恢复完成时返回消息并落库，sessionId 从 run 记录补取 */
        @Test
        void shouldReturnCompletedResultAndPersist() {
            when(agentHarness.completeLocalTool(anyString(), anyString(), anyString(),
                    anyBoolean(), anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.COMPLETED).runId("run-1").finalText("最终答案")
                    .totalTokens(6).build());
            when(agentRunService.getById("run-1")).thenReturn(waitingRun(
                    RunStatus.WAITING_LOCAL.value()));
            when(chatSessionService.getBySessionId("s1"))
                    .thenReturn(new cn.cangjiecloud.chat.entity.ChatSessionEntity());
            ApplicationEntity application = new ApplicationEntity();
            application.setId("app-1");
            when(applicationService.getById("app-1")).thenReturn(application);

            LocalToolResumeDTO result = service.complete(body(false));

            assertThat(result.getStatus()).isEqualTo("completed");
            assertThat(result.getMessage()).isEqualTo("最终答案");
            assertThat(result.getSessionId()).isEqualTo("s1");
            verify(chatMessageService).save(any());
            verify(sessionSummaryService).maybeSummarizeAsync(any(), any());
        }

        /** 标记失败时把错误消息作为结果正文传给引擎 */
        @Test
        void shouldPassErrorMessageWhenFailed() {
            when(agentHarness.completeLocalTool(anyString(), anyString(), anyString(),
                    anyBoolean(), anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.COMPLETED).runId("run-1").build());
            when(agentRunService.getById("run-1")).thenReturn(null);

            service.complete(body(true));

            verify(agentHarness).completeLocalTool(eq("run-1"), eq("call-1"),
                    eq("执行失败原因"), eq(true), eq("token-1"), any());
        }

        /** 恢复后再次挂起本地工具：返回 pendingLocalTool，sessionId 取自挂起信息 */
        @Test
        void shouldReturnPendingLocalTool() {
            LocalToolCall next = LocalToolCall.builder()
                    .runId("run-1").callId("c2").toolName("next")
                    .resumeToken("t2").sessionId("s1").build();
            when(agentHarness.completeLocalTool(anyString(), anyString(), anyString(),
                    anyBoolean(), anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.WAITING_LOCAL).pendingLocalTool(next).build());

            LocalToolResumeDTO result = service.complete(body(false));

            assertThat(result.getStatus()).isEqualTo("waiting_local");
            assertThat(result.getPendingLocalTool().getCallId()).isEqualTo("c2");
            assertThat(result.getSessionId()).isEqualTo("s1");
            verify(agentRunService, never()).getById(anyString());
        }

        /** 恢复后命中审批：返回 pendingApproval */
        @Test
        void shouldReturnPendingApproval() {
            cn.cangjiecloud.core.harness.ApprovalRequest approval =
                    cn.cangjiecloud.core.harness.ApprovalRequest.builder()
                            .approvalId("a1").runId("run-1").build();
            when(agentHarness.completeLocalTool(anyString(), anyString(), anyString(),
                    anyBoolean(), anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.WAITING_APPROVAL).pendingApproval(approval).build());

            LocalToolResumeDTO result = service.complete(body(false));

            assertThat(result.getStatus()).isEqualTo("waiting_approval");
            assertThat(result.getPendingApproval().getApprovalId()).isEqualTo("a1");
        }

        /** 恢复失败：返回错误信息 */
        @Test
        void shouldReturnErrorOnFailure() {
            when(agentHarness.completeLocalTool(anyString(), anyString(), anyString(),
                    anyBoolean(), anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.FAILED).errorMessage("又失败了").build());

            LocalToolResumeDTO result = service.complete(body(false));

            assertThat(result.getErrorMessage()).isEqualTo("又失败了");
        }

        /** 引擎抛异常时包装为业务异常 */
        @Test
        void shouldWrapEngineException() {
            when(agentHarness.completeLocalTool(anyString(), anyString(), anyString(),
                    anyBoolean(), anyString(), any()))
                    .thenThrow(new RuntimeException("检查点损坏"));

            assertThatThrownBy(() -> service.complete(body(false)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("恢复运行失败");
        }
    }

    @Nested
    class DecisionAudit {

        private AgentRunStepEntity step(String id, String type, String name, String input, String status) {
            AgentRunStepEntity step = new AgentRunStepEntity();
            step.setId(id);
            step.setType(type);
            step.setName(name);
            step.setInput(input);
            step.setStatus(status);
            return step;
        }

        /** 允许：留痕单取挂起时已落的 waiting_local 步骤，不采信客户端回传的工具名与入参 */
        @Test
        void shouldRecordApprovedDecisionFromWaitingLocalStep() {
            LocalToolResultDTO body = body(false);
            body.setConfirmed(true);
            body.setRiskLevel("high");
            body.setActionSummary("删除文件 c.txt");
            body.setToolName("local_write_file");
            AgentRunEntity run = waitingRun(RunStatus.WAITING_LOCAL.value());
            run.setUserId("u-1");
            when(agentRunService.getById("run-1")).thenReturn(run);
            when(agentRunService.listSteps("run-1")).thenReturn(List.of(
                    step("step-8", "tool", "local_read_file", "{}", "success"),
                    step("step-9", "tool", "local_delete_file", "{\"path\":\"c.txt\"}", "waiting_local")));
            when(agentHarness.completeLocalTool(anyString(), anyString(), anyString(),
                    anyBoolean(), anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.COMPLETED).runId("run-1").finalText("已删除").build());

            service.complete(body);

            ArgumentCaptor<ApprovalRequest> request = ArgumentCaptor.forClass(ApprovalRequest.class);
            ArgumentCaptor<Boolean> approved = ArgumentCaptor.forClass(Boolean.class);
            verify(approvalStore).recordDecided(request.capture(), approved.capture(),
                    eq("u-1"), eq("删除文件 c.txt"));
            assertThat(approved.getValue()).isTrue();
            ApprovalRequest recorded = request.getValue();
            assertThat(recorded.getRunId()).isEqualTo("run-1");
            assertThat(recorded.getStepId()).isEqualTo("step-9");
            assertThat(recorded.getSessionId()).isEqualTo("s1");
            assertThat(recorded.getApplicationId()).isEqualTo("app-1");
            assertThat(recorded.getUserId()).isEqualTo("u-1");
            assertThat(recorded.getToolName()).isEqualTo("local_delete_file");
            assertThat(recorded.getToolType()).isEqualTo("LOCAL");
            assertThat(recorded.getArguments()).isEqualTo("{\"path\":\"c.txt\"}");
            assertThat(recorded.getRiskLevel()).isEqualTo("high");
            assertThat(recorded.getReason()).contains("浏览器侧本地工具二次确认").contains("风险等级：high");
        }

        /** 拒绝同样要留痕：没有登录态时决策人记成可辨识的占位标识，备注退回拒绝原因 */
        @Test
        void shouldRecordRejectedDecisionWithClientFallback() {
            LocalToolResultDTO body = body(true);
            body.setConfirmed(false);
            body.setErrorMessage("用户拒绝了「删除文件 c.txt」操作");
            body.setToolName("local_delete_file");
            when(agentRunService.getById("run-1"))
                    .thenReturn(waitingRun(RunStatus.WAITING_LOCAL.value()));
            when(agentRunService.listSteps("run-1")).thenReturn(List.of());
            when(agentHarness.completeLocalTool(anyString(), anyString(), anyString(),
                    anyBoolean(), anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.FAILED).errorMessage("模型调用失败").build());

            service.complete(body);

            ArgumentCaptor<ApprovalRequest> request = ArgumentCaptor.forClass(ApprovalRequest.class);
            verify(approvalStore).recordDecided(request.capture(), eq(false),
                    eq("浏览器用户"), eq("用户拒绝了「删除文件 c.txt」操作"));
            assertThat(request.getValue().getToolName()).isEqualTo("local_delete_file");
            assertThat(request.getValue().getRiskLevel()).isEqualTo("medium");
            assertThat(request.getValue().getArguments()).isNull();
        }

        /** 只读工具自动执行、没有人参与：不留痕，也不额外查步骤 */
        @Test
        void shouldNotRecordWhenNoHumanDecision() {
            when(agentRunService.getById("run-1"))
                    .thenReturn(waitingRun(RunStatus.WAITING_LOCAL.value()));
            when(agentHarness.completeLocalTool(anyString(), anyString(), anyString(),
                    anyBoolean(), anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.COMPLETED).runId("run-1").finalText("目录内容").build());

            service.complete(body(false));

            verify(approvalStore, never()).recordDecided(any(), anyBoolean(), any(), any());
            verify(agentRunService, never()).listSteps(anyString());
        }
    }
}
