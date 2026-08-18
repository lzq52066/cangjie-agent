package cn.cangjiecloud.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;

import java.util.List;

public interface IChatSessionService extends IService<ChatSessionEntity> {

    List<ChatSessionEntity> listByUser(String userId);

    List<ChatSessionEntity> listByApplication(String applicationId);

    ChatSessionEntity close(String sessionId);

    ChatSessionEntity getBySessionId(String sessionId);

    /**
     * 物理删除会话及其所有消息
     */
    void deleteBySessionId(String sessionId);
}
