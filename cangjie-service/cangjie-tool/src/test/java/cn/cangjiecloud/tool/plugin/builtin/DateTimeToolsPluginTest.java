package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.core.plugin.PluginContext;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置日期时间插件测试：当前时间、时间戳互转、日期差值。
 * <p>
 * {@code builtin_now} 内部使用 {@code ZonedDateTime.now(zone)}，无时钟注入点，
 * 因此对时间敏感的断言采用格式/正则等宽松方式；固定日期入参则做精确断言。
 */
@DisplayName("内置插件 - DateTimeToolsPlugin")
class DateTimeToolsPluginTest {

    private static final DateTimeToolsPlugin PLUGIN = new DateTimeToolsPlugin();

    private static final String NOW = "builtin_now";
    private static final String TS = "builtin_timestamp_convert";
    private static final String DIFF = "builtin_date_diff";

    private static ObjectNode run(String toolId, Map<String, Object> params) {
        return (ObjectNode) PLUGIN.execute(PluginContext.builder()
                .params(params)
                .metadata(Map.of("toolId", toolId))
                .build());
    }

    private static ObjectNode call(String toolId, Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return run(toolId, m);
    }

    // ------------------------------------------------------------------
    // 元信息 / 分发
    // ------------------------------------------------------------------

    @Test
    @DisplayName("插件元信息")
    void metadata() {
        assertThat(PLUGIN.getName()).isEqualTo("builtin_datetime_tools");
        assertThat(PLUGIN.getDescription()).isEqualTo("内置日期时间工具集合：当前时间、时间戳互转、日期差值计算");
        assertThat(PLUGIN.getType()).isEqualTo("tool");
    }

    @Test
    @DisplayName("未知 toolId 报错")
    void unknownToolId() {
        ObjectNode r = call("builtin_unknown");
        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").asText()).isEqualTo("未知的内置工具: builtin_unknown");
    }

    @Test
    @DisplayName("context 为 null 时不抛异常")
    void nullContext() {
        assertThat(((ObjectNode) PLUGIN.execute(null)).path("success").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------
    // builtin_now
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("当前时间 builtin_now")
    class Now {

        @Test
        @DisplayName("默认 UTC 时区返回完整时间描述字段")
        void defaultUtc() {
            ObjectNode r = call(NOW, "timezone", "UTC");
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("datetime").asText()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
            assertThat(r.path("date").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(r.path("time").asText()).matches("\\d{2}:\\d{2}:\\d{2}");
            assertThat(r.path("iso8601").asText()).endsWith("Z");
            assertThat(r.path("utc").asText()).endsWith("Z");
            assertThat(r.path("zoneOffset").asText()).isEqualTo("Z");
            assertThat(r.path("quarter").asText()).matches("Q[1-4]");
            assertThat(r.path("weekday").asText()).startsWith("星期");
            assertThat(r.path("weekdayIndex").asInt()).isBetween(1, 7);
            assertThat(r.path("weekOfYear").asInt()).isBetween(1, 53);
            assertThat(r.path("dayOfYear").asInt()).isBetween(1, 366);
            assertThat(r.path("daysInMonth").asInt()).isBetween(28, 31);
            assertThat(r.path("month").asInt()).isBetween(1, 12);
            assertThat(r.path("year").asInt()).isGreaterThan(2020);
            assertThat(r.path("dayOfMonth").asInt()).isBetween(1, 31);
        }

        @Test
        @DisplayName("指定 Asia/Shanghai 时区后偏移为 +08:00")
        void shanghaiZone() {
            ObjectNode r = call(NOW, "timezone", "Asia/Shanghai");
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("zoneOffset").asText()).isEqualTo("+08:00");
            assertThat(r.path("iso8601").asText()).endsWith("+08:00");
        }

        @Test
        @DisplayName("自定义 format 覆盖 datetime 字段")
        void customFormat() {
            assertThat(call(NOW, "timezone", "UTC", "format", "yyyy").path("datetime").asText())
                    .matches("\\d{4}");
            assertThat(call(NOW, "timezone", "UTC", "format", "yyyyMMdd").path("datetime").asText())
                    .matches("\\d{8}");
        }

        @Test
        @DisplayName("空 format 回退默认格式")
        void blankFormatFallsBack() {
            assertThat(call(NOW, "timezone", "UTC", "format", "  ").path("datetime").asText())
                    .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        }

        @Test
        @DisplayName("非法时区返回明确提示")
        void invalidTimezone() {
            assertThat(call(NOW, "timezone", "Not/AZone").path("error").asText())
                    .isEqualTo("无法识别的时区: Not/AZone（示例 Asia/Shanghai、UTC、America/New_York）");
        }

        @Test
        @DisplayName("非法 format 返回明确提示")
        void invalidFormat() {
            assertThat(call(NOW, "timezone", "UTC", "format", "yyyy-MM-dd bbb").path("error").asText())
                    .isEqualTo("format 不是合法的日期格式: yyyy-MM-dd bbb（示例 yyyy-MM-dd HH:mm:ss）");
        }
    }

    // ------------------------------------------------------------------
    // builtin_timestamp_convert
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("时间戳互转 builtin_timestamp_convert")
    class TimestampConvert {

        @Test
        @DisplayName("10 位秒级时间戳")
        void secondsTimestamp() {
            ObjectNode r = call(TS, "value", "1767225600", "timezone", "UTC");
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("inputType").asText()).isEqualTo("timestamp_seconds");
            assertThat(r.path("timestampSeconds").asLong()).isEqualTo(1767225600L);
            assertThat(r.path("timestampMillis").asLong()).isEqualTo(1767225600000L);
            assertThat(r.path("datetime").asText()).isEqualTo("2026-01-01 00:00:00");
            assertThat(r.path("date").asText()).isEqualTo("2026-01-01");
            assertThat(r.path("weekday").asText()).isEqualTo("星期四");
            assertThat(r.path("quarter").asText()).isEqualTo("Q1");
            assertThat(r.path("weekdayIndex").asInt()).isEqualTo(4);
            assertThat(r.path("timezone").asText()).isEqualTo("UTC");
            assertThat(r.path("input").asText()).isEqualTo("1767225600");
        }

        @Test
        @DisplayName("13 位毫秒级时间戳")
        void millisTimestamp() {
            ObjectNode r = call(TS, "value", "1767225600123", "timezone", "UTC");
            assertThat(r.path("inputType").asText()).isEqualTo("timestamp_millis");
            assertThat(r.path("timestampMillis").asLong()).isEqualTo(1767225600123L);
            assertThat(r.path("timestampSeconds").asLong()).isEqualTo(1767225600L);
        }

        @Test
        @DisplayName("负数时间戳（1970 年前）")
        void negativeTimestamp() {
            ObjectNode r = call(TS, "value", "-1000000000", "timezone", "UTC");
            assertThat(r.path("inputType").asText()).isEqualTo("timestamp_seconds");
            assertThat(r.path("date").asText()).isEqualTo("1938-04-24");
        }

        @Test
        @DisplayName("日期字符串转时间戳")
        void dateStringToTimestamp() {
            ObjectNode r = call(TS, "value", "2026-01-01", "timezone", "UTC");
            assertThat(r.path("inputType").asText()).isEqualTo("datetime");
            assertThat(r.path("timestampSeconds").asLong()).isEqualTo(1767225600L);
            assertThat(r.path("timestampMillis").asLong()).isEqualTo(1767225600000L);
        }

        @Test
        @DisplayName("带时区偏移的输入不被调用方时区覆盖")
        void offsetBearingInputKeepsOwnZone() {
            ObjectNode r = call(TS, "value", "2026-01-01T00:00:00+08:00", "timezone", "UTC");
            assertThat(r.path("inputType").asText()).isEqualTo("datetime");
            // 北京时间 0 点 = UTC 前一日 16 点
            assertThat(r.path("timestampSeconds").asLong()).isEqualTo(1767196800L);
            assertThat(r.path("date").asText()).isEqualTo("2025-12-31");
        }

        @Test
        @DisplayName("Z 结尾的 ISO 输入")
        void isoWithZ() {
            assertThat(call(TS, "value", "2026-01-01T00:00:00Z").path("timestampSeconds").asLong())
                    .isEqualTo(1767225600L);
        }

        @Test
        @DisplayName("空格分隔的 ISO 本地日期时间")
        void spaceSeparatedIso() {
            assertThat(call(TS, "value", "2026-01-01 12:30:45", "timezone", "UTC")
                    .path("datetime").asText()).isEqualTo("2026-01-01 12:30:45");
        }

        @Test
        @DisplayName("常见中文/斜杠/紧凑格式")
        void commonPatterns() {
            long expected = 1767225600L;
            assertThat(call(TS, "value", "2026年01月01日", "timezone", "UTC")
                    .path("timestampSeconds").asLong()).isEqualTo(expected);
            assertThat(call(TS, "value", "2026/01/01", "timezone", "UTC")
                    .path("timestampSeconds").asLong()).isEqualTo(expected);
            assertThat(call(TS, "value", "20260101", "timezone", "UTC")
                    .path("timestampSeconds").asLong()).isEqualTo(expected);
            assertThat(call(TS, "value", "2026-01", "timezone", "UTC")
                    .path("timestampSeconds").asLong()).isEqualTo(expected);
        }

        @Test
        @DisplayName("指定 format 解析非标准写法")
        void explicitFormat() {
            ObjectNode r = call(TS, "value", "01/02/2026", "format", "dd/MM/yyyy", "timezone", "UTC");
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("date").asText()).isEqualTo("2026-02-01");
        }

        @Test
        @DisplayName("指定 format 无法解析时给出带格式名的错误")
        void explicitFormatMismatch() {
            assertThat(call(TS, "value", "2026-01-01", "format", "dd/MM/yyyy").path("error").asText())
                    .isEqualTo("无法按格式 dd/MM/yyyy 解析: 2026-01-01");
        }

        @Test
        @DisplayName("时间戳位数不支持")
        void unsupportedDigitLength() {
            assertThat(call(TS, "value", "12345678901234").path("error").asText())
                    .isEqualTo("无法识别的时间戳位数: 14 位（支持 10 位秒级或 13 位毫秒级）");
        }

        @Test
        @DisplayName("value 必填")
        void valueRequired() {
            assertThat(call(TS).path("error").asText()).isEqualTo("参数 value 不能为空");
        }

        @Test
        @DisplayName("无法解析的日期文本")
        void unparseableText() {
            assertThat(call(TS, "value", "昨天下午").path("error").asText())
                    .startsWith("无法解析的日期: 昨天下午（支持 yyyy-MM-dd");
        }

        @Test
        @DisplayName("非法时区优先报错")
        void invalidTimezone() {
            assertThat(call(TS, "value", "1767225600", "timezone", "Mars/Olympus").path("error").asText())
                    .startsWith("无法识别的时区: Mars/Olympus");
        }
    }

    // ------------------------------------------------------------------
    // builtin_date_diff
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("日期差值 builtin_date_diff")
    class DateDiff {

        @Test
        @DisplayName("正向一周：天/周/小时/工作日与摘要")
        void forwardWeek() {
            ObjectNode r = call(DIFF, "start", "2026-01-01", "end", "2026-01-08", "timezone", "UTC");
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("direction").asText()).isEqualTo("forward");
            assertThat(r.path("totalDays").asLong()).isEqualTo(7L);
            assertThat(r.path("totalWeeks").asLong()).isEqualTo(1L);
            assertThat(r.path("totalHours").asLong()).isEqualTo(168L);
            assertThat(r.path("totalMinutes").asLong()).isEqualTo(10080L);
            assertThat(r.path("totalSeconds").asLong()).isEqualTo(604800L);
            assertThat(r.path("workdays").asLong()).isEqualTo(5L);
            assertThat(r.path("start").asText()).isEqualTo("2026-01-01 00:00:00");
            assertThat(r.path("end").asText()).isEqualTo("2026-01-08 00:00:00");
            assertThat(r.path("startWeekday").asText()).isEqualTo("星期四");
            assertThat(r.path("endWeekday").asText()).isEqualTo("星期四");
            assertThat(r.path("summary").asText()).isEqualTo("相差 7 天（共 7 天）");
        }

        @Test
        @DisplayName("反向一周：direction=backward 且工作日为负")
        void backwardWeek() {
            ObjectNode r = call(DIFF, "start", "2026-01-08", "end", "2026-01-01", "timezone", "UTC");
            assertThat(r.path("direction").asText()).isEqualTo("backward");
            assertThat(r.path("totalDays").asLong()).isEqualTo(-7L);
            assertThat(r.path("workdays").asLong()).isEqualTo(-5L);
            assertThat(r.path("summary").asText()).isEqualTo("相差 7 天（共 7 天）");
        }

        @Test
        @DisplayName("同一天：direction=same，摘要为同一天")
        void sameDay() {
            ObjectNode r = call(DIFF, "start", "2026-01-01", "end", "2026-01-01", "timezone", "UTC");
            assertThat(r.path("direction").asText()).isEqualTo("same");
            assertThat(r.path("totalDays").asLong()).isZero();
            assertThat(r.path("workdays").asLong()).isZero();
            assertThat(r.path("summary").asText()).isEqualTo("同一天");
        }

        @Test
        @DisplayName("跨年月分解：years/months 按 ChronoUnit 精确计算")
        void yearsAndMonths() {
            ObjectNode r = call(DIFF, "start", "2026-01-01", "end", "2027-03-01", "timezone", "UTC");
            assertThat(r.path("totalDays").asLong()).isEqualTo(424L);
            assertThat(r.path("years").asLong()).isEqualTo(1L);
            assertThat(r.path("months").asLong()).isEqualTo(14L);
            // 摘要按 365/30 粗算：424 → 1 年 + 59 天 → 1 个月 + 29 天
            assertThat(r.path("summary").asText()).isEqualTo("相差 1 年 1 个月 29 天（共 424 天）");
        }

        @Test
        @DisplayName("恰好整年/整月时摘要不追加天")
        void summaryWithoutDayPart() {
            // 400 天：400/365=1 年，余 35/30=1 个月，35%30=5 天
            assertThat(call(DIFF, "start", "2026-01-01", "end", "2027-02-05", "timezone", "UTC")
                    .path("summary").asText()).isEqualTo("相差 1 年 1 个月 5 天（共 400 天）");
            // 30 天整：只有月，无天
            assertThat(call(DIFF, "start", "2026-01-01", "end", "2026-01-31", "timezone", "UTC")
                    .path("summary").asText()).isEqualTo("相差 1 个月（共 30 天）");
            // 365 天整：只有年
            assertThat(call(DIFF, "start", "2026-01-01", "end", "2027-01-01", "timezone", "UTC")
                    .path("summary").asText()).isEqualTo("相差 1 年（共 365 天）");
        }

        @Test
        @DisplayName("起止可混用时间戳与日期串")
        void mixedTimestampAndDate() {
            ObjectNode r = call(DIFF, "start", "1767225600", "end", "2026-01-02", "timezone", "UTC");
            assertThat(r.path("totalDays").asLong()).isEqualTo(1L);
            assertThat(r.path("direction").asText()).isEqualTo("forward");
        }

        @Test
        @DisplayName("13 位毫秒时间戳混用")
        void millisTimestampPoint() {
            ObjectNode r = call(DIFF, "start", "1767225600000", "end", "2026-01-03", "timezone", "UTC");
            assertThat(r.path("totalDays").asLong()).isEqualTo(2L);
        }

        @Test
        @DisplayName("带偏移的输入按自身偏移换算绝对时刻")
        void offsetAware() {
            ObjectNode r = call(DIFF, "start", "2026-01-01T00:00:00+08:00",
                    "end", "2026-01-01T00:00:00Z", "timezone", "UTC");
            assertThat(r.path("totalHours").asLong()).isEqualTo(8L);
            assertThat(r.path("totalDays").asLong()).isZero();
            assertThat(r.path("summary").asText()).isEqualTo("同一天");
        }

        @Test
        @DisplayName("指定 format 解析两端")
        void explicitFormat() {
            ObjectNode r = call(DIFF, "start", "01/02/2026", "end", "03/02/2026",
                    "format", "dd/MM/yyyy", "timezone", "UTC");
            assertThat(r.path("totalDays").asLong()).isEqualTo(2L);
        }

        @Test
        @DisplayName("超大跨度跳过工作日统计")
        void hugeSpanSkipsWorkdayCount() {
            ObjectNode r = call(DIFF, "start", "0001-01-01", "end", "2026-01-01", "timezone", "UTC");
            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("totalDays").asLong()).isEqualTo(739616L);
            assertThat(r.has("workdays")).isFalse();
            // 739616 = 2026*365 + 126；126 = 4*30 + 6
            assertThat(r.path("summary").asText()).isEqualTo("相差 2026 年 4 个月 6 天（共 739616 天）");
        }

        @Test
        @DisplayName("start 必填")
        void startRequired() {
            assertThat(call(DIFF, "end", "2026-01-01").path("error").asText())
                    .isEqualTo("参数 start 不能为空");
        }

        @Test
        @DisplayName("end 必填")
        void endRequired() {
            assertThat(call(DIFF, "start", "2026-01-01").path("error").asText())
                    .isEqualTo("参数 end 不能为空");
        }

        @Test
        @DisplayName("起始日期无法解析")
        void unparsableStart() {
            assertThat(call(DIFF, "start", "zzz", "end", "2026-01-01").path("error").asText())
                    .startsWith("起始日期无法解析：无法解析的日期: zzz");
        }

        @Test
        @DisplayName("结束日期无法解析")
        void unparsableEnd() {
            assertThat(call(DIFF, "start", "2026-01-01", "end", "zzz").path("error").asText())
                    .startsWith("结束日期无法解析：无法解析的日期: zzz");
        }

        @Test
        @DisplayName("非法时区优先报错")
        void invalidTimezone() {
            assertThat(call(DIFF, "start", "2026-01-01", "end", "2026-01-02", "timezone", "No/Where")
                    .path("error").asText()).startsWith("无法识别的时区: No/Where");
        }
    }
}
