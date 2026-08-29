package cn.cangjiecloud.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;

import java.util.List;

public interface IChatMessageService extends IService<ChatMessageEntity> {

    List<ChatMessageEntity> listBySession(String sessionId);

    void deleteBySession(String sessionId);

    /**
     * 用户反馈（点赞/点踩/取消）
     *
     * @param messageId 消息 ID
     * @param feedback  like / dislike / none
     */
    ChatMessageEntity feedback(String messageId, String feedback);

    /**
     * 人工标注：对 AI 回复填写修正答案（运营改进闭环）
     */
    ChatMessageEntity annotate(String messageId, String annotation);
}
