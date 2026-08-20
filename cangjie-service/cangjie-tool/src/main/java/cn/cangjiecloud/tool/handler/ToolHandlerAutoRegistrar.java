package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.tool.annotation.ToolHandlerType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具处理器自动注册器
 * 在 Spring 单例初始化完成后自动扫描所有 AbsToolHandler 子类并注册到 ToolHandlerRegistry
 */
@Slf4j
@Component
public class ToolHandlerAutoRegistrar implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final ToolHandlerRegistry registry;

    public ToolHandlerAutoRegistrar(ApplicationContext applicationContext, ToolHandlerRegistry registry) {
        this.applicationContext = applicationContext;
        this.registry = registry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, AbsToolHandler> beans = applicationContext.getBeansOfType(AbsToolHandler.class);
        for (AbsToolHandler handler : beans.values()) {
            ToolHandlerType annotation = handler.getClass().getAnnotation(ToolHandlerType.class);
            if (annotation != null) {
                registry.register(annotation.value(), handler);
                log.info("工具处理器已注册: {} -> {}", annotation.value(), handler.getClass().getSimpleName());
            }
        }
    }
}