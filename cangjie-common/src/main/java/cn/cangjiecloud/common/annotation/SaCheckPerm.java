package cn.cangjiecloud.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SaCheckPerm {
    String[] value() default {};
    Mode mode() default Mode.AND;

    enum Mode { AND, OR }
}
