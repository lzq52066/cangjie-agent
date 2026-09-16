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
 * OpenAI 兼容 API 控制器
 * 提供 /v1/chat/completions 端点，支持流式和非流式调用
 * 通过 Authorization: Bearer <apikey> 鉴权
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class ChatOpenAiController {

    private final IChatService chatService;
    private final OpenApiAuthenticator authenticator;
    private final Executor chatExecutor;

    /** SSE 流式连接超时（秒） */
    @Value("${cangjie.chat.sse-timeout-seconds:600}")
    private long sseTimeoutSeconds;

    @PostMapping("/chat/completions")
    public Object chatCompletions(@Valid @RequestBody ChatOpenAiRequest request,
                                  HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        ApplicationEntity application = authenticate(httpRequest);
        ChatRequestDTO internalRequest = toInternalRequest(request, application);
        String userId = UserContext.getUserId();

        if (Boolean.TRUE.equals(request.getStream())) {
            // 流式模式：返回 SSE
            httpResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            httpResponse.setCharacterEncoding("UTF-8");
            SseEmitter emitter = new SseEmitter(sseTimeoutSeconds * 1000);
            chatExecutor.execute(() -> chatService.chatStream(internalRequest, emitter, true, userId));
            return emitter;
        }

        // 非流式模式：返回 JSON
        ChatResponseDTO internalResponse = chatService.chat(internalRequest);
        return toOpenAiResponse(internalResponse, request.getModel());
    }

    /**
     * 统一鉴权：支持应用 API Key（企业后端到后端）与企业 OIDC JWT（企业用户直连）。
     */
    private ApplicationEntity authenticate(HttpServletRequest request) {
        ApiAuthResult result = authenticator.authenticate(authenticator.resolveToken(request));
        if (result == null) {
            throw new ApiException("凭证无效或应用未发布");
        }
        return result.getApplication();
    }

    /**
     * 将 OpenAI 格式请求转换为内部格式
     */
    private ChatRequestDTO toInternalRequest(ChatOpenAiRequest request, ApplicationEntity application) {
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setApplicationId(application.getId());
        dto.setSource("openai-api");
        dto.setStream(request.getStream());

        // 提取最后一条 user 消息作为主消息内容（用于 RAG 检索与会话记录）
        String userMessage = extractLastUserMessage(request.getMessages());
        dto.setMessage(StringUtils.hasText(userMessage) ? userMessage : "");

        // 透传完整对话上下文（system + user + assistant），保留多轮历史
        dto.setMessages(convertMessages(request.getMessages()));

        return dto;
    }

    /**
     * 将 OpenAI 消息列表转换为内部对话消息列表
     */
    private List<ChatRequestDTO.ConversationMessage> convertMessages(
            java.util.List<ChatOpenAiRequest.Message> messages) {
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
    private String extractLastUserMessage(java.util.List<ChatOpenAiRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatOpenAiRequest.Message msg = messages.get(i);
            if ("user".equalsIgnoreCase(msg.getRole()) && StringUtils.hasText(msg.getContent())) {
                return msg.getContent();
            }
        }
        // 兜底：返回第一条有内容的消息
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