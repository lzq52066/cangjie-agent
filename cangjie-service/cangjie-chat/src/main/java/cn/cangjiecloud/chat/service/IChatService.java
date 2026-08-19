package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IChatService {

    /**
     * 发送对话消息：构建上下文 → 可选 RAG 检索 → 调用模型 → 持久化消息
     */
    ChatResponseDTO chat(ChatRequestDTO request);

    /**
     * 流式对话：构建上下文 → 可选 RAG 检索 → 调用模型流式输出 → 持久化消息
     * 当 openAiFormat=true 时，推送 OpenAI 兼容的 SSE 格式（用于 /v1/chat/completions）
     */
    void chatStream(ChatRequestDTO request, SseEmitter emitter, boolean openAiFormat);
}
