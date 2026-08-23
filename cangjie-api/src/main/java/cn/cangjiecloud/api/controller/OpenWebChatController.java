package cn.cangjiecloud.api.controller;

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
import cn.cangjiecloud.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 网页匿名聊天接口（方案A：只传 appId，免 API Key）
 * <p>
 * 路径前缀 /api/open（免登录白名单），在 cangjie.openapi.web-anonymous=true 时免 API Key 放行，
 * 仅凭 appId 定位应用，避免把 API Key 暴露给普通网页用户。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("${cangjie.openapi.prefix:/api/open}")
public class OpenWebChatController {

    private final IApplicationService applicationService;
    private final IChatService chatService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final Executor chatExecutor;

    /** 是否允许网页匿名聊天（与拦截器开关保持一致） */
    @Value("${cangjie.openapi.web-anonymous:true}")
    private boolean webAnonymousEnabled;

    /**
     * 网页聊天应用配置（名称、描述、建议问题），供聊天页面初始化
     */
    @GetMapping("/chat/config/{applicationId}")
    public R<Map<String, Object>> config(@PathVariable String applicationId) {
        ApplicationEntity app = requirePublishedApplication(applicationId);
        Map<String, Object> result = new HashMap<>();
        result.put("applicationId", app.getId());
        result.put("name", app.getName());
        result.put("description", app.getDescription());
        result.put("suggestions", parseStringList(app.getSuggestions()));
        return R.data(result);
    }

    /**
     * 网页聊天会话列表（按应用 + 用户）
     */
    @GetMapping("/chat/sessions")
    public R<List<ChatSessionEntity>> sessions(@RequestParam String applicationId,
                                               @RequestParam(required = false) String userId) {
        requirePublishedApplication(applicationId);
        return R.data(chatSessionService.listByApplicationAndUser(applicationId, userId));
    }

    /**
     * 网页聊天历史消息
     */
    @GetMapping("/chat/sessions/{sessionId}/messages")
    public R<List<ChatMessageEntity>> messages(@PathVariable String sessionId) {
        return R.data(chatMessageService.listBySession(sessionId));
    }

    /**
     * 删除网页聊天会话
     */
    @DeleteMapping("/chat/sessions/{sessionId}")
    public R<Void> deleteSession(@PathVariable String sessionId) {
        chatSessionService.deleteBySessionId(sessionId);
        return R.ok("对话记录已删除");
    }

    /**
     * 网页聊天同步对话
     */
    @PostMapping("/chat")
    public R<ChatResponseDTO> chat(@RequestBody ChatRequestDTO request) {
        ensureWebAnonymousEnabled();
        requirePublishedApplication(request.getApplicationId());
        return R.data(chatService.chat(request));
    }

    /**
     * 网页聊天流式对话（SSE）
     */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody ChatRequestDTO request,
                                 HttpServletRequest httpRequest) {
        ensureWebAnonymousEnabled();
        requirePublishedApplication(request.getApplicationId());
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        String userId = StringUtils.hasText(request.getUserId()) ? request.getUserId() : null;
        chatExecutor.execute(() -> chatService.chatStream(request, emitter, false, userId));
        return emitter;
    }

    private void ensureWebAnonymousEnabled() {
        if (!webAnonymousEnabled) {
            throw new ApiException("网页匿名聊天已关闭，请使用 API Key 调用");
        }
    }

    private ApplicationEntity requirePublishedApplication(String applicationId) {
        if (!StringUtils.hasText(applicationId)) {
            throw new ApiException("缺少应用 ID");
        }
        ApplicationEntity app = applicationService.getById(applicationId);
        if (app == null || !"published".equals(app.getStatus())) {
            throw new ApiException("应用不存在或未发布");
        }
        return app;
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return java.util.Collections.emptyList();
        }
        try {
            return com.alibaba.fastjson.JSON.parseArray(json, String.class);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }
}