package cn.cangjiecloud.observability.aspect;

import cn.cangjiecloud.common.annotation.Sensitive;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.observability.entity.OperationLogEntity;
import cn.cangjiecloud.observability.service.IOperationLogService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.ValueFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 操作日志切面：自动采集所有 Controller 的写操作（POST/PUT/DELETE/PATCH），
 * 记录模块、操作、参数、结果、耗时、用户等，写入 operation_log 表。
 * <p>
 * 敏感字段通过 {@link Sensitive} 注解标记，序列化时值替换为 ******。
 * </p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    /** 参数/结果序列化最大长度，防止大字段拖慢写入 */
    private static final int MAX_TEXT_LENGTH = 2000;

    /** 敏感字段值替换掩码 */
    private static final String MASK = "******";

    /** 缓存各 Class 中标注了 @Sensitive 的字段名，避免重复反射 */
    private static final Map<Class<?>, Set<String>> SENSITIVE_FIELD_CACHE = new ConcurrentHashMap<>();

    private final IOperationLogService operationLogService;

    @Around("execution(public * cn.cangjiecloud..controller..*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs == null ? null : attrs.getRequest();
        if (request == null || !isWriteMethod(request.getMethod())) {
            return pjp.proceed();
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        OperationLogEntity entity = new OperationLogEntity();
        entity.setModule(resolveModule(signature.getDeclaringType()));
        entity.setAction(signature.getMethod().getName());
        entity.setMethod(request.getMethod());
        entity.setUri(request.getRequestURI());
        entity.setParams(serializeArgs(pjp.getArgs()));
        entity.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        entity.setIp(resolveIp(request));
        fillUser(entity);

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            entity.setDuration(System.currentTimeMillis() - start);
            entity.setStatus("success");
            entity.setResult(serializeResult(result));
            record(entity);
            return result;
        } catch (Throwable e) {
            entity.setDuration(System.currentTimeMillis() - start);
            entity.setStatus("fail");
            entity.setErrorMessage(truncate(e.getMessage()));
            record(entity);
            throw e;
        }
    }

    private void record(OperationLogEntity entity) {
        try {
            operationLogService.record(entity);
        } catch (Exception e) {
            log.warn("操作日志写入失败: {} {} - {}", entity.getMethod(), entity.getUri(), e.getMessage());
        }
    }

    private boolean isWriteMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "DELETE".equals(method) || "PATCH".equals(method);
    }

    /**
     * 从 Controller 类名推导模块名：KnowledgeDocumentController → knowledge.document
     */
    private String resolveModule(Class<?> clazz) {
        String simple = clazz.getSimpleName();
        if (simple.endsWith("Controller")) {
            simple = simple.substring(0, simple.length() - "Controller".length());
        }
        StringBuilder sb = new StringBuilder();
        for (char c : simple.toCharArray()) {
            if (Character.isUpperCase(c) && sb.length() > 0) {
                sb.append('.');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            String json = Arrays.stream(args)
                    .filter(a -> !(a instanceof HttpServletRequest || a instanceof HttpServletResponse
                            || a instanceof MultipartFile))
                    .map(a -> truncate(JSON.toJSONString(a, buildFilter(a.getClass()))))
                    .collect(Collectors.joining(","));
            return truncate(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeResult(Object result) {
        if (result == null) {
            return null;
        }
        try {
            return truncate(JSON.toJSONString(result, buildFilter(result.getClass())));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 为指定 Class 构建 ValueFilter：标注了 @Sensitive 的字段值替换为 ******。
     */
    private ValueFilter buildFilter(Class<?> clazz) {
        Set<String> fields = SENSITIVE_FIELD_CACHE.computeIfAbsent(clazz, this::resolveSensitiveFields);
        if (fields.isEmpty()) {
            return (object, name, value) -> value;
        }
        return (object, name, value) -> fields.contains(name) ? MASK : value;
    }

    /**
     * 反射扫描类中标注了 @Sensitive 的字段名。
     */
    private Set<String> resolveSensitiveFields(Class<?> clazz) {
        String[] names = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Sensitive.class))
                .map(Field::getName)
                .toArray(String[]::new);
        return names.length == 0 ? Collections.emptySet()
                : Collections.unmodifiableSet(Arrays.stream(names).collect(Collectors.toSet()));
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void fillUser(OperationLogEntity entity) {
        try {
            entity.setUserId(UserContext.getUserId());
            UserIdentity identity = UserContext.getIdentity();
            if (identity != null) {
                entity.setUsername(identity.getUsername());
            }
        } catch (Exception e) {
            // 未登录或会话异常时忽略用户信息
        }
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH);
    }
}