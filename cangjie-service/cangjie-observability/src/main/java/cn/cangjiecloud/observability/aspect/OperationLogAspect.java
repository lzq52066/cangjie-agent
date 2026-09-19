package cn.cangjiecloud.observability.aspect;

import cn.cangjiecloud.common.annotation.Sensitive;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.observability.context.TraceContext;
import cn.cangjiecloud.observability.entity.OperationLogEntity;
import cn.cangjiecloud.observability.service.IOperationLogService;
import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
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
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 操作日志切面：拦截所有 Controller 请求，打印请求地址、参数、耗时等信息。
 * 对于写操作（POST/PUT/DELETE/PATCH）同时持久化到 operation_log 表。
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

    /**
     * 带敏感字段掩码的 ObjectMapper：通过 BeanSerializerModifier 对每个 bean class
     * 即时解析其 @Sensitive 字段（含任意嵌套层级），序列化时统一输出 ******。
     */
    private static final ObjectMapper MASKED_MAPPER = JsonUtils.mapper().copy()
            .registerModule(new SimpleModule().setSerializerModifier(new BeanSerializerModifier() {
                @Override
                public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                                  BeanDescription beanDesc,
                                                                  List<BeanPropertyWriter> writers) {
                    Set<String> sensitiveFields =
                            SENSITIVE_FIELD_CACHE.computeIfAbsent(beanDesc.getBeanClass(),
                                    OperationLogAspect::resolveSensitiveFieldsStatic);
                    if (!sensitiveFields.isEmpty()) {
                        for (BeanPropertyWriter writer : writers) {
                            if (sensitiveFields.contains(writer.getName())) {
                                writer.assignSerializer(new JsonSerializer<>() {
                                    @Override
                                    public void serialize(Object value, JsonGenerator gen,
                                                          SerializerProvider serializers) throws IOException {
                                        gen.writeString(MASK);
                                    }
                                });
                            }
                        }
                    }
                    return writers;
                }
            }));

    private final IOperationLogService operationLogService;

    @Around("execution(public * cn.cangjiecloud..controller..*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs == null ? null : attrs.getRequest();
        if (request == null) {
            return pjp.proceed();
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String params = serializeArgs(pjp.getArgs());
        boolean isWrite = isWriteMethod(method);

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long duration = System.currentTimeMillis() - start;

            // 所有请求都打印日志
            log.info("{} {} | params: {} | cost: {}ms", method, uri, params != null ? params : "-", duration);

            // 写操作持久化到数据库
            if (isWrite) {
                OperationLogEntity entity = buildEntity(signature, method, uri, params, request, duration);
                entity.setStatus("success");
                entity.setResult(serializeResult(result));
                record(entity);
            }

            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - start;

            log.error("{} {} | params: {} | cost: {}ms | error: {}", method, uri,
                    params != null ? params : "-", duration, e.getMessage());

            // 写操作失败也持久化，记录失败状态与错误信息
            if (isWrite) {
                OperationLogEntity entity = buildEntity(signature, method, uri, params, request, duration);
                entity.setStatus("fail");
                entity.setErrorMessage(e.getMessage());
                record(entity);
            }

            throw e;
        }
    }

    private OperationLogEntity buildEntity(MethodSignature signature, String method, String uri, String params,
                                           HttpServletRequest request, long duration) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setModule(resolveModule(signature.getDeclaringType()));
        entity.setAction(signature.getMethod().getName());
        entity.setMethod(method);
        entity.setUri(uri);
        entity.setParams(params);
        String traceId = TraceContext.getTraceId();
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            TraceContext.setTraceId(traceId);
        }
        entity.setTraceId(traceId);
        entity.setIp(resolveIp(request));
        entity.setDuration(duration);
        fillUser(entity);
        return entity;
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
                    .map(this::toMaskedJson)
                    .map(this::truncate)
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
            return truncate(toMaskedJson(result));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 使用带敏感字段掩码的 ObjectMapper 序列化对象，@Sensitive 字段值替换为 ******。
     */
    private String toMaskedJson(Object value) {
        try {
            return MASKED_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 反射扫描类中标注了 @Sensitive 的字段名。
     */
    private static Set<String> resolveSensitiveFieldsStatic(Class<?> clazz) {
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