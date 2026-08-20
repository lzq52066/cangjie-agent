package cn.cangjiecloud.tool.annotation;

import java.lang.annotation.*;

/**
 * 标记 AbsToolHandler 实现类对应的工具类型
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolHandlerType {
    /** 工具类型：HTTP / CUSTOM / MCP / SKILL / PLUGIN */
    String value();
}