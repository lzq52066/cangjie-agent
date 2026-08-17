package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;

public interface IChatService {

    /**
     * 发送对话消息：构建上下文 → 可选 RAG 检索 → 调用模型 → 持久化消息
     */
    ChatResponseDTO chat(ChatRequestDTO request);
}
