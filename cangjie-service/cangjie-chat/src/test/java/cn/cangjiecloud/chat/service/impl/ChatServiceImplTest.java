package cn.cangjiecloud.chat.service.impl;

import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.context.ContextRequestFactory;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.harness.ChatHarnessContextFactory;
import cn.cangjiecloud.chat.harness.HarnessConfigResolver;
import cn.cangjiecloud.chat.harness.SseHarnessListener;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.chat.service.LongTermMemoryExtractService;
import cn.cangjiecloud.chat.service.SessionSummaryService;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.harness.AgentHarness;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.core.harness.context.ContextPipeline;
import cn.cangjiecloud.core.harness.context.ContextResult;
import cn.cangjiecloud.model.service.IModelService;
import cn.cangjiecloud.workflow.service.IWorkflowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
 * {@link ChatServiceImpl} 单元测试：覆盖应用校验、会话新建/复用、同步对话成功路径、
 * 审批/本地工具/失败等执行结果分支，以及工作流路由入口。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    /** 应用服务桩 */
    @Mock
    private IApplicationService applicationService;
    /** 会话服务桩 */
    @Mock
    private IChatSessionService chatSessionService;
    /** 消息服务桩 */
    @Mock
    private IChatMessageService chatMessageService;
    /** 模型服务桩 */
    @Mock
    private IModelService modelService;
    /** 记忆提取服务桩 */
    @Mock
    private LongTermMemoryExtractService longTermMemoryExtractService;
    /** 工作流服务桩 */
    @Mock
    private IWorkflowService workflowService;
    /** 限流器桩 */
    @Mock
    private cn.cangjiecloud.chat.ratelimit.ChatRateLimiter chatRateLimiter;
    /** 会话摘要服务桩 */
    @Mock
    private SessionSummaryService sessionSummaryService;
    /** 上下文请求工厂桩 */
    @Mock
    private ContextRequestFactory contextRequestFactory;
    /** 上下文管线桩 */
    @Mock
    private ContextPipeline contextPipeline;
    /** Agent 引擎桩 */
    @Mock
    private AgentHarness agentHarness;
    /** 配置解析器桩 */
    @Mock
    private HarnessConfigResolver harnessConfigResolver;
    /** Harness 请求工厂桩 */
    @Mock
    private ChatHarnessContextFactory harnessContextFactory;

    /** 被测试对话服务 */
    private ChatServiceImpl service;

    /** UserContext 静态桩：单测线程没有 Sa-Token 上下文，getUserId 需静态拦截（不设桩即返回 null） */
    private MockedStatic<UserContext> userContextMock;

    @BeforeEach
    void setUp() {
        userContextMock = mockStatic(UserContext.class);
        service = new ChatServiceImpl(applicationService, chatSessionService, chatMessageService,
                modelService, longTermMemoryExtractService, workflowService, chatRateLimiter,
                sessionSummaryService, contextRequestFactory, contextPipeline,
                agentHarness, harnessConfigResolver, harnessContextFactory);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    /**
     * 构造已发布应用
     */
    private ApplicationEntity application(String type, Boolean memoryEnabled) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId("app-1");
        entity.setName("助手");
        entity.setType(type);
        entity.setModelId("model-1");
        entity.setStatus("published");
        entity.setMemoryEnabled(memoryEnabled);
        return entity;
    }

    /**
     * 构造对话请求
     */
    private ChatRequestDTO request(String message) {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setApplicationId("app-1");
        request.setMessage(message);
        return request;
    }

    /**
     * 准备新会话创建的桩
     */
    private ChatSessionEntity prepareNewSession(ApplicationEntity application) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setSessionId("session-new");
        session.setApplicationId(application.getId());
        session.setModelId(application.getModelId());
        session.setMessageCount(0);
        session.setTokensUsed(0);
        return session;
    }

    @Nested
    class ApplicationValidation {

        /** 应用不存在时抛异常 */
        @Test
        void shouldThrowWhenApplicationMissing() {
            when(applicationService.getById("app-1")).thenReturn(null);

            assertThatThrownBy(() -> service.chat(request("hi")))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("应用不存在");
        }

        /** 应用未发布时抛异常 */
        @Test
        void shouldThrowWhenApplicationNotPublished() {
            ApplicationEntity application = application("chat", null);
            application.setStatus("draft");
            when(applicationService.getById("app-1")).thenReturn(application);

            assertThatThrownBy(() -> service.chat(request("hi")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("应用未发布，无法对话");
        }

        /** token 配额耗尽时抛异常 */
        @Test
        void shouldThrowWhenTokenQuotaExhausted() {
            ApplicationEntity application = application("chat", null);
            application.setTokenQuota(100L);
            application.setTokensUsed(100L);
            when(applicationService.getById("app-1")).thenReturn(application);

            assertThatThrownBy(() -> service.chat(request("hi")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("应用 token 配额已用尽，请联系管理员提升配额");
        }
    }

    @Nested
    class SyncChat {

        /** 完整成功路径：装配上下文、保存用户与 AI 消息、更新统计并返回 DTO */
        @Test
        void shouldReturnResponseOnSuccess() {
            ApplicationEntity application = application("chat", false);
            when(applicationService.getById("app-1")).thenReturn(application);
            prepareNewSession(application);
            when(contextRequestFactory.newRequest(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new cn.cangjiecloud.core.harness.context.ContextRequest());
            when(contextPipeline.assemble(any()))
                    .thenReturn(ContextResult.of(java.util.List.of()));
            when(harnessContextFactory.newRequest(any(), any(), anyBoolean(), any(),
                    any(), any(), any()))
                    .thenReturn(HarnessRequest.builder().build());
            when(agentHarness.run(any(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.COMPLETED).finalText("答案")
                    .inputTokens(10).outputTokens(5).totalTokens(15)
                    .build());

            ChatResponseDTO result = service.chat(request("你好"));

            assertThat(result.getMessage()).isEqualTo("答案");
            assertThat(result.getRole()).isEqualTo("assistant");
            assertThat(result.getTokens()).isEqualTo(15);
            verify(chatMessageService, org.mockito.Mockito.times(2)).save(any(ChatMessageEntity.class));
            verify(chatSessionService).updateById(any());
            verify(sessionSummaryService).maybeSummarizeAsync(any(), any());
        }

        /** 已存在 sessionId 时复用会话，不再新建 */
        @Test
        void shouldReuseExistingSession() {
            ApplicationEntity application = application("chat", false);
            when(applicationService.getById("app-1")).thenReturn(application);
            ChatSessionEntity existing = new ChatSessionEntity();
            existing.setSessionId("existing");
            existing.setApplicationId("app-1");
            when(chatSessionService.getBySessionId("existing")).thenReturn(existing);
            when(contextPipeline.assemble(any())).thenReturn(ContextResult.of(java.util.List.of()));
            when(harnessContextFactory.newRequest(any(), any(), anyBoolean(), any(),
                    any(), any(), any())).thenReturn(HarnessRequest.builder().build());
            when(agentHarness.run(any(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.COMPLETED).totalTokens(1).build());
            ChatRequestDTO request = request("hi");
            request.setSessionId("existing");

            service.chat(request);

            verify(chatSessionService, never()).save(any());
        }

        /** 引擎返回等待审批时抛出包含审批入口的异常 */
        @Test
        void shouldThrowWhenWaitingApproval() {
            ApplicationEntity application = application("chat", false);
            when(applicationService.getById("app-1")).thenReturn(application);
            prepareNewSession(application);
            when(contextPipeline.assemble(any())).thenReturn(ContextResult.of(java.util.List.of()));
            when(harnessContextFactory.newRequest(any(), any(), anyBoolean(), any(),
                    any(), any(), any())).thenReturn(HarnessRequest.builder().build());
            when(agentHarness.run(any(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.WAITING_APPROVAL).runId("run-1")
                    .pendingApproval(cn.cangjiecloud.core.harness.ApprovalRequest.builder()
                            .approvalId("a1").build())
                    .build());

            assertThatThrownBy(() -> service.chat(request("hi")))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("待人工审批")
                    .hasMessageContaining("a1");
        }

        /** 引擎返回等待本地工具时提示仅支持网页流式 */
        @Test
        void shouldThrowWhenWaitingLocal() {
            ApplicationEntity application = application("chat", false);
            when(applicationService.getById("app-1")).thenReturn(application);
            prepareNewSession(application);
            when(contextPipeline.assemble(any())).thenReturn(ContextResult.of(java.util.List.of()));
            when(harnessContextFactory.newRequest(any(), any(), anyBoolean(), any(),
                    any(), any(), any())).thenReturn(HarnessRequest.builder().build());
            when(agentHarness.run(any(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.WAITING_LOCAL).runId("run-9").build());

            assertThatThrownBy(() -> service.chat(request("hi")))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("本地工具仅支持网页流式对话");
        }

        /** 引擎返回失败状态时抛出包装异常 */
        @Test
        void shouldThrowWhenRunFailed() {
            ApplicationEntity application = application("chat", false);
            when(applicationService.getById("app-1")).thenReturn(application);
            prepareNewSession(application);
            when(contextPipeline.assemble(any())).thenReturn(ContextResult.of(java.util.List.of()));
            when(harnessContextFactory.newRequest(any(), any(), anyBoolean(), any(),
                    any(), any(), any())).thenReturn(HarnessRequest.builder().build());
            when(agentHarness.run(any(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.FAILED).errorMessage("模型挂了").build());

            assertThatThrownBy(() -> service.chat(request("hi")))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("模型调用失败: 模型挂了");
        }

        /** 应用启用记忆时成功后触发记忆提取 */
        @Test
        void shouldTriggerMemoryExtractionWhenEnabled() {
            ApplicationEntity application = application("chat", true);
            when(applicationService.getById("app-1")).thenReturn(application);
            prepareNewSession(application);
            when(contextPipeline.assemble(any())).thenReturn(ContextResult.of(java.util.List.of()));
            when(harnessContextFactory.newRequest(any(), any(), anyBoolean(), any(),
                    any(), any(), any())).thenReturn(HarnessRequest.builder().build());
            when(agentHarness.run(any(), any())).thenReturn(HarnessOutcome.builder()
                    .status(RunStatus.COMPLETED).totalTokens(1).build());

            service.chat(request("hi"));

            verify(longTermMemoryExtractService).extract(any(), any(), any(), any(), any());
        }
    }

    @Nested
    class WorkflowRoute {

        /** workflow 类型应用在关联工作流缺失时抛异常 */
        @Test
        void shouldThrowWhenWorkflowMissing() {
            ApplicationEntity application = application("workflow", null);
            when(applicationService.getById("app-1")).thenReturn(application);
            prepareNewSession(application);
            when(workflowService.getByApplicationId("app-1")).thenReturn(null);

            assertThatThrownBy(() -> service.chat(request("hi")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("应用关联的工作流不存在");
        }
    }

    /** 流式入口：初始化失败时捕获异常并最终完成发射器（通过无应用场景验证不抛出） */
    @Test
    void shouldCompleteEmitterWhenStreamInitFails() {
        SseEmitter emitter = new SseEmitter(0L);
        when(applicationService.getById("app-1")).thenReturn(null);

        service.chatStream(request("hi"), emitter, false, null);

        verify(applicationService).getById("app-1");
    }
}
