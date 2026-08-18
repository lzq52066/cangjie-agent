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
import cn.cangjiecloud.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话开放接口：通过应用 API Key 鉴权，不要求 Sa-Token 登录。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.CHAT_API)
public class ChatController {

    private final IChatService chatService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final IApplicationService applicationService;

    private static final String API_KEY_HEADER = "X-API-Key";

    @PostMapping("/send")
    public R<ChatResponseDTO> send(@Valid @RequestBody ChatRequestDTO request,
                                   HttpServletRequest httpRequest) {
        validateApikey(httpRequest, request.getApplicationId());
        return R.data(chatService.chat(request));
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
        // 需要至少一个有效的 apikey 才能查询会话列表
        requireAnyApikey(httpRequest);
        return R.data(chatSessionService.listByUser(userId));
    }

    @GetMapping("/sessions/{applicationId}")
    public R<List<ChatSessionEntity>> listSessionsByApp(@PathVariable String applicationId,
                                                        HttpServletRequest httpRequest) {
        validateApikey(httpRequest, applicationId);
        return R.data(chatSessionService.listByApplication(applicationId));
    }

    @GetMapping("/messages/{sessionId}")
    public R<List<ChatMessageEntity>> listMessages(@PathVariable String sessionId,
                                                   HttpServletRequest httpRequest) {
        ChatSessionEntity session = chatSessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new ApiException("会话不存在");
        }
        validateApikey(httpRequest, session.getApplicationId());
        return R.data(chatMessageService.listBySession(sessionId));
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
