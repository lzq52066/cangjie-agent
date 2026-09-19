package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.core.plugin.PluginContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置计算器插件测试：覆盖表达式求值（四则、优先级、幂、隐式乘法、函数、常量）、
 * 全角归一化、各类异常分支以及单位换算（含温度）。
 */
@DisplayName("内置插件 - CalculatorPlugin")
class CalculatorPluginTest {

    private static final CalculatorPlugin PLUGIN = new CalculatorPlugin();

    private static final String CALC = "builtin_calculator";
    private static final String UNIT = "builtin_unit_convert";

    /** 统一走插件公共入口：按 metadata.toolId 分发 */
    private static ObjectNode run(String toolId, Map<String, Object> params) {
        PluginContext context = PluginContext.builder()
                .params(params)
                .metadata(Map.of("toolId", toolId))
                .build();
        return (ObjectNode) PLUGIN.execute(context);
    }

    private static ObjectNode calc(Object... kv) {
        return run(CALC, map(kv));
    }

    private static ObjectNode unit(Object... kv) {
        return run(UNIT, map(kv));
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** result 字段：整数为 Long、小数为 String、非数值为 null，与插件输出形态一致 */
    private static Object result(ObjectNode r) {
        JsonNode value = r.path("result");
        if (value.isIntegralNumber()) {
            return value.longValue();
        }
        if (value.isTextual()) {
            return value.asText();
        }
        return value.isNull() || value.isMissingNode() ? null : value;
    }

    private static double resultDouble(ObjectNode r) {
        return r.path("resultDouble").asDouble();
    }

    // ------------------------------------------------------------------
    // 元信息与分发
    // ------------------------------------------------------------------

    @Test
    @DisplayName("插件元信息：name/description/type")
    void metadata() {
        assertThat(PLUGIN.getName()).isEqualTo("builtin_calculator");
        assertThat(PLUGIN.getDescription()).isEqualTo("内置计算工具集合：数学表达式求值、常用单位换算");
        assertThat(PLUGIN.getType()).isEqualTo("tool");
    }

    @Test
    @DisplayName("未知 toolId 返回未知内置工具错误")
    void unknownToolId() {
        ObjectNode r = run("builtin_not_exist", map("expression", "1+1"));
        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").asText()).isEqualTo("未知的内置工具: builtin_not_exist");
    }

    @Test
    @DisplayName("context 为 null 时按空 toolId 处理，不抛异常")
    void nullContext() {
        ObjectNode r = (ObjectNode) PLUGIN.execute(null);
        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").asText()).isEqualTo("未知的内置工具: ");
    }

    @Test
    @DisplayName("params/metadata 均为 null 时按空参数处理")
    void nullParamsAndMetadata() {
        ObjectNode r = (ObjectNode) PLUGIN.execute(PluginContext.builder().build());
        assertThat(r.path("success").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------
    // 表达式求值
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("表达式求值")
    class Evaluate {

        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource({
                "1+1, 2",
                "10-2.5, 7.5",
                "6*7, 42",
                "100/4, 25",
                "2+3*4, 14",
                "'(2+3)*4', 20",
                "'7%3', 1",
                "10 mod 3, 1",
                "'2^10', 1024",
                "'2^3^2', 512",
                "'2^-3', 0.125",
                "'-2^2', -4",
                "--2, 2",
                "+5, 5",
                "'2(3+4)', 14",
                "'(2)(3)', 6",
                "'1e3', 1000",
                "'1.5e2', 150",
                "  8 - 3 , 5"
        })
        void 常规表达式(String expression, String expected) {
            ObjectNode r = calc("expression", expression);
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(result(r)).hasToString(expected);
            assertThat(resultDouble(r)).isEqualTo(Double.parseDouble(expected));
        }

        @Test
        @DisplayName("隐式乘法 2pi 得到两倍圆周率")
        void implicitMultiplyConstant() {
            ObjectNode r = calc("expression", "2pi");
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(resultDouble(r)).isEqualTo(2 * Math.PI);
        }

        @Test
        @DisplayName("自然常数 e 参与幂运算")
        void eulerConstant() {
            ObjectNode r = calc("expression", "e^2");
            assertThat(resultDouble(r)).isEqualTo(Math.E * Math.E);
        }

        @Test
        @DisplayName("整数结果标记 isInteger=true，回填 expression 与 precision")
        void integerFlag() {
            ObjectNode r = calc("expression", "1+1");
            assertThat(r.path("isInteger").asBoolean()).isTrue();
            assertThat(result(r)).isEqualTo(2L);
            assertThat(r.path("precision").asInt()).isEqualTo(10);
            assertThat(r.path("expression").asText()).isEqualTo("1+1");
        }

        @Test
        @DisplayName("非整数结果 isInteger=false，尾零被裁掉")
        void decimalResult() {
            ObjectNode r = calc("expression", "1/3");
            assertThat(r.path("isInteger").asBoolean()).isFalse();
            assertThat(result(r)).hasToString("0.3333333333");
        }

        @Test
        @DisplayName("precision 生效：1/3 保留 2 位")
        void precisionApplied() {
            ObjectNode r = calc("expression", "1/3", "precision", 2);
            assertThat(result(r)).isEqualTo("0.33");
            assertThat(r.path("precision").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("precision 越界被夹紧到 0..12")
        void precisionClamped() {
            assertThat(calc("expression", "1/3", "precision", 99).path("precision").asInt()).isEqualTo(12);
            assertThat(calc("expression", "1/3", "precision", -5).path("precision").asInt()).isZero();
        }

        @Test
        @DisplayName("precision 以字符串形式传入（大模型常见写法）")
        void precisionAsText() {
            ObjectNode r = calc("expression", "1/3", "precision", "4");
            assertThat(r.path("precision").asInt()).isEqualTo(4);
            assertThat(result(r)).isEqualTo("0.3333");
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "'abs(-3)', 3",
                "'sqrt(16)', 4",
                "'floor(2.9)', 2",
                "'ceil(2.1)', 3",
                "'round(2.5)', 3",
                "'round(2.345, 2)', 2.35",
                "'pow(2, 10)', 1024",
                "'min(3, 1, 2)', 1",
                "'max(3, 1, 2)', 3",
                "'sum(1, 2, 3)', 6",
                "'avg(1, 2, 3)', 2",
                "'mean(2, 4)', 3",
                "'ln(e)', 1",
                "'log(100)', 2",
                "'log10(1000)', 3",
                "'log2(8)', 3",
                "'exp(0)', 1",
                "'sin(0)', 0",
                "'cos(0)', 1",
                "'tan(0)', 0",
                "'asin(1)', 1.5707963268",
                "'acos(0)', 1.5707963268",
                "'atan(1)', 0.7853981634"
        })
        void 内置函数(String expression, String expected) {
            ObjectNode r = calc("expression", expression);
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(result(r)).hasToString(expected);
        }

        @Test
        @DisplayName("cbrt 立方根（1 ulp 容差）")
        void cbrt() {
            assertThat(resultDouble(calc("expression", "cbrt(27)"))).isCloseTo(3d, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("函数名大小写不敏感")
        void functionNameCaseInsensitive() {
            assertThat(result(calc("expression", "SQRT(9)"))).isEqualTo(3L);
            assertThat(result(calc("expression", "Sqrt(9)"))).isEqualTo(3L);
        }

        @Test
        @DisplayName("MOD 关键字大小写不敏感")
        void modKeywordCaseInsensitive() {
            assertThat(result(calc("expression", "10 MOD 4"))).isEqualTo(2L);
        }
    }

    // ------------------------------------------------------------------
    // 归一化
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("表达式归一化")
    class Normalize {

        @Test
        @DisplayName("全角数字与括号折叠为半角")
        void fullWidth() {
            assertThat(result(calc("expression", "（１＋２）"))).isEqualTo(3L);
        }

        @Test
        @DisplayName("尾随等号被裁掉")
        void trailingEquals() {
            assertThat(result(calc("expression", "1+1="))).isEqualTo(2L);
            assertThat(result(calc("expression", "1+1 == "))).isEqualTo(2L);
        }

        @Test
        @DisplayName("数学乘除号与数学负号")
        void mathOperators() {
            assertThat(result(calc("expression", "2×3÷4"))).hasToString("1.5");
            assertThat(result(calc("expression", "−5+2"))).hasToString("-3");
            assertThat(result(calc("expression", "5–1"))).isEqualTo(4L);
            assertThat(result(calc("expression", "5—1"))).isEqualTo(4L);
        }

        @Test
        @DisplayName("上标幂与 π")
        void superscriptAndPi() {
            assertThat(result(calc("expression", "3²"))).isEqualTo(9L);
            assertThat(result(calc("expression", "2³"))).isEqualTo(8L);
            assertThat(result(calc("expression", "1⁴"))).isEqualTo(1L);
            assertThat(resultDouble(calc("expression", "π"))).isEqualTo(Math.PI);
        }

        @Test
        @DisplayName("全角逗号与顿号转半角逗号")
        void fullWidthComma() {
            assertThat(result(calc("expression", "min（3，1）"))).isEqualTo(1L);
            assertThat(result(calc("expression", "min(3、2)"))).isEqualTo(2L);
        }

        @Test
        @DisplayName("全角空格折叠为半角空格")
        void fullWidthSpace() {
            assertThat(result(calc("expression", "1　+　2"))).isEqualTo(3L);
        }
    }

    // ------------------------------------------------------------------
    // 异常分支
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("异常分支")
    class Failures {

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "  \t "})
        @DisplayName("expression 缺失或空白返回参数必填错误")
        void missingExpression(String expression) {
            ObjectNode r = calc("expression", expression);
            assertThat(r.path("success").asBoolean()).isFalse();
            assertThat(r.path("error").asText()).isEqualTo("参数 expression 不能为空");
        }

        @Test
        @DisplayName("完全不传 expression")
        void noExpressionAtAll() {
            assertThat(calc().path("error").asText()).isEqualTo("参数 expression 不能为空");
        }

        @Test
        @DisplayName("除零")
        void divideByZero() {
            assertThat(calc("expression", "1/0").path("error").asText())
                    .isEqualTo("表达式计算失败: 除数不能为 0");
        }

        @Test
        @DisplayName("取余除零")
        void moduloByZero() {
            assertThat(calc("expression", "1%0").path("error").asText())
                    .isEqualTo("表达式计算失败: 取余的除数不能为 0");
            assertThat(calc("expression", "1 mod 0").path("error").asText())
                    .isEqualTo("表达式计算失败: 取余的除数不能为 0");
        }

        @Test
        @DisplayName("缺少右括号")
        void missingRightParen() {
            assertThat(calc("expression", "(1+2").path("error").asText())
                    .startsWith("表达式无法解析: 缺少右括号 )");
        }

        @Test
        @DisplayName("缺少操作数")
        void incompleteExpression() {
            assertThat(calc("expression", "1+").path("error").asText())
                    .startsWith("表达式无法解析: 表达式不完整，缺少操作数");
            assertThat(calc("expression", "abs(1,").path("error").asText())
                    .startsWith("表达式无法解析: 表达式不完整，缺少操作数");
        }

        @Test
        @DisplayName("尾随无法识别的内容")
        void trailingGarbage() {
            assertThat(calc("expression", "2 @ 3").path("error").asText())
                    .startsWith("表达式无法解析: 位置 2 之后存在无法识别的内容: @ 3")
                    .endsWith("sqrt/abs/pow/min/max 等函数");
        }

        @Test
        @DisplayName("无法识别的字符")
        void unknownCharacter() {
            assertThat(calc("expression", "$1").path("error").asText())
                    .startsWith("表达式无法解析: 无法识别的字符: $（位于位置 0）");
        }

        @Test
        @DisplayName("未知标识符")
        void unknownIdentifier() {
            assertThat(calc("expression", "x + 1").path("error").asText())
                    .startsWith("表达式无法解析: 未知的标识符: x（位于位置 0）");
        }

        @Test
        @DisplayName("不支持的函数")
        void unsupportedFunction() {
            assertThat(calc("expression", "foo(1)").path("error").asText())
                    .startsWith("表达式无法解析: 不支持的函数: foo");
        }

        @Test
        @DisplayName("函数参数数量不符")
        void wrongArgumentCount() {
            assertThat(calc("expression", "pow(2)").path("error").asText())
                    .startsWith("表达式无法解析: 函数 pow 需要 2 个参数，实际 1");
            assertThat(calc("expression", "min()").path("error").asText())
                    .startsWith("表达式无法解析: 函数 min 至少需要 1 个参数");
        }

        @Test
        @DisplayName("函数参数列表不完整")
        void incompleteArgumentList() {
            assertThat(calc("expression", "abs(1 2)").path("error").asText())
                    .startsWith("表达式无法解析: 函数参数列表不完整");
        }

        @Test
        @DisplayName("sqrt 负数")
        void sqrtNegative() {
            assertThat(calc("expression", "sqrt(-4)").path("error").asText())
                    .isEqualTo("表达式计算失败: sqrt 的入参不能为负数: -4.0");
        }

        @Test
        @DisplayName("反三角函数越界")
        void asinOutOfRange() {
            assertThat(calc("expression", "asin(2)").path("error").asText())
                    .isEqualTo("表达式计算失败: asin 的入参必须在 -1 到 1 之间: 2.0");
            assertThat(calc("expression", "acos(-2)").path("error").asText())
                    .startsWith("表达式计算失败: acos 的入参必须在 -1 到 1 之间");
        }

        @Test
        @DisplayName("对数函数入参必须为正")
        void logNonPositive() {
            assertThat(calc("expression", "ln(0)").path("error").asText())
                    .isEqualTo("表达式计算失败: 函数 ln 的入参必须大于 0: 0.0");
            assertThat(calc("expression", "log(-1)").path("error").asText())
                    .isEqualTo("表达式计算失败: 函数 log 的入参必须大于 0: -1.0");
        }

        @Test
        @DisplayName("结果为无穷时数值收敛为 null 且 isInteger=false")
        void infiniteResult() {
            ObjectNode r = calc("expression", "1e308^2");
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(result(r)).isNull();
            assertThat(r.path("isInteger").asBoolean()).isFalse();
            assertThat(resultDouble(r)).isEqualTo(Double.POSITIVE_INFINITY);
        }
    }

    // ------------------------------------------------------------------
    // 单位换算
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("单位换算")
    class UnitConvert {

        @Test
        @DisplayName("长度：km -> m")
        void length() {
            ObjectNode r = unit("value", 1, "from", "km", "to", "m");
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("category").asText()).isEqualTo("length");
            assertThat(r.path("input").asText()).isEqualTo("1.0 km");
            assertThat(result(r)).isEqualTo(1000L);
            assertThat(resultDouble(r)).isEqualTo(1000d);
            assertThat(r.path("from").asText()).isEqualTo("km");
            assertThat(r.path("to").asText()).isEqualTo("m");
        }

        @Test
        @DisplayName("重量：斤 -> 千克（中文单位）")
        void weightChinese() {
            ObjectNode r = unit("value", 10, "from", "斤", "to", "千克");
            assertThat(r.path("category").asText()).isEqualTo("weight");
            assertThat(result(r)).hasToString("5");
        }

        @Test
        @DisplayName("唯一命中类别时自动识别；显式 category 走同类倍率")
        void categoryResolution() {
            assertThat(result(unit("value", 1, "from", "mi", "to", "km"))).hasToString("1.609344");
            assertThat(result(unit("value", 1, "from", "gb", "to", "mb", "category", "storage")))
                    .hasToString("1024");
            // 指定 category 后只在该类别内查找，避免静默跨类
            assertThat(unit("value", 1, "from", "kb", "to", "mb", "category", "storage").path("category").asText())
                    .isEqualTo("storage");
        }

        @Test
        @DisplayName("中文类别别名映射到标准类别键")
        void categoryAlias() {
            assertThat(unit("value", 1, "from", "公里", "to", "米", "category", "长度").path("category").asText())
                    .isEqualTo("length");
            assertThat(unit("value", 1, "from", "kbps", "to", "mbps", "category", "网速").path("category").asText())
                    .isEqualTo("data_rate");
            assertThat(unit("value", 1, "from", "亩", "to", "平方米", "category", "面积").path("category").asText())
                    .isEqualTo("area");
        }

        @Test
        @DisplayName("保留斜杠写法：km/h -> m/s")
        void slashUnits() {
            ObjectNode r = unit("value", 36, "from", "km/h", "to", "m/s");
            assertThat(r.path("category").asText()).isEqualTo("speed");
            assertThat(result(r)).hasToString("10");
        }

        @Test
        @DisplayName("单位大小写/空格/点号归一化")
        void unitNormalization() {
            assertThat(result(unit("value", 1, "from", " KM ", "to", "m"))).isEqualTo(1000L);
            assertThat(result(unit("value", 1, "from", "k.m", "to", "m"))).isEqualTo(1000L);
        }

        @Test
        @DisplayName("面积与体积换算")
        void areaAndVolume() {
            assertThat(result(unit("value", 1, "from", "ha", "to", "sqm"))).hasToString("10000");
            assertThat(result(unit("value", 1, "from", "l", "to", "ml"))).hasToString("1000");
            assertThat(result(unit("value", 1, "from", "加仑", "to", "升"))).hasToString("3.785411784");
        }

        @Test
        @DisplayName("时间与存储换算")
        void timeAndStorage() {
            assertThat(result(unit("value", 2, "from", "h", "to", "min"))).hasToString("120");
            assertThat(result(unit("value", 1, "from", "tb", "to", "gb"))).hasToString("1024");
        }

        @ParameterizedTest(name = "{0} {1} -> {2} = {3}")
        @CsvSource({
                "100, c, f, 212",
                "0, c, k, 273.15",
                "212, f, c, 100",
                "0, K, ℃, -273.15"
        })
        void 温度换算(double value, String from, String to, String expected) {
            ObjectNode r = unit("value", value, "from", from, "to", to);
            assertThat(r.path("category").asText()).isEqualTo("temperature");
            assertThat(result(r)).hasToString(expected);
            assertThat(r.has("celsius")).isTrue();
        }

        @Test
        @DisplayName("温度英文别名归一化为 c/k/f")
        void temperatureAliases() {
            assertThat(result(unit("value", 100, "from", "celsius", "to", "Fahrenheit"))).hasToString("212");
            assertThat(result(unit("value", 100, "from", "degC", "to", "degF"))).hasToString("212");
            assertThat(unit("value", 100, "from", "Kelvin", "to", "摄氏度").path("category").asText())
                    .isEqualTo("temperature");
        }

        @Test
        @DisplayName("温度换算返回 celsius 中间值")
        void temperatureCelsiusField() {
            ObjectNode r = unit("value", 32, "from", "f", "to", "c");
            assertThat(r.path("celsius").asText()).hasToString("0");
            assertThat(result(r)).hasToString("0");
        }

        @Test
        @DisplayName("value 缺失")
        void valueMissing() {
            assertThat(unit("from", "km", "to", "m").path("error").asText()).isEqualTo("参数 value 不能为空");
        }

        @Test
        @DisplayName("value 非数字")
        void valueNotNumber() {
            assertThat(unit("value", "abc", "from", "km", "to", "m").path("error").asText())
                    .isEqualTo("value 必须是数字: abc");
        }

        @Test
        @DisplayName("from/to 为空")
        void fromOrToEmpty() {
            assertThat(unit("value", 1, "from", "km").path("error").asText()).isEqualTo("from 和 to 不能为空");
            assertThat(unit("value", 1, "to", "m").path("error").asText()).isEqualTo("from 和 to 不能为空");
        }

        @Test
        @DisplayName("未知类别")
        void unknownCategory() {
            String error = unit("value", 1, "from", "km", "to", "m", "category", "volume2").path("error").asText();
            assertThat(error).startsWith("未知类别: volume2。支持的类别: length / weight");
            assertThat(error).endsWith(" / temperature");
        }

        @Test
        @DisplayName("无法识别的源单位")
        void unknownSourceUnit() {
            assertThat(unit("value", 1, "from", "zzz", "to", "m").path("error").asText())
                    .startsWith("无法识别的源单位: zzz");
        }

        @Test
        @DisplayName("指定类别下源单位无法识别")
        void unknownSourceUnitWithCategory() {
            assertThat(unit("value", 1, "from", "zzz", "to", "m", "category", "length").path("error").asText())
                    .isEqualTo("无法识别的源单位: zzz（类别 length）");
        }

        @Test
        @DisplayName("目标单位不存在或跨类别")
        void unknownTargetUnit() {
            assertThat(unit("value", 1, "from", "km", "to", "zzz").path("error").asText())
                    .startsWith("无法识别目标单位: zzz，或与源单位不同类（km 属于 length）");
        }

        @Test
        @DisplayName("跨类别单位（公里 vs 分钟）无法换算")
        void crossCategoryUnits() {
            assertThat(unit("value", 1, "from", "km", "to", "min").path("error").asText())
                    .startsWith("无法识别目标单位: min，或与源单位不同类");
        }

        @Test
        @DisplayName("未知单位提示支持的类别清单")
        void unknownUnitHint() {
            assertThat(unit("value", 1, "from", "km", "to", "min").path("error").asText())
                    .doesNotContain("请通过 category 指定")
                    .contains("。支持的类别: length / weight");
            assertThat(unit("value", 1, "from", "zzz", "to", "m").path("error").asText())
                    .contains("以及温度 c / k / f");
        }

        @Test
        @DisplayName("precision 影响换算结果位数")
        void precisionApplied() {
            assertThat(result(unit("value", 1, "from", "mi", "to", "km", "precision", 2)))
                    .hasToString("1.61");
        }
    }
}
