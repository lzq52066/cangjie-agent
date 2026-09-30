package cn.cangjiecloud.chat.controller;

import cn.cangjiecloud.application.api.dto.ApprovalDecisionDTO;
import cn.cangjiecloud.application.api.dto.ChatConfigDTO;
import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.service.ApprovalResumeService;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.chat.service.IChatService;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.harness.ApprovalRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChatController} 单元测试：覆盖 API Key 鉴权的各分支、对话发送/流式、
 * 会话与消息查询、反馈标注以及审批决策入口。
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    /** 对话服务桩 */
    @Mock
    private IChatService chatService;
    /** 会话服务桩 */
    @Mock
    private IChatSessionService chatSessionService;
    /** 消息服务桩 */
    @Mock
    private IChatMessageService chatMessageService;
    /** 应用服务桩 */
    @Mock
    private IApplicationService applicationService;
    /** 审批恢复服务桩 */
    @Mock
    private ApprovalResumeService approvalResumeService;
    /** 异步执行器桩 */
    @Mock
    private Executor chatExecutor;
    /** HTTP 请求桩 */
    @Mock
    private HttpServletRequest httpRequest;

    /** 被测试控制器 */
    private ChatController controller;

    /** UserContext 静态桩：单测线程没有 Sa-Token 上下文，getUserId 需静态拦截（不设桩即返回 null） */
    private MockedStatic<UserContext> userContextMock;

    @BeforeEach
    void setUp() {
        userContextMock = mockStatic(UserContext.class);
        controller = new ChatController(chatService, chatSessionService, chatMessageService,
                applicationService, approvalResumeService, chatExecutor);
        ReflectionTestUtils.setField(controller, "sseTimeoutSeconds", 600L);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    /**
     * 构造应用实体
     */
    private ApplicationEntity application(String id, String apikey) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(id);
        entity.setName("助手");
        entity.setApikey(apikey);
        return entity;
    }

    /**
     * 让请求头返回指定 API Key
     */
    private void withApiKey(String key) {
        when(httpRequest.getHeader("X-API-Key")).thenReturn(key);
    }

    @Nested
    class Send {

        /** 鉴权通过后同步发送，返回包装数据 */
        @Test
        void shouldSendAfterValidApikey() {
            withApiKey("key-1");
            when(applicationService.getByApikey("key-1"))
                    .thenReturn(application("app-1", "key-1"));
            ChatRequestDTO request = new ChatRequestDTO();
            request.setApplicationId("app-1");
            ChatResponseDTO response = new ChatResponseDTO();
            when(chatService.chat(request)).thenReturn(response);

            R<ChatResponseDTO> result = controller.send(request, httpRequest);

            assertThat(result.getData()).isSameAs(response);
        }

        /** 缺少 API Key 时抛业务异常 */
        @Test
        void shouldRejectMissingApikey() {
            when(httpRequest.getHeader("X-API-Key")).thenReturn(null);

            assertThatThrownBy(() -> controller.send(new ChatRequestDTO(), httpRequest))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("缺少 API Key");
        }

        /** API Key 无对应应用时抛异常 */
        @Test
        void shouldRejectInvalidApikey() {
            withApiKey("nope");
            when(applicationService.getByApikey("nope")).thenReturn(null);

            assertThatThrownBy(() -> controller.send(new ChatRequestDTO(), httpRequest))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("API Key 无效");
        }

        /** Key 所属应用与请求应用不一致时抛异常 */
        @Test
        void shouldRejectMismatchedApplication() {
            withApiKey("key-1");
            when(applicationService.getByApikey("key-1"))
                    .thenReturn(application("app-9", "key-1"));
            ChatRequestDTO request = new ChatRequestDTO();
            request.setApplicationId("app-1");

            assertThatThrownBy(() -> controller.send(request, httpRequest))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("与应用不匹配");
        }

        /** 流式入口鉴权通过后提交异步任务并返回 SSE 发射器 */
        @Test
        void shouldSubmitAsyncTaskForStream() {
            withApiKey("key-1");
            when(applicationService.getByApikey("key-1"))
                    .thenReturn(application("app-1", "key-1"));
            ChatRequestDTO request = new ChatRequestDTO();
            request.setApplicationId("app-1");

            assertThat(controller.sendStream(request, httpRequest)).isNotNull();
            verify(chatExecutor).execute(any(Runnable.class));
        }
    }

    @Nested
    class Config {

        /** 鉴权通过且应用存在时返回名称与建议问题 */
        @Test
        void shouldReturnAppConfig() {
            withApiKey("key-1");
            ApplicationEntity app = application("app-1", "key-1");
            app.setSuggestions("[\"问题一\"]");
            when(applicationService.getByApikey("key-1")).thenReturn(app);
            when(applicationService.getById("app-1")).thenReturn(app);

            R<ChatConfigDTO> result = controller.config("app-1", httpRequest);

            assertThat(result.getData().getTitle()).isEqualTo("助手");
            assertThat(result.getData().getSuggestions()).containsExactly("问题一");
        }

        /** 应用不存在时抛异常 */
        @Test
        void shouldThrowWhenAppMissing() {
            withApiKey("key-1");
            when(applicationService.getByApikey("key-1"))
                    .thenReturn(application("app-1", "key-1"));
            when(applicationService.getById("app-1")).thenReturn(null);

            assertThatThrownBy(() -> controller.config("app-1", httpRequest))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("应用不存在");
        }
    }

    @Nested
    class Sessions {

        /** 任意有效 Key 可列出其所属应用的会话 */
        @Test
        void shouldListSessionsForApikeyOwner() {
            withApiKey("key-1");
            when(applicationService.getByApikey("key-1"))
                    .thenReturn(application("app-1", "key-1"));
            when(chatSessionService.listByApplicationAndUser("app-1", "u1"))
                    .thenReturn(List.of(new ChatSessionEntity()));

            R<List<ChatSessionEntity>> result = controller.listSessions("u1", httpRequest);

            assertThat(result.getData()).hasSize(1);
        }

        /** 指定应用 ID 的会话列表需通过匹配校验 */
        @Test
        void shouldListSessionsByApplicationId() {
            withApiKey("key-1");
            when(applicationService.getByApikey("key-1"))
                    .thenReturn(application("app-1", "key-1"));

            controller.listSessionsByApp("app-1", null, httpRequest);

            verify(chatSessionService).listByApplicationAndUser("app-1", null);
        }
    }

    @Nested
    class Messages {

        /**
         * 构造会话实体
         */
        private ChatSessionEntity session() {
            ChatSessionEntity session = new ChatSessionEntity();
            session.setSessionId("s1");
            session.setApplicationId("app-1");
            return session;
        }

        /** 会话存在且 Key 匹配时返回消息明细 */
        @Test
        void shouldListMessagesWhenSessionAndKeyValid() {
            withApiKey("key-1");
            when(chatSessionService.getBySessionId("s1")).thenReturn(session());
            when(applicationService.getByApikey("key-1"))
                    .thenReturn(application("app-1", "key-1"));
            when(chatMessageService.listBySession("s1"))
                    .thenReturn(List.of(new ChatMessageEntity()));

            R<List<ChatMessageEntity>> result = controller.listMessages("s1", httpRequest);

            assertThat(result.getData()).hasSize(1);
        }

        /** 会话不存在时抛异常 */
        @Test
        void shouldThrowWhenSessionMissing() {
            when(chatSessionService.getBySessionId("s1")).thenReturn(null);

            assertThatThrownBy(() -> controller.listMessages("s1", httpRequest))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("会话不存在");
        }
    }

    @Nested
    class CloseAndDelete {

        /**
         * 准备一个可通过鉴权的会话
         */
        private void prepareSession() {
            ChatSessionEntity session = new ChatSessionEntity();
            session.setSessionId("s1");
            session.setApplicationId("app-1");
            when(chatSessionService.getBySessionId("s1")).thenReturn(session);
            withApiKey("key-1");
            when(applicationService.getByApikey("key-1"))
                    .thenReturn(application("app-1", "key-1"));
        }

        /** 关闭会话返回更新后的实体 */
        @Test
        void shouldCloseSession() {
            prepareSession();
            ChatSessionEntity closed = new ChatSessionEntity();
            when(chatSessionService.close("s1")).thenReturn(closed);

            assertThat(controller.closeSession("s1", httpRequest).getData()).isSameAs(closed);
        }

        /** 删除会话后返回成功提示 */
        @Test
        void shouldDeleteSession() {
            prepareSession();

            controller.deleteSession("s1", httpRequest);

            verify(chatSessionService).deleteBySessionId("s1");
        }
    }

    @Nested
    class FeedbackAndAnnotation {

        /**
         * 准备消息级访问校验
         */
        private void prepareMessage() {
            ChatMessageEntity message = new ChatMessageEntity();
            message.setId("m1");
            message.setApplicationId("app-1");
            when(chatMessageService.getById("m1")).thenReturn(message);
            withApiKey("key-1");
            when(applicationService.getByApikey("key-1"))
                    .thenReturn(application("app-1", "key-1"));
        }

        /** 反馈：校验通过后透传反馈值 */
        @Test
        void shouldSubmitFeedback() {
            prepareMessage();

            controller.feedback("m1", Map.of("feedback", "like"), httpRequest);

            verify(chatMessageService).feedback("m1", "like");
        }

        /** 标注：校验通过后透传修正答案 */
        @Test
        void shouldSubmitAnnotation() {
            prepareMessage();

            controller.annotate("m1", Map.of("annotation", "答案"), httpRequest);

            verify(chatMessageService).annotate("m1", "答案");
        }

        /** 消息不存在时抛异常 */
        @Test
        void shouldThrowWhenMessageMissing() {
            when(chatMessageService.getById("m1")).thenReturn(null);

            assertThatThrownBy(() -> controller.feedback(
                    "m1", Map.of(), httpRequest))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("消息不存在");
        }
    }

    /** 审批决策：审批单存在且 Key 匹配后交由恢复服务处理 */
    @Test
    void shouldDecideApprovalAfterValidation() {
        ApprovalRequest approval = ApprovalRequest.builder()
                .approvalId("a1").applicationId("app-1").build();
        when(approvalResumeService.requireDecidable("a1")).thenReturn(approval);
        withApiKey("key-1");
        when(applicationService.getByApikey("key-1"))
                .thenReturn(application("app-1", "key-1"));
        ApprovalDecisionDTO body = new ApprovalDecisionDTO();

        controller.decideApproval("a1", body, httpRequest);

        verify(approvalResumeService).decide(eq(approval), eq(body));
    }
}
