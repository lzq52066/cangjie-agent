package cn.cangjiecloud.api.controller;

import cn.cangjiecloud.api.config.OpenApiAuthInterceptor;
import cn.cangjiecloud.application.api.dto.ChatOpenAiRequest;
import cn.cangjiecloud.application.api.dto.ChatOpenAiResponse;
import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.chat.service.IChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 对外对话接口（OpenAI 兼容）
 * <p>
 * 路径前缀 /api/open（免登录白名单），由 {@link OpenApiAuthInterceptor} 统一做 API Key 鉴权。
 * 支持同步（JSON）与流式（SSE）两种模式，与 /v1/chat/completions 协议一致。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("${cangjie.openapi.prefix:/api/open}")
public class OpenChatController {

    private final IChatService chatService;
    private final Executor chatExecutor;

    /** SSE 流式连接超时（秒） */
    @Value("${cangjie.chat.sse-timeout-seconds:600}")
    private long sseTimeoutSeconds;

    @PostMapping("/chat/completions")
    public Object chatCompletions(@Valid @RequestBody ChatOpenAiRequest request,
                                  HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        ApplicationEntity application = (ApplicationEntity) httpRequest
                .getAttribute(OpenApiAuthInterceptor.APPLICATION_ATTR);
        ChatRequestDTO internalRequest = toInternalRequest(request, application);

        if (Boolean.TRUE.equals(request.getStream())) {
            httpResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            httpResponse.setCharacterEncoding("UTF-8");
            SseEmitter emitter = new SseEmitter(sseTimeoutSeconds * 1000);
            chatExecutor.execute(() -> chatService.chatStream(internalRequest, emitter, true,
                    request.getUser()));
            return emitter;
        }

        ChatResponseDTO internal = chatService.chat(internalRequest);
        return toOpenAiResponse(internal, request.getModel());
    }

    /**
     * 将 OpenAI 格式请求转换为内部格式
     */
    private ChatRequestDTO toInternalRequest(ChatOpenAiRequest request, ApplicationEntity application) {
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setApplicationId(application.getId());
        dto.setSource("openapi");
        dto.setStream(request.getStream());
        dto.setUserId(StringUtils.hasText(request.getUser()) ? request.getUser() : null);

        String userMessage = extractLastUserMessage(request.getMessages());
        dto.setMessage(StringUtils.hasText(userMessage) ? userMessage : "");

        dto.setMessages(convertMessages(request.getMessages()));
        return dto;
    }

    /**
     * 将 OpenAI 消息列表转换为内部对话消息列表
     */
    private List<ChatRequestDTO.ConversationMessage> convertMessages(
            List<ChatOpenAiRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        List<ChatRequestDTO.ConversationMessage> result = new java.util.ArrayList<>();
        for (ChatOpenAiRequest.Message msg : messages) {
            if (!StringUtils.hasText(msg.getRole()) || !StringUtils.hasText(msg.getContent())) {
                continue;
            }
            ChatRequestDTO.ConversationMessage cm = new ChatRequestDTO.ConversationMessage();
            cm.setRole(msg.getRole());
            cm.setContent(msg.getContent());
            result.add(cm);
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 提取最后一条 user 消息
     */
    private String extractLastUserMessage(List<ChatOpenAiRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatOpenAiRequest.Message msg = messages.get(i);
            if ("user".equalsIgnoreCase(msg.getRole()) && StringUtils.hasText(msg.getContent())) {
                return msg.getContent();
            }
        }
        for (ChatOpenAiRequest.Message msg : messages) {
            if (StringUtils.hasText(msg.getContent())) {
                return msg.getContent();
            }
        }
        return "";
    }

    /**
     * 将内部响应转换为 OpenAI 格式
     */
    private ChatOpenAiResponse toOpenAiResponse(ChatResponseDTO internal, String modelName) {
        return ChatOpenAiResponse.builder()
                .id("chatcmpl-" + UUID.randomUUID().toString().replace("-", ""))
                .object("chat.completion")
                .created(System.currentTimeMillis() / 1000)
                .model(StringUtils.hasText(modelName) ? modelName : "default")
                .choices(Collections.singletonList(
                        ChatOpenAiResponse.Choice.builder()
                                .index(0)
                                .message(ChatOpenAiResponse.ChoiceMessage.builder()
                                        .role(internal.getRole() != null ? internal.getRole() : "assistant")
                                        .content(internal.getMessage())
                                        .build())
                                .finishReason("stop")
                                .build()
                ))
                .usage(ChatOpenAiResponse.Usage.builder()
                        .promptTokens(internal.getPromptTokens())
                        .completionTokens(internal.getCompletionTokens())
                        .totalTokens(internal.getTokens())
                        .build())
                .build();
    }
}