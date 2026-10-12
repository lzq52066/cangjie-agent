package cn.cangjiecloud.core.conversation;

/**
 * 对话回流的筛选条件。
 * <p>
 * 字段用包装类型 + null 表示「不限」：{@code false} 与「不筛这一项」是两件事，
 * 拿基本类型当缺省哨兵会把「未标注的也要」和「都无所谓」混成一格。
 *
 * @param applicationId 应用 ID，null 表示全部应用
 * @param feedback      like / dislike / none，null 表示不限反馈
 * @param annotatedOnly 只要有人工标注的（TRUE）/ 只要没有的（FALSE）/ 不限（null）
 * @param limit         最多取多少条，实现方按自己的上限收敛
 */
public record ConversationFilter(String applicationId,
                                 String feedback,
                                 Boolean annotatedOnly,
                                 Integer limit) {

    /** 不限条件、只限量 */
    public static ConversationFilter of(int limit) {
        return new ConversationFilter(null, null, null, limit);
    }

    public int limitOrDefault(int defaultValue) {
        return limit == null || limit <= 0 ? defaultValue : limit;
    }
}
