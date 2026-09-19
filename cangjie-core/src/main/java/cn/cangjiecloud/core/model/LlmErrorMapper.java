package cn.cangjiecloud.core.model;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM 调用错误分类与提示映射工具。
 * <p>
 * 分类优先依据 langchain4j 的异常类型（见 {@code dev.langchain4j.internal.ExceptionMapper}：
 * 429 → RateLimitException、5xx → InternalServerException、400 → InvalidRequestException 等），
 * 类型无法判定时回落到错误文本正则（覆盖 Ollama 等未走标准映射的厂商）。
 * <p>
 * 分类结果同时给出「用户可读中文提示」与「是否值得重试」，前者供展示、后者供
 * {@code OpenAICompatibleClient} 的自动重试判定。
 */
public final class LlmErrorMapper {

    /**
     * LLM 调用错误分类
     */
    public enum LlmErrorKind {

        /** 账号余额不足（充值后可恢复，但重试无意义） */
        INSUFFICIENT_BALANCE("模型账号余额不足，请充值后重试", false),
        /** 频率超限 */
        RATE_LIMIT("模型调用频率超限，请稍后重试", true),
        /** 凭证无效 */
        AUTHENTICATION("模型 API Key 无效或已过期", false),
        /** 上下文超长 */
        CONTEXT_LENGTH("对话上下文超出模型限制，请精简消息或开启新对话", false),
        /** 模型不存在 */
        MODEL_NOT_FOUND("配置的模型不存在，请检查模型设置", false),
        /** 服务端临时异常（5xx） */
        SERVER_ERROR("模型服务暂时异常，请稍后重试", true),
        /** 调用超时 */
        TIMEOUT("模型调用超时，请重试", true),
        /** 网络/地址不可达 */
        NETWORK("模型连接失败，请检查网络和模型地址配置", true),
        /** 配额用尽 */
        QUOTA_EXCEEDED("模型配额已用尽，请联系管理员", false),
        /** 内容被安全策略拦截 */
        CONTENT_FILTERED("内容被模型安全策略拦截", false),
        /** 未能识别 */
        UNKNOWN(null, false);

        private final String friendlyMessage;
        private final boolean retriable;

        LlmErrorKind(String friendlyMessage, boolean retriable) {
            this.friendlyMessage = friendlyMessage;
            this.retriable = retriable;
        }

        /** 用户可读提示；UNKNOWN 无固定提示，直接回显原始错误 */
        public String friendlyMessage() {
            return friendlyMessage;
        }

        /** 该类别是否值得原样重试 */
        public boolean retriable() {
            return retriable;
        }
    }

    /** 文本兜底规则：顺序敏感，越具体的模式越靠前 */
    private static final Map<Pattern, LlmErrorKind> TEXT_RULES = new LinkedHashMap<>();

    static {
        TEXT_RULES.put(Pattern.compile("(?i)insufficient.*balance"), LlmErrorKind.INSUFFICIENT_BALANCE);
        TEXT_RULES.put(Pattern.compile("(?i)rate.?limit.*exceeded|too.?many.?requests"), LlmErrorKind.RATE_LIMIT);
        TEXT_RULES.put(Pattern.compile("(?i)invalid.*api.?key|unauthorized|authentication"), LlmErrorKind.AUTHENTICATION);
        TEXT_RULES.put(Pattern.compile("(?i)context.?length.*exceeded|maximum.?context.?length|token.*limit"),
                LlmErrorKind.CONTEXT_LENGTH);
        TEXT_RULES.put(Pattern.compile("(?i)model.?not.?found"), LlmErrorKind.MODEL_NOT_FOUND);
        TEXT_RULES.put(Pattern.compile("(?i)server.?error|internal.?server|service.?unavailable"), LlmErrorKind.SERVER_ERROR);
        TEXT_RULES.put(Pattern.compile("(?i)timeout|timed.?out"), LlmErrorKind.TIMEOUT);
        TEXT_RULES.put(Pattern.compile("(?i)connection.*refused|connect.*fail|network"), LlmErrorKind.NETWORK);
        TEXT_RULES.put(Pattern.compile("(?i)quota.*exceeded|billing"), LlmErrorKind.QUOTA_EXCEEDED);
        TEXT_RULES.put(Pattern.compile("(?i)content.?filter|content.?policy|safety"), LlmErrorKind.CONTENT_FILTERED);
    }

    /**
     * 按异常类型分类，类型不足以判定时回落到错误文本。
     *
     * @param error 原始异常（可为 null）
     * @return 分类结果，无法判定时为 {@link LlmErrorKind#UNKNOWN}
     */
    public static LlmErrorKind classify(Throwable error) {
        Throwable candidate = findLangChain4jCause(error);
        LlmErrorKind byType = classifyByType(candidate);
        if (byType != LlmErrorKind.UNKNOWN) {
            return byType;
        }
        return classifyByText(candidate == null ? null : candidate.getMessage());
    }

    /**
     * 该异常是否值得原样重试（仅限瞬时故障：限流 / 5xx / 超时 / 网络）。
     */
    public static boolean isRetriable(Throwable error) {
        return classify(error).retriable();
    }

    /**
     * 将异常转换为包含中文提示的友好消息。
     *
     * @return 格式为 "中文提示（原始错误）"；分类未知时返回原始错误消息
     */
    public static String map(Throwable error) {
        String raw = error == null ? null : error.getMessage();
        LlmErrorKind kind = classify(error);
        return buildMessage(kind, raw);
    }

    /**
     * 将原始错误文本转换为包含中文提示的友好消息（无异常对象时的入口）。
     *
     * @param rawError 原始错误信息（如 "Insufficient Balance"）
     * @return 格式为 "中文提示（原始错误）"；无匹配时返回 rawError 本身
     */
    public static String map(String rawError) {
        return buildMessage(classifyByText(rawError), rawError);
    }

    private static String buildMessage(LlmErrorKind kind, String raw) {
        if (raw == null || raw.isBlank()) {
            return kind.friendlyMessage() != null ? kind.friendlyMessage() : "未知错误";
        }
        if (kind.friendlyMessage() == null) {
            return raw;
        }
        return kind.friendlyMessage() + "（" + raw + "）";
    }

    /**
     * 沿 cause 链寻找 langchain4j 定义的异常：HttpException / LangChain4jException 携带了
     * 最可靠的分类依据，即便被上层包装过也要能取到。
     */
    private static Throwable findLangChain4jCause(Throwable error) {
        Throwable current = error;
        Throwable fallback = error;
        while (current != null) {
            if (current instanceof HttpException || current instanceof LangChain4jException) {
                return current;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return fallback;
    }

    private static LlmErrorKind classifyByType(Throwable error) {
        if (error == null) {
            return LlmErrorKind.UNKNOWN;
        }
        // 顺序敏感：先判子类，再判父类
        if (error instanceof ContentFilteredException) {
            return LlmErrorKind.CONTENT_FILTERED;
        }
        if (error instanceof AuthenticationException) {
            return LlmErrorKind.AUTHENTICATION;
        }
        if (error instanceof ModelNotFoundException) {
            return LlmErrorKind.MODEL_NOT_FOUND;
        }
        if (error instanceof RateLimitException) {
            return LlmErrorKind.RATE_LIMIT;
        }
        if (error instanceof TimeoutException) {
            return LlmErrorKind.TIMEOUT;
        }
        if (error instanceof InternalServerException) {
            return LlmErrorKind.SERVER_ERROR;
        }
        if (error instanceof UnresolvedModelServerException) {
            return LlmErrorKind.NETWORK;
        }
        if (error instanceof HttpException http) {
            return classifyByStatusCode(http.statusCode());
        }
        if (error instanceof InvalidRequestException) {
            // 400 中唯一有明确语义的是上下文超长，其余按未知处理（不可重试）
            return classifyByText(error.getMessage());
        }
        if (error instanceof RetriableException) {
            return LlmErrorKind.SERVER_ERROR;
        }
        return LlmErrorKind.UNKNOWN;
    }

    private static LlmErrorKind classifyByStatusCode(int statusCode) {
        if (statusCode >= 500 && statusCode < 600) {
            return LlmErrorKind.SERVER_ERROR;
        }
        if (statusCode == 401 || statusCode == 403) {
            return LlmErrorKind.AUTHENTICATION;
        }
        if (statusCode == 404) {
            return LlmErrorKind.MODEL_NOT_FOUND;
        }
        if (statusCode == 408) {
            return LlmErrorKind.TIMEOUT;
        }
        if (statusCode == 429) {
            return LlmErrorKind.RATE_LIMIT;
        }
        return LlmErrorKind.UNKNOWN;
    }

    private static LlmErrorKind classifyByText(String rawError) {
        if (rawError == null || rawError.isBlank()) {
            return LlmErrorKind.UNKNOWN;
        }
        for (Map.Entry<Pattern, LlmErrorKind> entry : TEXT_RULES.entrySet()) {
            if (entry.getKey().matcher(rawError).find()) {
                return entry.getValue();
            }
        }
        return LlmErrorKind.UNKNOWN;
    }

    private LlmErrorMapper() {
    }
}
