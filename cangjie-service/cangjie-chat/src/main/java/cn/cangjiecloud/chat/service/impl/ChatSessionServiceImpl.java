package cn.cangjiecloud.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.mapper.ChatSessionMapper;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSessionEntity>
        implements IChatSessionService {

    @Autowired
    private IChatMessageService chatMessageService;

    @Override
    public List<ChatSessionEntity> listByApplication(String applicationId) {
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSessionEntity::getApplicationId, applicationId)
                .orderByDesc(ChatSessionEntity::getCreateTime);
        return list(wrapper);
    }

    @Override
    public List<ChatSessionEntity> listByApplicationAndUser(String applicationId, String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSessionEntity::getApplicationId, applicationId)
                .eq(ChatSessionEntity::getUserId, userId)
                .orderByDesc(ChatSessionEntity::getCreateTime);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionEntity close(String sessionId) {
        ChatSessionEntity entity = getBySessionId(sessionId);
        if (entity == null) {
            throw new ApiException("会话不存在");
        }
        entity.setStatus("closed");
        updateById(entity);
        log.info("会话已关闭: {}", sessionId);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySessionId(String sessionId) {
        ChatSessionEntity entity = getBySessionId(sessionId);
        if (entity == null) {
            throw new ApiException("会话不存在");
        }
        // 先删除消息
        chatMessageService.deleteBySession(sessionId);
        // 再删除会话
        removeById(entity.getId());
        log.info("会话已物理删除: {}", sessionId);
    }

    @Override
    public ChatSessionEntity getBySessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getSessionId, sessionId)
                .last("LIMIT 1"));
    }
}
