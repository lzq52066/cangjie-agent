package cn.cangjiecloud.chat.controller;

import cn.cangjiecloud.application.api.dto.ChatConfigDTO;
import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.chat.service.IChatService;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 对话开放接口：通过应用 API Key 鉴权，不要求 Sa-Token 登录。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.CHAT_API)
public class ChatController {

    private final IChatService chatService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final IApplicationService applicationService;
    private final Executor chatExecutor;

    /** SSE 流式连接超时（秒） */
    @Value("${cangjie.chat.sse-timeout-seconds:600}")
    private long sseTimeoutSeconds;

    private static final String API_KEY_HEADER = "X-API-Key";

    @PostMapping("/send")
    public R<ChatResponseDTO> send(@Valid @RequestBody ChatRequestDTO request,
                                   HttpServletRequest httpRequest) {
        validateApikey(httpRequest, request.getApplicationId());
        return R.data(chatService.chat(request));
    }

    @PostMapping("/send-stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter sendStream(
            @Valid @RequestBody ChatRequestDTO request,
            HttpServletRequest httpRequest) {
        validateApikey(httpRequest, request.getApplicationId());
        String userId = StringUtils.hasText(request.getUserId()) ? request.getUserId() : UserContext.getUserId();
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(sseTimeoutSeconds * 1000);
        chatExecutor.execute(() -> chatService.chatStream(request, emitter, false, userId));
        return emitter;
    }

    /**
     * 获取应用对话配置（名称、建议问题等），供 chat 前端初始化使用
     */
    @GetMapping("/config/{applicationId}")
    public R<ChatConfigDTO> config(@PathVariable String applicationId,
                                   HttpServletRequest httpRequest) {
        validateApikey(httpRequest, applicationId);
        ApplicationEntity app = applicationService.getById(applicationId);
        if (app == null) {
            throw new ApiException("应用不存在");
        }
        ChatConfigDTO config = new ChatConfigDTO();
        config.setTitle(app.getName());
        config.setSuggestions(parseStringList(app.getSuggestions()));
        return R.data(config);
    }

    @GetMapping("/sessions")
    public R<List<ChatSessionEntity>> listSessions(@RequestParam(required = false) String userId,
                                                  HttpServletRequest httpRequest) {
        // 会话列表限定在请求 apikey 所属应用内，防止跨应用枚举任意用户会话
        ApplicationEntity application = requireAnyApikey(httpRequest);
        return R.data(chatSessionService.listByApplicationAndUser(application.getId(), userId));
    }

    @GetMapping("/sessions/{applicationId}")
    public R<List<ChatSessionEntity>> listSessionsByApp(
            @PathVariable String applicationId,
            @RequestParam(required = false) String userId,
            HttpServletRequest httpRequest) {
        validateApikey(httpRequest, applicationId);
        return R.data(chatSessionService.listByApplicationAndUser(applicationId, userId));
    }

    @GetMapping("/messages/{sessionId}")
    public R<List<ChatMessageEntity>> listMessages(@PathVariable String sessionId,
                                                   HttpServletRequest httpRequest) {
        long t0 = System.currentTimeMillis();

        ChatSessionEntity session = chatSessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new ApiException("会话不存在");
        }
        long t1 = System.currentTimeMillis();

        validateApikey(httpRequest, session.getApplicationId());
        long t2 = System.currentTimeMillis();

        List<ChatMessageEntity> messages = chatMessageService.listBySession(sessionId);
        long t3 = System.currentTimeMillis();

        log.info("[耗时] sessionId={} | getSession={}ms | validateApikey={}ms | listMessages={}ms | total={}ms",
                sessionId, t1 - t0, t2 - t1, t3 - t2, t3 - t0);
        return R.data(messages);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public R<ChatSessionEntity> closeSession(@PathVariable String sessionId,
                                             HttpServletRequest httpRequest) {
        ChatSessionEntity session = chatSessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new ApiException("会话不存在");
        }
        validateApikey(httpRequest, session.getApplicationId());
        return R.data(chatSessionService.close(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}/delete")
    public R<Void> deleteSession(@PathVariable String sessionId,
                                 HttpServletRequest httpRequest) {
        ChatSessionEntity session = chatSessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new ApiException("会话不存在");
        }
        validateApikey(httpRequest, session.getApplicationId());
        chatSessionService.deleteBySessionId(sessionId);
        return R.ok("对话记录已删除");
    }

    /**
     * 消息反馈：点赞/点踩/取消（body: {"feedback":"like|dislike|none"}）
     */
    @PostMapping("/messages/{messageId}/feedback")
    public R<ChatMessageEntity> feedback(@PathVariable String messageId,
                                         @RequestBody java.util.Map<String, String> body,
                                         HttpServletRequest httpRequest) {
        validateMessageAccess(messageId, httpRequest);
        return R.data(chatMessageService.feedback(messageId, body.get("feedback")));
    }

    /**
     * 人工标注：为 AI 回复填写修正答案（body: {"annotation":"..."}）
     */
    @PostMapping("/messages/{messageId}/annotate")
    public R<ChatMessageEntity> annotate(@PathVariable String messageId,
                                         @RequestBody java.util.Map<String, String> body,
                                         HttpServletRequest httpRequest) {
        validateMessageAccess(messageId, httpRequest);
        return R.data(chatMessageService.annotate(messageId, body.get("annotation")));
    }

    /**
     * 消息级访问校验：请求 apikey 必须与消息所属应用匹配，防止跨应用篡改
     */
    private void validateMessageAccess(String messageId, HttpServletRequest httpRequest) {
        ChatMessageEntity message = chatMessageService.getById(messageId);
        if (message == null) {
            throw new ApiException("消息不存在");
        }
        validateApikey(httpRequest, message.getApplicationId());
    }

    /**
     * 校验请求头中的 apikey 与指定应用一致
     */
    private void validateApikey(HttpServletRequest request, String applicationId) {
        String apikey = request.getHeader(API_KEY_HEADER);
        if (!StringUtils.hasText(apikey)) {
            throw new ApiException("缺少 API Key，请在请求头 " + API_KEY_HEADER + " 中提供");
        }
        ApplicationEntity application = applicationService.getByApikey(apikey);
        if (application == null) {
            throw new ApiException("API Key 无效或应用未发布");
        }
        if (StringUtils.hasText(applicationId) && !application.getId().equals(applicationId)) {
            throw new ApiException("API Key 与应用不匹配");
        }
    }

    /**
     * 列表接口允许任意已发布的 apikey
     */
    private ApplicationEntity requireAnyApikey(HttpServletRequest request) {
        String apikey = request.getHeader(API_KEY_HEADER);
        if (!StringUtils.hasText(apikey)) {
            throw new ApiException("缺少 API Key，请在请求头 " + API_KEY_HEADER + " 中提供");
        }
        ApplicationEntity application = applicationService.getByApikey(apikey);
        if (application == null) {
            throw new ApiException("API Key 无效或应用未发布");
        }
        return application;
    }

    /**
     * 解析 JSON 数组字符串为列表
     */
    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new java.util.ArrayList<>();
        }
        try {
            return com.alibaba.fastjson.JSON.parseArray(json, String.class);
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }
}
