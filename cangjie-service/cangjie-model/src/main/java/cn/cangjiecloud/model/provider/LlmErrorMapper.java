package cn.cangjiecloud.model.provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM 调用错误码 → 中文提示映射工具
 * <p>
 * 在 OpenAICompatibleClient 捕获异常时调用，将原始英文错误转为用户可读的中文描述。
 * 格式：中文提示（原始错误信息）
 */
public final class LlmErrorMapper {

    private static final Map<Pattern, String> PATTERNS = new LinkedHashMap<>();

    static {
        // 顺序敏感：越具体的模式越靠前
        PATTERNS.put(Pattern.compile("(?i)insufficient.*balance"), "模型账号余额不足，请充值后重试");
        PATTERNS.put(Pattern.compile("(?i)rate.?limit.*exceeded|too.?many.?requests"), "模型调用频率超限，请稍后重试");
        PATTERNS.put(Pattern.compile("(?i)invalid.*api.?key|unauthorized|authentication"), "模型 API Key 无效或已过期");
        PATTERNS.put(Pattern.compile("(?i)context.?length.*exceeded|maximum.?context.?length|token.*limit"), "对话上下文超出模型限制，请精简消息或开启新对话");
        PATTERNS.put(Pattern.compile("(?i)model.?not.?found"), "配置的模型不存在，请检查模型设置");
        PATTERNS.put(Pattern.compile("(?i)server.?error|internal.?server|service.?unavailable"), "模型服务暂时异常，请稍后重试");
        PATTERNS.put(Pattern.compile("(?i)timeout|timed.?out"), "模型调用超时，请重试");
        PATTERNS.put(Pattern.compile("(?i)connection.*refused|connect.*fail|network"), "模型连接失败，请检查网络和模型地址配置");
        PATTERNS.put(Pattern.compile("(?i)quota.*exceeded|billing"), "模型配额已用尽，请联系管理员");
        PATTERNS.put(Pattern.compile("(?i)content.?filter|content.?policy|safety"), "内容被模型安全策略拦截");
    }

    /**
     * 将原始错误信息映射为包含中文提示的友好消息。
     *
     * @param rawError 原始错误信息（如 "Insufficient Balance"）
     * @return 包含中文提示的消息，格式为 "中文提示（原始错误）"；无匹配时返回 rawError 本身
     */
    public static String map(String rawError) {
        if (rawError == null || rawError.isBlank()) {
            return "未知错误";
        }
        for (Map.Entry<Pattern, String> entry : PATTERNS.entrySet()) {
            if (entry.getKey().matcher(rawError).find()) {
                return entry.getValue() + "（" + rawError + "）";
            }
        }
        return rawError;
    }

    private LlmErrorMapper() {
    }
}