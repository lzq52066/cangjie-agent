package cn.cangjiecloud.chat.context;

/**
 * {@code ContextRequest.attributes} 的约定键。
 * <p>
 * attributes 同时承担两个职责：工厂向 Contributor 透传业务参数（避免反向依赖实体），
 * 以及 Contributor 之间的协作通道（已产出片段、检索引用出参、加载的历史复用）。
 */
public final class ContextAttrs {

    private ContextAttrs() {
    }

    /** 本轮知识库引用来源出参，{@code List<Map<String, Object>>} */
    public static final String RETRIEVAL_SOURCES = "retrievalSources";

    /** OpenAI 兼容路径：请求携带的完整会话（含当前输入），{@code List<ChatMessage>} */
    public static final String REQUEST_CONVERSATION = "requestConversation";

    /** 已加载的历史消息（正序），供查询改写复用，{@code List<ChatMessage>} */
    public static final String HISTORY_MESSAGES = "historyMessages";

    /** 管线已产出的片段（共享引用），{@code List<ContextFragment>} */
    public static final String FRAGMENTS = "contextFragments";

    /** 应用名（规则评估上下文），String */
    public static final String APPLICATION_NAME = "applicationName";

    /** 应用描述（无模板且无检索结果时充当人设），String */
    public static final String DESCRIPTION = "description";

    /** 提示词模板 ID，String */
    public static final String PROMPT_TEMPLATE_ID = "promptTemplateId";

    /** 技能 ID 列表，{@code List<String>} */
    public static final String SKILL_IDS = "skillIds";

    /** 规则 ID 列表，{@code List<String>} */
    public static final String RULE_IDS = "ruleIds";
}
