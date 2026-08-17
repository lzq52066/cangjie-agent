package cn.cangjiecloud.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.mapper.ChatMessageMapper;
import cn.cangjiecloud.chat.service.IChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageEntity>
        implements IChatMessageService {

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
}
