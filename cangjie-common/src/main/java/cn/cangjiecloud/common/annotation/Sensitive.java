package cn.cangjiecloud.common.annotation;

import java.lang.annotation.*;

/**
 * 标记敏感字段，操作日志切面序列化时会自动过滤该字段。
 * <p>
 * 适用于 DTO、VO、Entity 等需要记录操作日志但包含敏感信息的类。
 * 用法：{@code @Sensitive private String password;}
 * </p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sensitive {
}