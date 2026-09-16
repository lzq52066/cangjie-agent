package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginContext;
import com.alibaba.fastjson.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置插件公共基类 —— 统一结果封装与参数读取。
 * <p>
 * 所有内置插件都必须容忍大模型传来的"字符串化"参数（如 {@code "12"} 代替 {@code 12}、
 * {@code "true"} 代替 {@code true}），因此这里提供宽松解析的参数访问方法。
 */
public abstract class AbstractBuiltinPlugin implements Plugin {

    /** 成功结果：{"success": true, ...data} */
    protected static JSONObject ok(Map<String, Object> data) {
        JSONObject jo = new JSONObject(new LinkedHashMap<>());
        jo.put("success", true);
        if (data != null) {
            jo.putAll(data);
        }
        return jo;
    }

    /** 失败结果：{"success": false, "error": message} */
    protected static JSONObject error(String message) {
        JSONObject jo = new JSONObject(new LinkedHashMap<>());
        jo.put("success", false);
        jo.put("error", message);
        return jo;
    }

    /** 必填参数缺失 */
    protected static JSONObject missing(String name) {
        return error("参数 " + name + " 不能为空");
    }

    /** 取调用参数（永不为 null） */
    protected static Map<String, Object> paramsOf(PluginContext context) {
        Map<String, Object> params = context == null ? null : context.getParams();
        return params != null ? params : Map.of();
    }

    /** 按 metadata.toolId 分发到具体内置工具 */
    protected static String toolIdOf(PluginContext context) {
        Map<String, Object> metadata = context == null ? null : context.getMetadata();
        Object toolId = metadata == null ? null : metadata.get("toolId");
        return toolId == null ? "" : toolId.toString();
    }

    /** 字符串参数（去首尾空格） */
    protected static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    /** 字符串参数（原样保留，用于待处理的正文内容） */
    protected static String raw(Object o) {
        return o == null ? "" : o.toString();
    }

    protected static int intVal(Object o, int def) {
        if (o == null) {
            return def;
        }
        try {
            return (int) Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    protected static double doubleVal(Object o, double def) {
        if (o == null) {
            return def;
        }
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 布尔参数：兼容 true / TRUE / "1" / yes；未传时返回默认值 */
    protected static boolean boolVal(Object o, boolean def) {
        if (o == null) {
            return def;
        }
        String s = o.toString().trim().toLowerCase();
        if (s.isEmpty()) {
            return def;
        }
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "y".equals(s);
    }

    protected static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 数值输出：按 precision 四舍五入后去掉无意义尾随零，
     * 避免大模型读到 12.500000000000001 / 3.0 这类噪声，也不输出科学计数法。
     */
    protected static Object number(double value, int precision) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return decimal(BigDecimal.valueOf(value), precision);
    }

    /** {@link #number(double, int)} 的 BigDecimal 版本，保留高精度换算结果 */
    protected static Object decimal(BigDecimal value, int precision) {
        if (value == null) {
            return null;
        }
        BigDecimal decimal = value.setScale(clamp(precision, 0, 12), RoundingMode.HALF_UP);
        if (decimal.scale() > 0) {
            decimal = decimal.stripTrailingZeros();
        }
        if (decimal.scale() <= 0) {
            return decimal.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                    ? decimal.toPlainString()
                    : decimal.longValueExact();
        }
        return decimal.toPlainString();
    }

    /** 默认按 10 位小数收敛 */
    protected static Object number(double value) {
        return number(value, 10);
    }
}
