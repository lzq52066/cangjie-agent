package cn.cangjiecloud.chat.controller;

import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧会话与聊天记录只读查询接口。
 * <p>
 * 对话侧接口（X-API-Key 鉴权）见 {@link ChatController}，本接口仅供后台审计排查，
 * 不提供任何写入能力。
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/chat")
public class AdminChatController {

    private final IChatSessionService sessionService;
    private final IChatMessageService messageService;

    /**
     * 会话分页查询：支持按应用 / 用户 / 来源 / 状态 / 关键词（会话 ID 或标题）筛选
     */
    @GetMapping("/sessions")
    public R<PageResult<ChatSessionEntity>> sessions(@RequestParam(required = false) String applicationId,
                                                     @RequestParam(required = false) String userId,
                                                     @RequestParam(required = false) String sessionId,
                                                     @RequestParam(required = false) String source,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                                     @RequestParam(defaultValue = "10") Integer pageSize) {
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(applicationId != null && !applicationId.isBlank(), ChatSessionEntity::getApplicationId, applicationId)
                .eq(userId != null && !userId.isBlank(), ChatSessionEntity::getUserId, userId)
                .eq(sessionId != null && !sessionId.isBlank(), ChatSessionEntity::getSessionId, sessionId)
                .eq(source != null && !source.isBlank(), ChatSessionEntity::getSource, source)
                .eq(status != null && !status.isBlank(), ChatSessionEntity::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(ChatSessionEntity::getTitle, keyword)
                        .or().like(ChatSessionEntity::getSessionId, keyword))
                .orderByDesc(ChatSessionEntity::getCreateTime);
        Page<ChatSessionEntity> page = sessionService.page(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
        return R.data(PageResult.of(page));
    }

    /**
     * 某会话的消息明细分页（按时间正序），支持按角色与内容关键词筛选
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public R<PageResult<ChatMessageEntity>> messages(@PathVariable String sessionId,
                                                     @RequestParam(required = false) String role,
                                                     @RequestParam(required = false) String feedback,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                                     @RequestParam(defaultValue = "50") Integer pageSize) {
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessageEntity::getSessionId, sessionId)
                .eq(role != null && !role.isBlank(), ChatMessageEntity::getRole, role)
                .eq(feedback != null && !feedback.isBlank(), ChatMessageEntity::getFeedback, feedback)
                .like(keyword != null && !keyword.isBlank(), ChatMessageEntity::getContent, keyword)
                .orderByAsc(ChatMessageEntity::getCreateTime);
        Page<ChatMessageEntity> page = messageService.page(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 50 : pageSize), wrapper);
        return R.data(PageResult.of(page));
    }
}
