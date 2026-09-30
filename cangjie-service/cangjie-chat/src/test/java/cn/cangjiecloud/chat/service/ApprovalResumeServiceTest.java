package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.application.api.dto.ApprovalDecisionDTO;
import cn.cangjiecloud.application.api.dto.ApprovalResumeDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.harness.AgentHarness;
import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.observability.service.IAgentApprovalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalResumeService} 单元测试：覆盖审批单可决策校验、会话不匹配、
 * 决策幂等失败、恢复执行的完成/再次审批/失败等分支与收尾落库。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalResumeServiceTest {

    /** 审批存储桩 */
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

    /** 被测试审批恢复服务 */
    private ApprovalResumeService service;

    /** UserContext 静态桩：单测线程没有 Sa-Token 上下文，getUserId 需静态拦截（不设桩即返回 null） */
    private MockedStatic<UserContext> userContextMock;

    @BeforeEach
    void setUp() {
        userContextMock = mockStatic(UserContext.class);
        service = new ApprovalResumeService(approvalStore, agentHarness, applicationService,
                chatSessionService, chatMessageService, sessionSummaryService);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    /**
     * 构造审批请求
     */
    private ApprovalRequest approval(String approvalId, String sessionId) {
        return ApprovalRequest.builder()
                .approvalId(approvalId).runId("run-1").resumeToken("token-1")
                .sessionId(sessionId).applicationId("app-1")
                .toolName("tool-a").expireAt(0)
                .build();
    }

    @Nested
    class RequireDecidable {

        /** 审批单不存在时抛异常 */
        @Test
        void shouldThrowWhenApprovalMissing() {
            when(approvalStore.find("a1")).thenReturn(null);

            assertThatThrownBy(() -> service.requireDecidable("a1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("审批单不存在");
        }

        /** 缺少 runId 或恢复令牌时抛异常 */
        @Test
        void shouldThrowWhenRunIdOrTokenMissing() {
            ApprovalRequest invalid = ApprovalRequest.builder()
                    .approvalId("a1").runId(null).resumeToken("t").build();
            when(approvalStore.find("a1")).thenReturn(invalid);

            assertThatThrownBy(() -> service.requireDecidable("a1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("审批单缺少运行标识或恢复令牌，无法恢复运行");
        }

        /** 审批单已过期时抛异常 */
        @Test
        void shouldThrowWhenApprovalExpired() {
            ApprovalRequest expired = approval("a1", "s1");
            expired.setExpireAt(System.currentTimeMillis() - 1000);
            when(approvalStore.find("a1")).thenReturn(expired);

            assertThatThrownBy(() -> service.requireDecidable("a1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("审批单已超时");
        }

        /** 合法审批单原样返回 */
        @Test
        void shouldReturnApprovalWhenValid() {
            when(approvalStore.find("a1")).thenReturn(approval("a1", "s1"));

            assertThat(service.requireDecidable("a1").getApprovalId()).isEqualTo("a1");
        }
    }

    /** findPending 透传存储查询并翻译为 DTO，无待审批时返回 null */
    @Test
    void shouldFindPendingApproval() {
        when(approvalStore.findPendingBySession("s1")).thenReturn(approval("a1", "s1"));

        ApprovalResumeDTO.PendingApproval result = service.findPending("s1");

        assertThat(result.getApprovalId()).isEqualTo("a1");
        assertThat(service.findPending("none")).isNull();
    }

    @Nested
    class Decide {

        /** 会话与审批单不匹配时抛异常 */
        @Test
        void shouldThrowWhenSessionMismatch() {
            ApprovalDecisionDTO decision = new ApprovalDecisionDTO();
            decision.setSessionId("other");

            assertThatThrownBy(() -> service.decide(approval("a1", "s1"), decision))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("会话与审批单不匹配");
        }

        /** 决策幂等失败（审批单已处理）时抛异常，不调用引擎 */
        @Test
        void shouldThrowWhenDecideReturnsFalse() {
            when(approvalStore.decide(anyString(), anyString(), any(), any())).thenReturn(false);

            assertThatThrownBy(() -> service.decide(
                    approval("a1", "s1"), new ApprovalDecisionDTO()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("审批单已处理或已失效，请勿重复提交");
            verify(agentHarness, never()).resume(anyString(), anyBoolean(), any(),
                    any(), anyString(), any());
        }

        /** 决策通过且引擎恢复完成：返回消息并落库 */
        @Test
        void shouldReturnCompletedResultAndPersist() {
            when(approvalStore.decide(anyString(), anyString(), any(), any())).thenReturn(true);
            when(agentHarness.resume(anyString(), anyBoolean(), any(), any(),
                    anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.COMPLETED).runId("run-1").finalText("恢复后的答案")
                    .totalTokens(8).build());
            ChatSessionEntity session = new ChatSessionEntity();
            session.setSessionId("s1");
            when(chatSessionService.getBySessionId("s1")).thenReturn(session);
            ApplicationEntity application = new ApplicationEntity();
            application.setId("app-1");
            when(applicationService.getById("app-1")).thenReturn(application);

            ApprovalResumeDTO result = service.decide(approval("a1", "s1"),
                    decision(true));

            assertThat(result.getStatus()).isEqualTo("completed");
            assertThat(result.getMessage()).isEqualTo("恢复后的答案");
            verify(chatMessageService).save(any());
            verify(chatSessionService).updateById(any());
            verify(sessionSummaryService).maybeSummarizeAsync(any(), any());
        }

        /** 会话或应用不存在时跳过落库，但结果仍正常返回 */
        @Test
        void shouldSkipPersistWhenSessionMissing() {
            when(approvalStore.decide(anyString(), anyString(), any(), any())).thenReturn(true);
            when(agentHarness.resume(anyString(), anyBoolean(), any(), any(),
                    anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.COMPLETED).finalText("x").build());
            when(chatSessionService.getBySessionId("s1")).thenReturn(null);

            ApprovalResumeDTO result = service.decide(approval("a1", "s1"),
                    decision(true));

            assertThat(result.getMessage()).isEqualTo("x");
            verify(chatMessageService, never()).save(any());
        }

        /** 恢复后再次命中审批：返回带新审批单的结果 */
        @Test
        void shouldReturnNewPendingApproval() {
            when(approvalStore.decide(anyString(), anyString(), any(), any())).thenReturn(true);
            ApprovalRequest next = approval("a2", "s1");
            when(agentHarness.resume(anyString(), anyBoolean(), any(), any(),
                    anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.WAITING_APPROVAL).pendingApproval(next).build());

            ApprovalResumeDTO result = service.decide(approval("a1", "s1"),
                    decision(true));

            assertThat(result.getStatus()).isEqualTo("waiting_approval");
            assertThat(result.getPendingApproval().getApprovalId()).isEqualTo("a2");
        }

        /** 恢复执行失败：返回错误信息 */
        @Test
        void shouldReturnErrorWhenResumeFails() {
            when(approvalStore.decide(anyString(), anyString(), any(), any())).thenReturn(true);
            when(agentHarness.resume(anyString(), anyBoolean(), any(), any(),
                    anyString(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.FAILED).errorMessage("再次失败").build());

            ApprovalResumeDTO result = service.decide(approval("a1", "s1"),
                    decision(false));

            assertThat(result.getStatus()).isEqualTo("failed");
            assertThat(result.getErrorMessage()).isEqualTo("再次失败");
        }

        /** 引擎 resume 抛异常时包装为业务异常 */
        @Test
        void shouldWrapResumeException() {
            when(approvalStore.decide(anyString(), anyString(), any(), any())).thenReturn(true);
            when(agentHarness.resume(anyString(), anyBoolean(), any(), any(),
                    anyString(), any())).thenThrow(new RuntimeException("检查点丢失"));

            assertThatThrownBy(() -> service.decide(approval("a1", "s1"),
                    decision(true)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("恢复运行失败");
        }
    }

    /**
     * 构造决策 DTO
     */
    private ApprovalDecisionDTO decision(boolean approved) {
        ApprovalDecisionDTO decision = new ApprovalDecisionDTO();
        decision.setApproved(approved);
        decision.setDecidedBy("admin");
        decision.setRemark("备注");
        return decision;
    }
}
