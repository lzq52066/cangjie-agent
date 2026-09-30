package cn.cangjiecloud.chat.controller;

import cn.cangjiecloud.application.api.dto.ChatOpenAiRequest;
import cn.cangjiecloud.application.api.dto.ChatOpenAiResponse;
import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.application.auth.ApiAuthResult;
import cn.cangjiecloud.application.auth.OpenApiAuthenticator;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.chat.service.IChatService;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChatOpenAiController} 单元测试：覆盖 Bearer 鉴权、非流式请求翻译与响应翻译、
 * 流式入口，以及最后一条用户消息提取等内部转换逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ChatOpenAiControllerTest {

    /** 对话服务桩 */
    @Mock
    private IChatService chatService;
    /** 鉴权器桩 */
    @Mock
    private OpenApiAuthenticator authenticator;
    /** 异步执行器桩 */
    @Mock
    private Executor chatExecutor;
    /** HTTP 请求桩 */
    @Mock
    private HttpServletRequest httpRequest;
    /** HTTP 响应桩 */
    @Mock
    private HttpServletResponse httpResponse;

    /** 被测试控制器 */
    private ChatOpenAiController controller;

    /** UserContext 静态桩：单测线程没有 Sa-Token 上下文，getUserId 需静态拦截（不设桩即返回 null） */
    private MockedStatic<UserContext> userContextMock;

    @BeforeEach
    void setUp() {
        userContextMock = mockStatic(UserContext.class);
        controller = new ChatOpenAiController(chatService, authenticator, chatExecutor);
        ReflectionTestUtils.setField(controller, "sseTimeoutSeconds", 600L);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    /**
     * 构造 OpenAI 消息
     */
    private ChatOpenAiRequest.Message message(String role, String content) {
        ChatOpenAiRequest.Message message = new ChatOpenAiRequest.Message();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    /**
     * 构造 OpenAI 请求
     */
    private ChatOpenAiRequest request(boolean stream) {
        ChatOpenAiRequest request = new ChatOpenAiRequest();
        request.setModel("gpt-test");
        request.setStream(stream);
        request.setMessages(List.of(
                message("system", "你是助手"),
                message("user", "你好")));
        return request;
    }

    @Nested
    class NonStream {

        /** 鉴权通过后，请求被翻译为内部 DTO 并返回 OpenAI 响应 */
        @Test
        void shouldTranslateAndReturnOpenAiResponse() {
            ApplicationEntity application = new ApplicationEntity();
            application.setId("app-1");
            when(authenticator.resolveToken(httpRequest)).thenReturn("token");
            when(authenticator.authenticate("token"))
                    .thenReturn(ApiAuthResult.apikey(application));
            ChatResponseDTO internalResponse = new ChatResponseDTO();
            internalResponse.setRole("assistant");
            internalResponse.setMessage("答案");
            internalResponse.setPromptTokens(7);
            internalResponse.setCompletionTokens(3);
            internalResponse.setTokens(10);
            when(chatService.chat(any(ChatRequestDTO.class))).thenReturn(internalResponse);

            Object result = controller.chatCompletions(request(false), httpRequest, httpResponse);

            assertThat(result).isInstanceOf(ChatOpenAiResponse.class);
            ChatOpenAiResponse response = (ChatOpenAiResponse) result;
            assertThat(response.getModel()).isEqualTo("gpt-test");
            assertThat(response.getChoices()).hasSize(1);
            assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("答案");
            assertThat(response.getUsage().getTotalTokens()).isEqualTo(10);
        }

        /** 翻译出的内部请求：应用 ID、来源、主消息与完整对话都正确 */
        @Test
        void shouldTranslateToInternalRequest() {
            ApplicationEntity application = new ApplicationEntity();
            application.setId("app-1");
            when(authenticator.resolveToken(httpRequest)).thenReturn("token");
            when(authenticator.authenticate("token"))
                    .thenReturn(ApiAuthResult.apikey(application));

            when(chatService.chat(any(ChatRequestDTO.class))).thenReturn(new ChatResponseDTO());

            controller.chatCompletions(request(false), httpRequest, httpResponse);

            ArgumentCaptor<ChatRequestDTO> captor = ArgumentCaptor.forClass(ChatRequestDTO.class);
            verify(chatService).chat(captor.capture());
            ChatRequestDTO internal = captor.getValue();
            assertThat(internal.getApplicationId()).isEqualTo("app-1");
            assertThat(internal.getSource()).isEqualTo("openai-api");
            assertThat(internal.getMessage()).isEqualTo("你好");
            assertThat(internal.getMessages()).hasSize(2);
        }

        /** 鉴权失败（返回 null）时抛业务异常 */
        @Test
        void shouldThrowWhenAuthenticationFails() {
            when(authenticator.resolveToken(httpRequest)).thenReturn("bad");
            when(authenticator.authenticate("bad")).thenReturn(null);

            assertThatThrownBy(() -> controller.chatCompletions(
                    request(false), httpRequest, httpResponse))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("凭证无效或应用未发布");
        }
    }

    @Nested
    class StreamMode {

        /** 流式请求设置响应头、提交异步任务并返回 SSE 发射器 */
        @Test
        void shouldReturnEmitterForStreamRequest() {
            ApplicationEntity application = new ApplicationEntity();
            application.setId("app-1");
            when(authenticator.resolveToken(httpRequest)).thenReturn("token");
            when(authenticator.authenticate("token"))
                    .thenReturn(ApiAuthResult.apikey(application));

            Object result = controller.chatCompletions(request(true), httpRequest, httpResponse);

            assertThat(result).isInstanceOf(
                    org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);
            verify(httpResponse).setContentType("text/event-stream");
            verify(chatExecutor).execute(any(Runnable.class));
        }
    }

    /** 多轮对话时取最后一条 user 消息作为主消息（通过内部 DTO 内容验证） */
    @Test
    void shouldUseLastUserMessageAsMainContent() {
        ApplicationEntity application = new ApplicationEntity();
        application.setId("app-1");
        when(authenticator.resolveToken(httpRequest)).thenReturn("token");
        when(authenticator.authenticate("token"))
                .thenReturn(ApiAuthResult.apikey(application));
        ChatOpenAiRequest request = new ChatOpenAiRequest();
        request.setStream(false);
        request.setMessages(List.of(
                message("user", "第一个问题"),
                message("assistant", "回答一"),
                message("user", "第二个问题")));

        when(chatService.chat(any(ChatRequestDTO.class))).thenReturn(new ChatResponseDTO());

        controller.chatCompletions(request, httpRequest, httpResponse);

        ArgumentCaptor<ChatRequestDTO> captor = ArgumentCaptor.forClass(ChatRequestDTO.class);
        verify(chatService).chat(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo("第二个问题");
    }

    /** 消息中角色或内容空白的条目在转换时被跳过 */
    @Test
    void shouldSkipMalformedMessagesInConversion() {
        ApplicationEntity application = new ApplicationEntity();
        application.setId("app-1");
        when(authenticator.resolveToken(httpRequest)).thenReturn("token");
        when(authenticator.authenticate("token"))
                .thenReturn(ApiAuthResult.apikey(application));
        ChatOpenAiRequest request = new ChatOpenAiRequest();
        request.setStream(false);
        request.setMessages(List.of(message("", "无角色"), message("user", "有效")));

        when(chatService.chat(any(ChatRequestDTO.class))).thenReturn(new ChatResponseDTO());

        controller.chatCompletions(request, httpRequest, httpResponse);

        ArgumentCaptor<ChatRequestDTO> captor = ArgumentCaptor.forClass(ChatRequestDTO.class);
        verify(chatService).chat(captor.capture());
        assertThat(captor.getValue().getMessages()).hasSize(1);
    }
}
