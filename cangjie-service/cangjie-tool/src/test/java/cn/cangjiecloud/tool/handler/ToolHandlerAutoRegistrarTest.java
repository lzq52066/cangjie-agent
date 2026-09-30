package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.annotation.ToolHandlerType;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ToolHandlerAutoRegistrar} 单元测试：Spring 单例初始化完成后，
 * 只把带 {@link ToolHandlerType} 注解的 {@link AbsToolHandler} Bean 注册进注册中心。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("工具处理器自动注册器 - ToolHandlerAutoRegistrar")
class ToolHandlerAutoRegistrarTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ToolHandlerRegistry registry;

    private ToolHandlerAutoRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new ToolHandlerAutoRegistrar(applicationContext, registry);
    }

    /** 带注解的处理器 */
    @ToolHandlerType(ToolConstants.ToolType.HTTP)
    static class AnnotatedHandler extends StubHandler {
    }

    /** 未带注解的处理器 */
    static class PlainHandler extends StubHandler {
    }

    /** 测试用最小处理器实现 */
    static class StubHandler extends AbsToolHandler {

        @Override
        public ToolSpecification buildToolSpecification(ToolEntity entity) {
            return ToolSpecification.builder().toolId(entity.getId()).build();
        }

        @Override
        public ToolExecuteResultDTO execute(ToolEntity entity, Map<String, Object> params) {
            return ToolExecuteResultDTO.builder().success(true).build();
        }
    }

    @Nested
    @DisplayName("afterSingletonsInstantiated")
    class AfterSingletonsInstantiated {

        @Test
        @DisplayName("带注解的处理器按注解值注册，且注解值来自真实 handler 类")
        void shouldRegisterAnnotatedHandler() {
            AnnotatedHandler annotated = new AnnotatedHandler();
            Map<String, AbsToolHandler> beans = new LinkedHashMap<>();
            beans.put("annotatedHandler", annotated);
            when(applicationContext.getBeansOfType(AbsToolHandler.class)).thenReturn(beans);

            registrar.afterSingletonsInstantiated();

            verify(registry).register(eq(ToolConstants.ToolType.HTTP), eq(annotated));
        }

        @Test
        @DisplayName("无注解的处理器被跳过")
        void shouldSkipHandlerWithoutAnnotation() {
            Map<String, AbsToolHandler> beans = new LinkedHashMap<>();
            beans.put("plainHandler", new PlainHandler());
            when(applicationContext.getBeansOfType(AbsToolHandler.class)).thenReturn(beans);

            registrar.afterSingletonsInstantiated();

            verify(registry, never()).register(anyString(), any(AbsToolHandler.class));
        }

        @Test
        @DisplayName("混合场景：仅注解处理器被注册")
        void shouldRegisterOnlyAnnotatedAmongMixedBeans() {
            AnnotatedHandler annotated = new AnnotatedHandler();
            PlainHandler plain = new PlainHandler();
            Map<String, AbsToolHandler> beans = new LinkedHashMap<>();
            beans.put("plainHandler", plain);
            beans.put("annotatedHandler", annotated);
            when(applicationContext.getBeansOfType(AbsToolHandler.class)).thenReturn(beans);

            registrar.afterSingletonsInstantiated();

            assertThat(annotated.getClass().getAnnotation(ToolHandlerType.class)).isNotNull();
            verify(registry).register(ToolConstants.ToolType.HTTP, annotated);
            verify(registry, never()).register(anyString(), eq(plain));
        }

        @Test
        @DisplayName("容器中没有处理器 Bean 时不发生注册")
        void shouldDoNothingWhenNoHandlers() {
            when(applicationContext.getBeansOfType(AbsToolHandler.class)).thenReturn(Map.of());

            registrar.afterSingletonsInstantiated();

            verifyNoInteractions(registry);
        }
    }
}
