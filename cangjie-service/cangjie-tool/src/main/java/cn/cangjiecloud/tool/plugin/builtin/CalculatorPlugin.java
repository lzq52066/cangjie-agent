package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.core.plugin.PluginContext;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 计算器插件（纯 JDK 实现，无外网依赖）。
 * <p>
 * 一个插件承载多个工具，按 metadata.toolId 分发：
 * <ul>
 *     <li>builtin_calculator    —— 数学表达式求值（四则运算、幂、取余、常用函数、常量）</li>
 *     <li>builtin_unit_convert  —— 常用单位换算（长度/重量/面积/体积/时间/速度/存储/网速/温度）</li>
 * </ul>
 * 对应 tool 表记录的 implementation 配置为本类全限定名，toolType=PLUGIN。
 * <p>
 * 大模型的多位数算术并不可靠（乘除、百分比、复合表达式经常心算出错），
 * 凡是"帮我算一下"类问题都应引导模型调用本插件而非自行推算。
 */
@Component
public class CalculatorPlugin extends AbstractBuiltinPlugin {

    @Override
    public String getName() {
        return "builtin_calculator";
    }

    @Override
    public String getDescription() {
        return "内置计算工具集合：数学表达式求值、常用单位换算";
    }

    @Override
    public String getType() {
        return "tool";
    }

    @Override
    public Object execute(PluginContext context) {
        Map<String, Object> params = paramsOf(context);
        return switch (toolIdOf(context)) {
            case "builtin_calculator" -> calculate(params);
            case "builtin_unit_convert" -> unitConvert(params);
            default -> error("未知的内置工具: " + toolIdOf(context));
        };
    }

    // ------------------------------------------------------------------
    // builtin_calculator
    // ------------------------------------------------------------------

    /** 表达式求值：expression 必填，precision 控制小数位（默认 10，自动去尾零） */
    private ObjectNode calculate(Map<String, Object> params) {
        String rawExpression = str(params.get("expression"));
        if (rawExpression.isEmpty()) {
            return missing("expression");
        }
        int precision = clamp(intVal(params.get("precision"), 10), 0, 12);
        String expression = normalizeExpression(rawExpression);
        try {
            double value = new ExpressionEvaluator(expression).evaluate();
            if (Double.isNaN(value)) {
                return error("表达式结果为 NaN，请检查输入: " + rawExpression);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("expression", rawExpression);
            data.put("result", number(value, precision));
            data.put("resultDouble", value);
            data.put("isInteger", value == Math.floor(value) && !Double.isInfinite(value));
            data.put("precision", precision);
            return ok(data);
        } catch (ArithmeticException e) {
            return error("表达式计算失败: " + e.getMessage());
        } catch (RuntimeException e) {
            return error("表达式无法解析: " + e.getMessage()
                    + "。支持 + - * / % ^ 括号，pi / e 常量，以及 sqrt/abs/pow/min/max 等函数");
        }
    }

    /** 归一化全角/数学符号与尾随等号，容忍大模型常见写法 */
    private static String normalizeExpression(String expression) {
        // 全角 ASCII（！～ 区间）统一折叠为半角，覆盖全角数字、字母与运算符
        StringBuilder folded = new StringBuilder(expression.length());
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c >= 0xFF01 && c <= 0xFF5E) {
                folded.append((char) (c - 0xFEE0));
            } else if (c == '　') {
                folded.append(' ');
            } else {
                folded.append(c);
            }
        }
        String text = folded.toString()
                .replace('×', '*').replace('÷', '/')
                .replace('−', '-').replace('–', '-').replace('—', '-')
                .replace('，', ',').replace('、', ',')
                .replace("²", "^2").replace("³", "^3")
                .replace("⁴", "^4");
        text = text.replace("π", "pi");
        text = text.replaceAll("[=]+\\s*$", "");
        return text.trim();
    }

    /**
     * 递归下降表达式求值器。
     * <pre>
     *   expr    := term (('+' | '-') term)*
     *   term    := unary (('*' | '/' | '%' | 'mod' | 隐式乘法) unary)*
     *   unary   := ('+' | '-')* power
     *   power   := primary ('^' unary)?              // 幂，右结合，支持 -2^2 / 2^3^2
     *   primary := number | constant | func '(' args ')' | '(' expr ')'
     * </pre>
     */
    private static final class ExpressionEvaluator {

        private final String text;
        private int pos;

        ExpressionEvaluator(String text) {
            this.text = text;
        }

        double evaluate() {
            double value = parseExpression();
            skipSpaces();
            if (pos < text.length()) {
                throw new IllegalArgumentException("位置 " + pos + " 之后存在无法识别的内容: " + text.substring(pos));
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                char c = peek();
                if (c == '+') {
                    pos++;
                    value += parseTerm();
                } else if (c == '-') {
                    pos++;
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseUnary();
            while (true) {
                char c = peek();
                if (c == '*') {
                    pos++;
                    value *= parseUnary();
                } else if (c == '/') {
                    pos++;
                    value = divide(value, parseUnary());
                } else if (c == '%') {
                    pos++;
                    value = modulo(value, parseUnary());
                } else if (matchKeyword("mod")) {
                    value = modulo(value, parseUnary());
                } else if (c == '(' || Character.isLetter(c)) {
                    // 隐式乘法：2(3+4) / 2pi
                    value *= parseUnary();
                } else {
                    return value;
                }
            }
        }

        /** 若当前位置是完整关键字（后面不接字母数字）则跳过并返回 true */
        private boolean matchKeyword(String keyword) {
            skipSpaces();
            if (!text.regionMatches(true, pos, keyword, 0, keyword.length())) {
                return false;
            }
            int next = pos + keyword.length();
            if (next < text.length() && Character.isLetterOrDigit(text.charAt(next))) {
                return false;
            }
            pos = next;
            return true;
        }

        private double parseUnary() {
            boolean negative = false;
            while (true) {
                char c = peek();
                if (c == '-') {
                    negative = !negative;
                    pos++;
                } else if (c == '+') {
                    pos++;
                } else {
                    break;
                }
            }
            double value = parsePower();
            return negative ? -value : value;
        }

        private double parsePower() {
            double base = parsePrimary();
            if (peek() == '^') {
                pos++;
                // 右结合，且指数允许一元符号：2^-3
                return Math.pow(base, parseUnary());
            }
            return base;
        }

        private double parsePrimary() {
            skipSpaces();
            if (pos >= text.length()) {
                throw new IllegalArgumentException("表达式不完整，缺少操作数");
            }
            char c = text.charAt(pos);
            if (c == '(') {
                pos++;
                double value = parseExpression();
                if (peek() != ')') {
                    throw new IllegalArgumentException("缺少右括号 )");
                }
                pos++;
                return value;
            }
            if (Character.isDigit(c) || c == '.') {
                return parseNumber();
            }
            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                String word = parseWord();
                switch (word.toLowerCase(Locale.ROOT)) {
                    case "pi" -> {
                        return Math.PI;
                    }
                    case "e" -> {
                        return Math.E;
                    }
                    default -> {
                    }
                }
                if (peek() == '(') {
                    return applyFunction(word, parseArguments());
                }
                throw new IllegalArgumentException("未知的标识符: " + word
                        + "（位于位置 " + start + "）");
            }
            throw new IllegalArgumentException("无法识别的字符: " + c + "（位于位置 " + pos + "）");
        }

        private List<Double> parseArguments() {
            pos++; // '('
            List<Double> args = new ArrayList<>(2);
            skipSpaces();
            if (peek() == ')') {
                pos++;
                return args;
            }
            while (true) {
                args.add(parseExpression());
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ')') {
                    pos++;
                    return args;
                }
                throw new IllegalArgumentException("函数参数列表不完整");
            }
        }

        private double applyFunction(String name, List<Double> args) {
            String key = name.toLowerCase(Locale.ROOT);
            return switch (key) {
                case "abs" -> Math.abs(first(key, args));
                case "sqrt" -> {
                    requireExact(key, args, 1);
                    double v = args.get(0);
                    if (v < 0) {
                        throw new ArithmeticException("sqrt 的入参不能为负数: " + v);
                    }
                    yield Math.sqrt(v);
                }
                case "cbrt" -> Math.cbrt(first(key, args));
                case "round" -> {
                    requireAtLeast(key, args, 1);
                    if (args.size() == 1) {
                        yield (double) Math.round(args.get(0));
                    }
                    requireExact(key, args, 2);
                    yield BigDecimal.valueOf(args.get(0))
                            .setScale(clampScale(args.get(1).intValue()), RoundingMode.HALF_UP).doubleValue();
                }
                case "floor" -> Math.floor(first(key, args));
                case "ceil" -> Math.ceil(first(key, args));
                case "sin" -> Math.sin(first(key, args));
                case "cos" -> Math.cos(first(key, args));
                case "tan" -> Math.tan(first(key, args));
                case "asin", "acos", "atan" -> {
                    requireExact(key, args, 1);
                    double v = args.get(0);
                    if (v < -1 || v > 1) {
                        throw new ArithmeticException(key + " 的入参必须在 -1 到 1 之间: " + v);
                    }
                    yield switch (key) {
                        case "asin" -> Math.asin(v);
                        case "acos" -> Math.acos(v);
                        default -> Math.atan(v);
                    };
                }
                case "ln" -> {
                    requirePositive(key, args);
                    yield Math.log(args.get(0));
                }
                case "log", "log10" -> {
                    requirePositive(key, args);
                    yield Math.log10(args.get(0));
                }
                case "log2" -> {
                    requirePositive(key, args);
                    yield Math.log(args.get(0)) / Math.log(2);
                }
                case "exp" -> Math.exp(first(key, args));
                case "pow" -> {
                    requireExact(key, args, 2);
                    yield Math.pow(args.get(0), args.get(1));
                }
                case "min" -> fold(key, args, true);
                case "max" -> fold(key, args, false);
                case "sum" -> sum(requireAtLeast(key, args, 1));
                case "avg", "mean" -> sum(requireAtLeast(key, args, 1)) / args.size();
                default -> throw new IllegalArgumentException("不支持的函数: " + name);
            };
        }

        private static double sum(List<Double> args) {
            double total = 0;
            for (double v : args) {
                total += v;
            }
            return total;
        }

        private static double fold(String name, List<Double> args, boolean min) {
            requireAtLeast(name, args, 1);
            double result = args.get(0);
            for (double v : args) {
                result = min ? Math.min(result, v) : Math.max(result, v);
            }
            return result;
        }

        private static int clampScale(int scale) {
            return Math.max(0, Math.min(12, scale));
        }

        private static double first(String name, List<Double> args) {
            return requireAtLeast(name, args, 1).get(0);
        }

        private static void requireExact(String name, List<Double> args, int size) {
            if (args.size() != size) {
                throw new IllegalArgumentException("函数 " + name + " 需要 " + size + " 个参数，实际 " + args.size());
            }
        }

        private static List<Double> requireAtLeast(String name, List<Double> args, int size) {
            if (args.size() < size) {
                throw new IllegalArgumentException("函数 " + name + " 至少需要 " + size + " 个参数");
            }
            return args;
        }

        private static void requirePositive(String name, List<Double> args) {
            requireExact(name, args, 1);
            if (args.get(0) <= 0) {
                throw new ArithmeticException("函数 " + name + " 的入参必须大于 0: " + args.get(0));
            }
        }

        private double divide(double left, double right) {
            if (right == 0) {
                throw new ArithmeticException("除数不能为 0");
            }
            return left / right;
        }

        private double modulo(double left, double right) {
            if (right == 0) {
                throw new ArithmeticException("取余的除数不能为 0");
            }
            return left % right;
        }

        private double parseNumber() {
            int start = pos;
            boolean dot = false;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    pos++;
                } else if ((c == 'e' || c == 'E') && pos > start && !dotOnly(text.substring(start, pos))) {
                    int mark = pos;
                    pos++;
                    if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                        pos++;
                    }
                    if (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                        while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                            pos++;
                        }
                    } else {
                        pos = mark;
                        break;
                    }
                } else {
                    break;
                }
            }
            String literal = text.substring(start, pos);
            try {
                return Double.parseDouble(literal);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("非法数字: " + literal + "（位于位置 " + start + "）");
            }
        }

        private static boolean dotOnly(String literal) {
            return literal.isEmpty() || ".".equals(literal);
        }

        private String parseWord() {
            int start = pos;
            while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
                pos++;
            }
            return text.substring(start, pos);
        }

        private char peek() {
            skipSpaces();
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        private void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }
    }

    // ------------------------------------------------------------------
    // builtin_unit_convert
    // ------------------------------------------------------------------

    /** 类别 → (归一化单位 → 相对基准单位的倍率) */
    private static final Map<String, Map<String, BigDecimal>> UNITS = new LinkedHashMap<>();

    /** 温度单位（非线性换算，单独处理） */
    private static final Set<String> TEMPERATURE = Set.of("c", "k", "f", "℃", "℉", "摄氏度", "华氏度", "开尔文");

    /** 类别的中文/别名写法 → 标准类别键 */
    private static final Map<String, String> CATEGORY_ALIAS = Map.ofEntries(
            Map.entry("长度", "length"), Map.entry("距离", "length"),
            Map.entry("重量", "weight"), Map.entry("质量", "weight"), Map.entry("体重", "weight"),
            Map.entry("面积", "area"),
            Map.entry("体积", "volume"), Map.entry("容积", "volume"),
            Map.entry("时间", "time"),
            Map.entry("速度", "speed"),
            Map.entry("存储", "storage"),
            Map.entry("网速", "data_rate"), Map.entry("带宽", "data_rate"), Map.entry("传输速率", "data_rate"),
            Map.entry("温度", "temperature"));

    static {
        // 基准单位：米 / 千克 / 平方米 / 升 / 秒 / 米每秒 / 字节 / 字节每秒
        register("length", "nm=1e-9", "um=1e-6", "mm=0.001", "cm=0.01", "m=1", "km=1000",
                "in=0.0254", "ft=0.3048", "yd=0.9144", "mi=1609.344", "nmi=1852",
                "纳米=1e-9", "微米=1e-6", "毫米=0.001", "厘米=0.01", "米=1", "公里=1000", "千米=1000",
                "英寸=0.0254", "英尺=0.3048", "码=0.9144", "英里=1609.344", "海里=1852", "里=500");
        register("weight", "mg=1e-6", "g=0.001", "kg=1", "t=1000",
                "oz=0.028349523125", "lb=0.45359237", "jin=0.5", "liang=0.05",
                "毫克=1e-6", "克=0.001", "千克=1", "公斤=1", "吨=1000", "斤=0.5", "两=0.05",
                "盎司=0.028349523125", "磅=0.45359237");
        register("area", "sqmm=1e-6", "sqcm=1e-4", "sqm=1", "ha=10000", "sqkm=1000000",
                "sqin=0.00064516", "sqft=0.09290304", "sqyd=0.83612736", "mu=666.6666666667",
                "平方毫米=1e-6", "平方厘米=1e-4", "平方米=1", "平米=1", "公顷=10000",
                "平方公里=1000000", "平方英尺=0.09290304", "亩=666.6666666667");
        register("volume", "cbcm=1e-6", "ml=0.001", "l=1", "cbm=1000",
                "floz=0.0295735295625", "pint=0.473176473", "qt=0.946352946", "gal=3.785411784",
                "立方厘米=1e-6", "毫升=0.001", "升=1", "立方米=1000", "加仑=3.785411784");
        register("time", "ms=0.001", "s=1", "min=60", "h=3600", "d=86400", "wk=604800",
                "毫秒=0.001", "秒=1", "分钟=60", "小时=3600", "天=86400", "周=604800");
        register("speed", "mps=1", "m/s=1", "kmh=0.2777777777777778", "km/h=0.2777777777777778",
                "mph=0.44704", "mi/h=0.44704", "kn=0.5144444444444445", "kt=0.5144444444444445",
                "ftps=0.3048", "ft/s=0.3048",
                "米每秒=1", "公里每小时=0.2777777777777778", "千米每小时=0.2777777777777778",
                "英里每小时=0.44704", "节=0.5144444444444445");
        register("storage", "bit=0.125", "byte=1", "kb=1024", "mb=1048576", "gb=1073741824",
                "tb=1099511627776", "pb=1125899906842624",
                "位=0.125", "比特=0.125", "字节=1", "千字节=1024", "千位=0.125");
        // 同时登记 bps 与 b/s 两种写法，模型两种返回都常见
        register("data_rate", "bps=0.125", "b/s=0.125", "byteps=1", "byte/s=1",
                "kbps=128", "kb/s=128", "mbps=131072", "mb/s=131072",
                "gbps=134217728", "gb/s=134217728",
                "比特每秒=0.125", "字节每秒=1", "千比特每秒=128", "千字节每秒=128",
                "兆比特每秒=131072", "兆字节每秒=131072");
    }

    private static void register(String category, String... specs) {
        Map<String, BigDecimal> units = UNITS.computeIfAbsent(category, key -> new LinkedHashMap<>());
        for (String spec : specs) {
            int split = spec.indexOf('=');
            if (split <= 0) {
                throw new IllegalStateException("内置单位配置错误: " + spec);
            }
            String unit = spec.substring(0, split).trim().toLowerCase(Locale.ROOT);
            BigDecimal factor = new BigDecimal(spec.substring(split + 1).trim(), MathContext.DECIMAL128);
            units.put(unit, factor);
        }
    }

    /**
     * 单位换算。category 可选，用于同名单位跨类别消歧；温度走独立分支。
     */
    private ObjectNode unitConvert(Map<String, Object> params) {
        String valueText = str(params.get("value"));
        String from = normalizeUnit(str(params.get("from")));
        String to = normalizeUnit(str(params.get("to")));
        String category = normalizeCategory(str(params.get("category")));
        if (valueText.isEmpty()) {
            return missing("value");
        }
        double value;
        try {
            value = Double.parseDouble(valueText);
        } catch (NumberFormatException e) {
            return error("value 必须是数字: " + valueText);
        }
        if (from.isEmpty() || to.isEmpty()) {
            return error("from 和 to 不能为空");
        }
        int precision = clamp(intVal(params.get("precision"), 10), 0, 12);
        if (!category.isEmpty() && !UNITS.containsKey(category) && !"temperature".equals(category)) {
            return error("未知类别: " + str(params.get("category"))
                    + "。支持的类别: " + String.join(" / ", UNITS.keySet()) + " / temperature");
        }
        if (TEMPERATURE.contains(from) && TEMPERATURE.contains(to)) {
            return temperatureConvert(value, from, to, precision);
        }
        UnitSource source = resolveUnit(from, category);
        if (source == null) {
            return error("无法识别的源单位: " + str(params.get("from"))
                    + (category.isEmpty() ? unknownCategoryHint(from) : "（类别 " + category + "）"));
        }
        UnitSource target = resolveUnit(to, source.category());
        if (target == null) {
            return error("无法识别目标单位: " + str(params.get("to"))
                    + "，或与源单位不同类（" + from + " 属于 " + source.category() + "）"
                    + unknownCategoryHint(to));
        }
        BigDecimal result = source.factor().multiply(BigDecimal.valueOf(value))
                .divide(target.factor(), MathContext.DECIMAL128);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category", source.category());
        data.put("input", value + " " + from);
        data.put("from", from);
        data.put("to", to);
        data.put("result", decimal(result, precision));
        data.put("resultDouble", result.doubleValue());
        return ok(data);
    }

    private ObjectNode temperatureConvert(double value, String from, String to, int precision) {
        String fromKey = temperatureKey(from);
        String toKey = temperatureKey(to);
        double celsius = switch (fromKey) {
            case "f" -> (value - 32) / 1.8;
            case "k" -> value - 273.15;
            default -> value;
        };
        double result = switch (toKey) {
            case "f" -> celsius * 1.8 + 32;
            case "k" -> celsius + 273.15;
            default -> celsius;
        };
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category", "temperature");
        data.put("input", value + " " + from);
        data.put("from", from);
        data.put("to", to);
        data.put("celsius", number(celsius, precision));
        data.put("result", number(result, precision));
        data.put("resultDouble", result);
        return ok(data);
    }

    private static String temperatureKey(String unit) {
        return switch (unit) {
            case "f", "℉", "华氏度" -> "f";
            case "k", "开尔文" -> "k";
            default -> "c";
        };
    }

    private record UnitSource(String category, BigDecimal factor) {}

    /**
     * 查找单位。指定 category 时只在该类别内查找（避免 mi 被当作 min 静默跨类换算）；
     * 未指定时要求全局唯一类别命中，歧义则返回 null。
     */
    private UnitSource resolveUnit(String unit, String category) {
        if (!category.isEmpty()) {
            Map<String, BigDecimal> units = UNITS.get(category);
            BigDecimal factor = units == null ? null : units.get(unit);
            return factor == null ? null : new UnitSource(category, factor);
        }
        String matched = null;
        BigDecimal factor = null;
        for (Map.Entry<String, Map<String, BigDecimal>> entry : UNITS.entrySet()) {
            BigDecimal hit = entry.getValue().get(unit);
            if (hit == null) {
                continue;
            }
            if (matched != null && !matched.equals(entry.getKey())) {
                return null;
            }
            matched = entry.getKey();
            factor = hit;
        }
        return matched == null ? null : new UnitSource(matched, factor);
    }

    private String unknownCategoryHint(String unit) {
        List<String> categories = new ArrayList<>(4);
        for (Map.Entry<String, Map<String, BigDecimal>> entry : UNITS.entrySet()) {
            if (entry.getValue().containsKey(unit)) {
                categories.add(entry.getKey());
            }
        }
        if (categories.size() > 1) {
            return "，该单位在多个类别中存在（" + String.join(" / ", categories) + "），请通过 category 指定";
        }
        return "。支持的类别: " + String.join(" / ", UNITS.keySet()) + "，以及温度 c / k / f";
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "";
        }
        String key = category.trim();
        String alias = CATEGORY_ALIAS.get(key);
        return (alias != null ? alias : key).toLowerCase(Locale.ROOT);
    }

    private static String normalizeUnit(String unit) {
        if (unit == null) {
            return "";
        }
        // 只去掉空格与点号；保留斜杠，避免 m/s（米每秒）被折叠成 ms（毫秒）而与时间单位冲突
        String text = unit.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "").replace(".", "");
        return switch (text) {
            case "celsius", "centigrade", "degc" -> "c";
            case "fahrenheit", "degf" -> "f";
            case "kelvin" -> "k";
            default -> text;
        };
    }
}
