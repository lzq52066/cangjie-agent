package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.core.plugin.PluginContext;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本处理插件（纯 JDK 实现，无外网依赖）。
 * <p>
 * 一个插件承载多个工具，按 metadata.toolId 分发：
 * <ul>
 *     <li>builtin_text_stats —— 统计字符数/汉字数/单词数/行数/字节数（字数限制、摘要长度校验）</li>
 *     <li>builtin_random     —— 生成随机数、随机字符串与强口令</li>
 * </ul>
 * 对应 tool 表记录的 implementation 配置为本类全限定名，toolType=PLUGIN。
 * <p>
 * 大模型无法准确数字符（尤其中英文混排的"字数"），也不具备真正的随机性，
 * 这两类需求必须由本插件兜底。
 */
@Component
public class TextToolsPlugin extends AbstractBuiltinPlugin {

    /** 中日韩统一表意文字（含扩展 A） */
    private static final Pattern CJK = Pattern.compile("[\\u3400-\\u4dbf\\u4e00-\\u9fff]");
    /** 英文单词（含撇号与连字符，如 don't / state-of-the-art） */
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z][A-Za-z0-9'’\\-]*");
    /** 数字串（含小数、千分比等） */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final SecureRandom SECURE = new SecureRandom();

    /** 剔除易混淆字符（l o I O 0 1）后的安全字符集 */
    private static final String LOWER_SAFE = "abcdefghijkmnpqrstuvwxyz";
    private static final String UPPER_SAFE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS_SAFE = "23456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/";

    @Override
    public String getName() {
        return "builtin_text_tools";
    }

    @Override
    public String getDescription() {
        return "内置文本工具集合：文本/字数统计、随机数与随机口令生成";
    }

    @Override
    public String getType() {
        return "tool";
    }

    @Override
    public Object execute(PluginContext context) {
        Map<String, Object> params = paramsOf(context);
        return switch (toolIdOf(context)) {
            case "builtin_text_stats" -> textStats(params);
            case "builtin_random" -> random(params);
            default -> error("未知的内置工具: " + toolIdOf(context));
        };
    }

    // ------------------------------------------------------------------
    // builtin_text_stats
    // ------------------------------------------------------------------

    /** 文本统计：text 必填；maxChars &gt; 0 时额外给出是否超限的判定 */
    private ObjectNode textStats(Map<String, Object> params) {
        String text = raw(params.get("text"));
        if (text.isEmpty()) {
            return missing("text");
        }
        int maxChars = intVal(params.get("maxChars"), 0);
        int cjk = 0;
        int letters = 0;
        int digits = 0;
        int punctuation = 0;
        int whitespace = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\f' || c == '　') {
                whitespace++;
            } else if (CJK.matcher(String.valueOf(c)).matches()) {
                cjk++;
            } else if (Character.isLetter(c)) {
                letters++;
            } else if (Character.isDigit(c)) {
                digits++;
            } else if (Character.getType(c) == Character.SURROGATE) {
                // 代理对（如 emoji）按一个字符计入 characters，不重复分类
            } else if (!Character.isWhitespace(c)) {
                punctuation++;
            }
        }
        int latinWords = countMatches(LATIN_WORD, text);
        int numbers = countMatches(NUMBER, text);
        int lines = text.split("\r\n|\r|\n", -1).length;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("characters", text.length());
        data.put("charactersNoSpaces", text.replaceAll("\\s", "").length());
        data.put("chineseCharacters", cjk);
        data.put("letters", letters);
        data.put("digits", digits);
        data.put("punctuation", punctuation);
        data.put("whitespace", whitespace);
        data.put("latinWords", latinWords);
        // 中文按字计、英文按词计，符合常见"字数"口径
        data.put("words", cjk + latinWords);
        data.put("numbers", numbers);
        data.put("lines", lines);
        data.put("utf8Bytes", text.getBytes(StandardCharsets.UTF_8).length);
        if (maxChars > 0) {
            data.put("maxChars", maxChars);
            data.put("withinLimit", text.length() <= maxChars);
            data.put("exceededBy", Math.max(0, text.length() - maxChars));
        }
        return ok(data);
    }

    private static int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------
    // builtin_random
    // ------------------------------------------------------------------

    /**
     * 随机生成。type：
     * <ul>
     *     <li>int      —— 区间整数，min / max / count</li>
     *     <li>decimal  —— 区间小数，min / max / count / precision</li>
     *     <li>string   —— 字母数字串，length / count</li>
     *     <li>password —— 强口令，length + 字符集开关，保证每类至少出现一次</li>
     * </ul>
     */
    private ObjectNode random(Map<String, Object> params) {
        String type = str(params.getOrDefault("type", "int")).toLowerCase();
        int count = clamp(intVal(params.get("count"), 1), 1, 200);
        return switch (type) {
            case "int", "integer", "number" -> randomInt(params, count);
            case "decimal", "double", "float" -> randomDecimal(params, count);
            case "string", "alnum", "text" -> randomString(params, count);
            case "password", "pwd", "secret" -> randomPassword(params, count);
            default -> error("不支持的 type: " + type + "（可选 int / decimal / string / password）");
        };
    }

    private ObjectNode randomInt(Map<String, Object> params, int count) {
        long min = longVal(params.get("min"), 0);
        long max = longVal(params.get("max"), 100);
        if (min > max) {
            long swap = min;
            min = max;
            max = swap;
        }
        List<Object> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(ThreadLocalRandom.current().nextLong(min, Math.addExact(max, 1)));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "int");
        data.put("min", min);
        data.put("max", max);
        data.put("count", count);
        data.put("value", values.get(0));
        data.put("values", values);
        return ok(data);
    }

    private ObjectNode randomDecimal(Map<String, Object> params, int count) {
        double min = doubleVal(params.get("min"), 0);
        double max = doubleVal(params.get("max"), 1);
        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
        }
        int precision = clamp(intVal(params.get("precision"), 2), 0, 12);
        List<Object> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(number(min + (max - min) * ThreadLocalRandom.current().nextDouble(), precision));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "decimal");
        data.put("min", number(min, precision));
        data.put("max", number(max, precision));
        data.put("count", count);
        data.put("value", values.get(0));
        data.put("values", values);
        return ok(data);
    }

    private ObjectNode randomString(Map<String, Object> params, int count) {
        int length = clamp(intVal(params.get("length"), 16), 1, 512);
        boolean noAmbiguous = boolVal(params.get("noAmbiguous"), false);
        List<String> pools = new ArrayList<>(3);
        if (boolVal(params.get("lowercase"), true)) {
            pools.add(letters(noAmbiguous, false));
        }
        if (boolVal(params.get("uppercase"), true)) {
            pools.add(letters(noAmbiguous, true));
        }
        if (boolVal(params.get("digits"), true)) {
            pools.add(numbers(noAmbiguous));
        }
        String alphabet = String.join("", pools);
        if (alphabet.isEmpty()) {
            return error("string 类型至少需要启用 lowercase / uppercase / digits 之一");
        }
        List<Object> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(randomChars(alphabet, List.of(), length));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "string");
        data.put("length", length);
        data.put("count", count);
        data.put("value", values.get(0));
        data.put("values", values);
        return ok(data);
    }

    private ObjectNode randomPassword(Map<String, Object> params, int count) {
        int length = clamp(intVal(params.get("length"), 16), 4, 128);
        boolean noAmbiguous = boolVal(params.get("noAmbiguous"), false);
        List<String> pools = new ArrayList<>(4);
        if (boolVal(params.get("lowercase"), true)) {
            pools.add(letters(noAmbiguous, false));
        }
        if (boolVal(params.get("uppercase"), true)) {
            pools.add(letters(noAmbiguous, true));
        }
        if (boolVal(params.get("digits"), true)) {
            pools.add(numbers(noAmbiguous));
        }
        if (boolVal(params.get("symbols"), true)) {
            pools.add(SYMBOLS);
        }
        pools.removeIf(String::isEmpty);
        if (pools.isEmpty()) {
            return error("password 至少需要启用 lowercase / uppercase / digits / symbols 之一");
        }
        if (length < pools.size()) {
            return error("password 长度至少为 " + pools.size() + "（每个启用的字符类别都要至少出现一次）");
        }
        String alphabet = String.join("", pools);
        List<Object> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(randomChars(alphabet, pools, length));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "password");
        data.put("length", length);
        data.put("count", count);
        data.put("strength", assessStrength(alphabet.length(), length));
        data.put("values", values);
        return ok(data);
    }

    /** 字母字符集；noAmbiguous=true 时剔除易混淆的 l o / I O */
    private static String letters(boolean noAmbiguous, boolean uppercase) {
        if (!noAmbiguous) {
            return uppercase ? "ABCDEFGHIJKLMNOPQRSTUVWXYZ" : "abcdefghijklmnopqrstuvwxyz";
        }
        return uppercase ? UPPER_SAFE : LOWER_SAFE;
    }

    /** 数字字符集；noAmbiguous=true 时剔除 0 1 */
    private static String numbers(boolean noAmbiguous) {
        return noAmbiguous ? DIGITS_SAFE : "0123456789";
    }

    /**
     * 生成长度固定的随机串：requiredPools 中的每个字符集至少取一个字符，
     * 其余位置从全字符集随机，最后整体打散，避免必选字符集中在开头。
     */
    private static String randomChars(String alphabet, List<String> requiredPools, int length) {
        char[] result = new char[length];
        int filled = 0;
        for (String pool : requiredPools) {
            if (filled >= length) {
                break;
            }
            result[filled++] = pool.charAt(SECURE.nextInt(pool.length()));
        }
        while (filled < length) {
            result[filled++] = alphabet.charAt(SECURE.nextInt(alphabet.length()));
        }
        for (int i = length - 1; i > 0; i--) {
            int j = SECURE.nextInt(i + 1);
            char swap = result[i];
            result[i] = result[j];
            result[j] = swap;
        }
        return new String(result);
    }

    /** 口令强度：按字符集大小与长度估算信息熵 */
    private static String assessStrength(int alphabetSize, int length) {
        int bits = (int) Math.round(length * (Math.log(alphabetSize) / Math.log(2)));
        if (bits >= 60) {
            return "强（熵约 " + bits + " bit）";
        }
        if (bits >= 40) {
            return "中（熵约 " + bits + " bit）";
        }
        return "弱（熵约 " + bits + " bit，建议增加长度或启用更多字符类别）";
    }

    private static long longVal(Object value, long def) {
        if (value == null) {
            return def;
        }
        try {
            return (long) Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
