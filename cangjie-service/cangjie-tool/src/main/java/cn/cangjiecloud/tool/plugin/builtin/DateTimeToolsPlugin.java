package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.core.plugin.PluginContext;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日期时间插件（纯 JDK 实现，无外网依赖）。
 * <p>
 * 一个插件承载多个工具，按 metadata.toolId 分发：
 * <ul>
 *     <li>builtin_now               —— 获取当前日期时间（含时区、星期、季度、时间戳）</li>
 *     <li>builtin_timestamp_convert —— 时间戳 ⇄ 日期时间互转</li>
 *     <li>builtin_date_diff         —— 两个日期的差值（天/周/月/年 + 工作日）</li>
 * </ul>
 * 对应 tool 表记录的 implementation 配置为本类全限定名，toolType=PLUGIN。
 * <p>
 * 大模型不掌握准确的系统时间，且极易在跨月/跨年的日期计算上出错，本插件用于兜底。
 */
@Component
public class DateTimeToolsPlugin extends AbstractBuiltinPlugin {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final List<String> WEEKDAY_CN =
            List.of("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日");
    /** 未显式指定时区的常见日期写法，按顺序尝试 */
    private static final List<String> COMMON_PATTERNS = List.of(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy/MM/dd",
            "yyyy年MM月dd日 HH:mm:ss", "yyyy年MM月dd日", "yyyy-MM", "yyyy.MM.dd", "yyyyMMdd", "yyyyMMddHHmmss");
    /** 逐日统计工作日的跨度上限（约一千年），超出则跳过该统计 */
    private static final long MAX_WORKDAY_SPAN_DAYS = 400_000L;

    @Override
    public String getName() {
        return "builtin_datetime_tools";
    }

    @Override
    public String getDescription() {
        return "内置日期时间工具集合：当前时间、时间戳互转、日期差值计算";
    }

    @Override
    public String getType() {
        return "tool";
    }

    @Override
    public Object execute(PluginContext context) {
        Map<String, Object> params = paramsOf(context);
        return switch (toolIdOf(context)) {
            case "builtin_now" -> now(params);
            case "builtin_timestamp_convert" -> timestampConvert(params);
            case "builtin_date_diff" -> dateDiff(params);
            default -> error("未知的内置工具: " + toolIdOf(context));
        };
    }

    // ------------------------------------------------------------------
    // builtin_now
    // ------------------------------------------------------------------

    /** 当前时间：timezone 默认取服务器时区，format 自定义 datetime 输出格式 */
    private JSONObject now(Map<String, Object> params) {
        ZoneResult zone = resolveZone(str(params.get("timezone")));
        if (zone.error() != null) {
            return error(zone.error());
        }
        String pattern = orDefault(str(params.get("format")), DEFAULT_DATETIME_PATTERN);
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException | DateTimeException e) {
            return error("format 不是合法的日期格式: " + pattern + "（示例 yyyy-MM-dd HH:mm:ss）");
        }
        ZonedDateTime now = ZonedDateTime.now(zone.zoneId());
        Map<String, Object> data = describe(now);
        data.put("datetime", now.format(formatter));
        return ok(data);
    }

    // ------------------------------------------------------------------
    // builtin_timestamp_convert
    // ------------------------------------------------------------------

    /**
     * 时间戳互转：value 为纯数字时按时间戳解析（10 位秒 / 13 位毫秒），
     * 否则按日期字符串解析并输出对应时间戳。
     */
    private JSONObject timestampConvert(Map<String, Object> params) {
        String value = str(params.get("value"));
        if (value.isEmpty()) {
            return missing("value");
        }
        ZoneResult zone = resolveZone(str(params.get("timezone")));
        if (zone.error() != null) {
            return error(zone.error());
        }
        Instant instant;
        String inputType;
        if (value.matches("[+-]?\\d+")) {
            long number = Long.parseLong(value);
            int digits = String.valueOf(Math.abs(number)).length();
            if (digits > 13) {
                return error("无法识别的时间戳位数: " + digits + " 位（支持 10 位秒级或 13 位毫秒级）");
            }
            if (digits > 10) {
                instant = Instant.ofEpochMilli(number);
                inputType = "timestamp_millis";
            } else {
                instant = Instant.ofEpochSecond(number);
                inputType = "timestamp_seconds";
            }
        } else {
            ParseResult parsed = parseDateTime(value, str(params.get("format")));
            if (parsed.error() != null) {
                return error(parsed.error());
            }
            instant = parsed.toInstant(zone.zoneId());
            inputType = "datetime";
        }
        ZonedDateTime zdt = instant.atZone(zone.zoneId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("input", value);
        data.put("inputType", inputType);
        data.put("timezone", zdt.getZone().getId());
        data.put("timestampMillis", instant.toEpochMilli());
        data.put("timestampSeconds", instant.getEpochSecond());
        data.putAll(describe(zdt));
        return ok(data);
    }

    // ------------------------------------------------------------------
    // builtin_date_diff
    // ------------------------------------------------------------------

    /**
     * 日期差值：start / end 支持 yyyy-MM-dd、yyyy-MM-dd HH:mm:ss、ISO（含时区偏移）、
     * 时间戳混用，返回总天数、年/月分解以及区间内工作日数量。
     */
    private JSONObject dateDiff(Map<String, Object> params) {
        String startText = str(params.get("start"));
        String endText = str(params.get("end"));
        if (startText.isEmpty()) {
            return missing("start");
        }
        if (endText.isEmpty()) {
            return missing("end");
        }
        ZoneResult zone = resolveZone(str(params.get("timezone")));
        if (zone.error() != null) {
            return error(zone.error());
        }
        String pattern = str(params.get("format"));
        ParseResult start = resolvePoint(startText, pattern);
        if (start.error() != null) {
            return error("起始日期无法解析：" + start.error());
        }
        ParseResult end = resolvePoint(endText, pattern);
        if (end.error() != null) {
            return error("结束日期无法解析：" + end.error());
        }

        ZonedDateTime from = start.toInstant(zone.zoneId()).atZone(zone.zoneId());
        ZonedDateTime to = end.toInstant(zone.zoneId()).atZone(zone.zoneId());
        LocalDateTime fromLocal = from.toLocalDateTime();
        LocalDateTime toLocal = to.toLocalDateTime();
        LocalDate fromDate = fromLocal.toLocalDate();
        LocalDate toDate = toLocal.toLocalDate();

        long totalDays = ChronoUnit.DAYS.between(fromLocal, toLocal);
        long totalSeconds = ChronoUnit.SECONDS.between(fromLocal, toLocal);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("start", fromLocal.format(DATETIME_FMT));
        data.put("startWeekday", WEEKDAY_CN.get(fromDate.getDayOfWeek().getValue() - 1));
        data.put("end", toLocal.format(DATETIME_FMT));
        data.put("endWeekday", WEEKDAY_CN.get(toDate.getDayOfWeek().getValue() - 1));
        data.put("direction", totalSeconds > 0 ? "forward" : totalSeconds < 0 ? "backward" : "same");
        data.put("totalDays", totalDays);
        data.put("totalWeeks", ChronoUnit.WEEKS.between(fromLocal, toLocal));
        data.put("totalHours", ChronoUnit.HOURS.between(fromLocal, toLocal));
        data.put("totalMinutes", ChronoUnit.MINUTES.between(fromLocal, toLocal));
        data.put("totalSeconds", totalSeconds);
        data.put("years", ChronoUnit.YEARS.between(fromLocal, toLocal));
        data.put("months", ChronoUnit.MONTHS.between(fromLocal, toLocal));
        Long workdays = countWorkdays(fromDate, toDate);
        if (workdays != null) {
            data.put("workdays", workdays);
        }
        data.put("summary", buildDiffSummary(totalDays));
        return ok(data);
    }

    /** 工作日数量（周一至周五）：不含起始日、含结束日；结束日早于起始日时返回负数；跨度过大返回 null */
    private Long countWorkdays(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end);
        if (Math.abs(days) > MAX_WORKDAY_SPAN_DAYS) {
            return null;
        }
        long count = 0;
        if (days >= 0) {
            for (LocalDate d = start.plusDays(1); !d.isAfter(end); d = d.plusDays(1)) {
                if (d.getDayOfWeek().getValue() <= 5) {
                    count++;
                }
            }
            return count;
        }
        for (LocalDate d = end.plusDays(1); !d.isAfter(start); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                count++;
            }
        }
        return -count;
    }

    /** 按 365/30 天粗估年月，精确值仍以 totalDays 为准 */
    private String buildDiffSummary(long totalDays) {
        if (totalDays == 0) {
            return "同一天";
        }
        long abs = Math.abs(totalDays);
        long years = abs / 365;
        long months = (abs % 365) / 30;
        long days = abs % 365 % 30;
        StringBuilder sb = new StringBuilder("相差 ");
        if (years > 0) {
            sb.append(years).append(" 年 ");
        }
        if (months > 0) {
            sb.append(months).append(" 个月 ");
        }
        if (days > 0 || (years == 0 && months == 0)) {
            sb.append(days).append(" 天");
        }
        return sb.toString().strip() + "（共 " + abs + " 天）";
    }

    // ------------------------------------------------------------------
    // 公共辅助
    // ------------------------------------------------------------------

    /** 把一个时间点展开成模型易读的完整描述 */
    private Map<String, Object> describe(ZonedDateTime zdt) {
        LocalDate date = zdt.toLocalDate();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("datetime", zdt.toLocalDateTime().format(DATETIME_FMT));
        data.put("date", date.format(DATE_FMT));
        data.put("time", zdt.toLocalTime().format(TIME_FMT));
        data.put("year", date.getYear());
        data.put("month", date.getMonthValue());
        data.put("dayOfMonth", date.getDayOfMonth());
        data.put("weekday", WEEKDAY_CN.get(date.getDayOfWeek().getValue() - 1));
        data.put("weekdayIndex", date.getDayOfWeek().getValue());
        data.put("weekOfYear", date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        data.put("quarter", "Q" + date.get(IsoFields.QUARTER_OF_YEAR));
        data.put("dayOfYear", date.getDayOfYear());
        data.put("daysInMonth", date.lengthOfMonth());
        data.put("iso8601", zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        data.put("utc", zdt.withZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
        data.put("zoneOffset", zdt.getOffset().getId());
        return data;
    }

    private static String orDefault(String value, String def) {
        return value == null || value.isEmpty() ? def : value;
    }

    private record ZoneResult(ZoneId zoneId, String error) {}

    private ZoneResult resolveZone(String timezone) {
        if (timezone == null || timezone.isEmpty()) {
            return new ZoneResult(ZoneId.systemDefault(), null);
        }
        try {
            return new ZoneResult(ZoneId.of(timezone), null);
        } catch (DateTimeException e) {
            return new ZoneResult(null, "无法识别的时区: " + timezone
                    + "（示例 Asia/Shanghai、UTC、America/New_York）");
        }
    }

    /**
     * 解析后的时间点。{@code offset} 非空表示输入自带时区信息（如 2026-01-01T00:00:00+08:00
     * 或 2026-01-01T00:00:00Z），此时必须用输入自带的偏移换算，不能被调用方时区覆盖；
     * 不带时区的"裸"日期时间则由调用方时区决定其绝对时刻。
     */
    private record ParseResult(LocalDateTime dateTime, ZoneOffset offset, Instant instant, String error) {

        static ParseResult ofLocal(LocalDateTime dateTime) {
            return new ParseResult(dateTime, null, null, null);
        }

        static ParseResult ofOffset(LocalDateTime dateTime, ZoneOffset offset) {
            return new ParseResult(dateTime, offset, null, null);
        }

        static ParseResult ofInstant(Instant instant) {
            return new ParseResult(null, null, instant, null);
        }

        static ParseResult fail(String message) {
            return new ParseResult(null, null, null, message);
        }

        boolean succeeded() {
            return error == null;
        }

        Instant toInstant(ZoneId fallback) {
            if (instant != null) {
                return instant;
            }
            if (offset != null) {
                return dateTime.toInstant(offset);
            }
            return dateTime.atZone(fallback).toInstant();
        }
    }

    /** 时间戳或日期字符串均可（供 date_diff 混用） */
    private ParseResult resolvePoint(String text, String pattern) {
        String trimmed = text.trim();
        if (trimmed.matches("[+-]?\\d{10}") || trimmed.matches("[+-]?\\d{13}")) {
            long number = Long.parseLong(trimmed);
            long digits = Math.abs(number);
            return ParseResult.ofInstant(digits < 100_000_000_000L
                    ? Instant.ofEpochSecond(number) : Instant.ofEpochMilli(number));
        }
        return parseDateTime(trimmed, pattern);
    }

    /**
     * 宽松解析日期时间字符串。pattern 非空时只按 pattern 解析；
     * 否则依次尝试 ISO（含时区/偏移）、本地日期时间与常见中文/斜杠/紧凑格式。
     */
    private ParseResult parseDateTime(String text, String pattern) {
        if (text == null || text.isEmpty()) {
            return ParseResult.fail("日期内容不能为空");
        }
        String trimmed = text.trim();
        if (pattern != null && !pattern.isEmpty()) {
            ParseResult result = tryPattern(trimmed, pattern);
            return result.succeeded() ? result
                    : ParseResult.fail("无法按格式 " + pattern + " 解析: " + text);
        }
        // ISO 带时区/偏移
        try {
            OffsetDateTime odt = OffsetDateTime.parse(trimmed.replace(' ', 'T'));
            return ParseResult.ofOffset(odt.toLocalDateTime(), odt.getOffset());
        } catch (DateTimeException ignored) {
            // 继续尝试
        }
        // ISO 本地日期时间
        try {
            return ParseResult.ofLocal(LocalDateTime.parse(trimmed.replace(' ', 'T')));
        } catch (DateTimeParseException ignored) {
            // 继续尝试
        }
        try {
            return ParseResult.ofLocal(LocalDate.parse(trimmed).atStartOfDay());
        } catch (DateTimeParseException ignored) {
            // 继续尝试
        }
        for (String candidate : COMMON_PATTERNS) {
            ParseResult result = tryPattern(trimmed, candidate);
            if (result.succeeded()) {
                return result;
            }
        }
        return ParseResult.fail("无法解析的日期: " + text
                + "（支持 yyyy-MM-dd、yyyy-MM-dd HH:mm:ss、时间戳，或用 format 指定格式）");
    }

    /** 按指定 pattern 解析，尽可能还原到 Instant / 日期时间 / 年月 / 年 */
    private ParseResult tryPattern(String text, String pattern) {
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            return ParseResult.fail("format 不是合法的日期格式: " + pattern);
        }
        TemporalAccessor parsed;
        try {
            parsed = formatter.parseBest(text,
                    Instant::from, ZonedDateTime::from, OffsetDateTime::from,
                    LocalDateTime::from, LocalDate::from, YearMonth::from, Year::from);
        } catch (DateTimeException e) {
            return ParseResult.fail("无法按格式 " + pattern + " 解析: " + text);
        }
        if (parsed instanceof Instant instant) {
            return ParseResult.ofInstant(instant);
        }
        if (parsed instanceof OffsetDateTime odt) {
            return ParseResult.ofOffset(odt.toLocalDateTime(), odt.getOffset());
        }
        if (parsed instanceof ZonedDateTime zdt) {
            return ParseResult.ofOffset(zdt.toLocalDateTime(), zdt.getOffset());
        }
        if (parsed instanceof LocalDateTime ldt) {
            return ParseResult.ofLocal(ldt);
        }
        if (parsed instanceof LocalDate ld) {
            return ParseResult.ofLocal(ld.atStartOfDay());
        }
        if (parsed instanceof YearMonth ym) {
            return ParseResult.ofLocal(ym.atDay(1).atStartOfDay());
        }
        if (parsed instanceof Year year) {
            return ParseResult.ofLocal(year.atMonth(1).atDay(1).atStartOfDay());
        }
        return ParseResult.fail("无法解析的日期: " + text);
    }
}
