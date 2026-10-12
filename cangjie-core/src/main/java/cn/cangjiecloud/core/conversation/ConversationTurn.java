package cn.cangjiecloud.core.conversation;

/**
 * 对话里的一句话，回流时当成评测用例的上下文。
 *
 * @param role    user / assistant
 * @param content 消息正文
 */
public record ConversationTurn(String role, String content) {

    public boolean isUser() {
        return "user".equalsIgnoreCase(role);
    }
}
