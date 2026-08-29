package cn.cangjiecloud.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.mapper.ChatMessageMapper;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageEntity>
        implements IChatMessageService {

    private static final Set<String> VALID_FEEDBACKS = Set.of("like", "dislike", "none");

    @Override
    public List<ChatMessageEntity> listBySession(String sessionId) {
        return list(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySession(String sessionId) {
        remove(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId));
        log.info("已清空会话消息: {}", sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageEntity feedback(String messageId, String feedback) {
        String normalized = feedback == null ? "none" : feedback.toLowerCase();
        if (!VALID_FEEDBACKS.contains(normalized)) {
            throw new ApiException("反馈值非法，仅支持 like / dislike / none");
        }
        ChatMessageEntity message = requireAssistantMessage(messageId);
        message.setFeedback(normalized);
        updateById(message);
        log.info("对话反馈已记录: message={}, feedback={}", messageId, normalized);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageEntity annotate(String messageId, String annotation) {
        if (annotation == null || annotation.isBlank()) {
            throw new ApiException("标注内容不能为空");
        }
        ChatMessageEntity message = requireAssistantMessage(messageId);
        message.setAnnotation(annotation);
        message.setAnnotateBy(UserContext.getUserId());
        message.setAnnotateTime(LocalDateTime.now());
        updateById(message);
        log.info("对话标注已记录: message={}, by={}", messageId, message.getAnnotateBy());
        return message;
    }

    private ChatMessageEntity requireAssistantMessage(String messageId) {
        ChatMessageEntity message = getById(messageId);
        if (message == null) {
            throw new ApiException("消息不存在");
        }
        if (!"assistant".equals(message.getRole())) {
            throw new ApiException("仅支持对 AI 回复进行反馈/标注");
        }
        return message;
    }
}
