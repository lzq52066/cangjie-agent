package cn.cangjiecloud.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;

import java.util.List;

public interface IChatMessageService extends IService<ChatMessageEntity> {

    List<ChatMessageEntity> listBySession(String sessionId);

    void deleteBySession(String sessionId);
}
