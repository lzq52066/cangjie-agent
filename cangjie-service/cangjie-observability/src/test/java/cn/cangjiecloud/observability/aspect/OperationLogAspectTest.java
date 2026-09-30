package cn.cangjiecloud.observability.aspect;

import cn.cangjiecloud.common.annotation.Sensitive;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.observability.context.TraceContext;
import cn.cangjiecloud.observability.entity.OperationLogEntity;
import cn.cangjiecloud.observability.service.IOperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OperationLogAspect} 单元测试。
 * <p>
 * 用 {@link MockHttpServletRequest} + {@link RequestContextHolder} 构造请求现场，
 * mock 掉 {@code UserContext} 静态方法（无 Web 容器时 Sa-Token 会抛异常）；
 * 覆盖写操作持久化、失败留痕回抛、读操作跳过、参数/结果脱敏与截断等核心分支。
 */
@ExtendWith(MockitoExtension.class)
class OperationLogAspectTest {

    @Mock
    private IOperationLogService operationLogService;

    @Mock
    private ProceedingJoinPoint pjp;

    @Mock
    private MethodSignature signature;

    private OperationLogAspect aspect;

    private MockedStatic<UserContext> userContextMock;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        aspect = new OperationLogAspect(operationLogService);
        userContextMock = Mockito.mockStatic(UserContext.class);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/order/item");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
        RequestContextHolder.resetRequestAttributes();
        TraceContext.clear();
    }

    /**
     * 测试用 Controller 桩：类名参与模块名推导（OrderItemController → order.item）
     */
    public static class OrderItemController {
        public String create(LoginForm form) {
            return "ok";
        }
    }

    /** 带 @Sensitive 字段的请求体桩 */
    public static class LoginForm {
        private final String username;
        @Sensitive
        private final String password;

        LoginForm(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    private void stubSignature() throws NoSuchMethodException {
        Method method = OrderItemController.class.getMethod("create", LoginForm.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(OrderItemController.class);
        when(signature.getMethod()).thenReturn(method);
    }

    private OperationLogEntity capturePersisted() {
        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(operationLogService).record(captor.capture());
        return captor.getValue();
    }

    // ==================== 无请求上下文 ====================

    @Test
    void shouldProceedWithoutPersistingWhenNoRequestContext() throws Throwable {
        RequestContextHolder.resetRequestAttributes();
        when(pjp.proceed()).thenReturn("ok");

        assertThat(aspect.around(pjp)).isEqualTo("ok");

        verify(pjp).proceed();
        verifyNoInteractions(operationLogService);
    }

    // ==================== 写操作成功留痕 ====================

    @Nested
    class SuccessfulWrite {

        @Test
        void shouldPersistEntityWithMaskedSensitiveArgs() throws Throwable {
            stubSignature();
            request.setMethod("POST");
            LoginForm form = new LoginForm("alice", "s3cret");
            when(pjp.getArgs()).thenReturn(new Object[]{form});
            when(pjp.proceed()).thenReturn(form);
            userContextMock.when(UserContext::getUserId).thenReturn("user-1");
            userContextMock.when(UserContext::getIdentity)
                    .thenReturn(UserIdentity.builder().username("alice").build());

            Object result = aspect.around(pjp);

            assertThat(result).isSameAs(form);
            OperationLogEntity entity = capturePersisted();
            assertThat(entity.getModule()).isEqualTo("order.item");
            assertThat(entity.getAction()).isEqualTo("create");
            assertThat(entity.getMethod()).isEqualTo("POST");
            assertThat(entity.getUri()).isEqualTo("/api/admin/order/item");
            assertThat(entity.getStatus()).isEqualTo("success");
            assertThat(entity.getUserId()).isEqualTo("user-1");
            assertThat(entity.getUsername()).isEqualTo("alice");
            assertThat(entity.getDuration()).isNotNull().isGreaterThanOrEqualTo(0L);
            assertThat(entity.getTraceId()).hasSize(32).matches("[0-9a-f]{32}");
            assertThat(TraceContext.getTraceId()).isEqualTo(entity.getTraceId());
            assertThat(entity.getParams())
                    .contains("\"username\":\"alice\"", "\"password\":\"******\"")
                    .doesNotContain("s3cret");
            assertThat(entity.getResult())
                    .contains("\"password\":\"******\"")
                    .doesNotContain("s3cret");
            assertThat(entity.getErrorMessage()).isNull();
        }

        @Test
        void shouldReuseExistingTraceIdFromContext() throws Throwable {
            stubSignature();
            request.setMethod("PUT");
            TraceContext.setTraceId("trace-existing");
            when(pjp.proceed()).thenReturn("ok");

            aspect.around(pjp);

            assertThat(capturePersisted().getTraceId()).isEqualTo("trace-existing");
        }

        @Test
        void shouldResolveClientIpFromForwardedHeader() throws Throwable {
            stubSignature();
            request.setMethod("DELETE");
            request.addHeader("X-Forwarded-For", " 1.2.3.4 , 5.6.7.8");
            when(pjp.proceed()).thenReturn(null);

            aspect.around(pjp);

            assertThat(capturePersisted().getIp()).isEqualTo("1.2.3.4");
        }

        @Test
        void shouldFallbackToRemoteAddrWhenForwardedHeaderBlank() throws Throwable {
            stubSignature();
            request.setMethod("PATCH");
            request.addHeader("X-Forwarded-For", "  ");
            request.setRemoteAddr("10.0.0.8");
            when(pjp.proceed()).thenReturn(null);

            aspect.around(pjp);

            assertThat(capturePersisted().getIp()).isEqualTo("10.0.0.8");
        }
    }

    // ==================== 参数与结果序列化边界 ====================

    @Nested
    class SerializationEdges {

        @Test
        void shouldStoreNullParamsWhenArgsMissing() throws Throwable {
            stubSignature();
            request.setMethod("POST");
            when(pjp.getArgs()).thenReturn(new Object[0]);
            Object exploding = new Object() {
                @SuppressWarnings("unused")
                public String getBoom() {
                    throw new IllegalStateException("cannot serialize");
                }
            };
            when(pjp.proceed()).thenReturn(exploding);

            aspect.around(pjp);

            OperationLogEntity entity = capturePersisted();
            assertThat(entity.getParams()).isNull();
            assertThat(entity.getResult()).isNull();
            assertThat(entity.getStatus()).isEqualTo("success");
        }

        @Test
        void shouldDropServletTypesFromParams() throws Throwable {
            stubSignature();
            request.setMethod("POST");
            when(pjp.getArgs()).thenReturn(new Object[]{request, "payload"});
            when(pjp.proceed()).thenReturn(null);

            aspect.around(pjp);

            assertThat(capturePersisted().getParams()).isEqualTo("\"payload\"");
        }

        @Test
        void shouldPersistEmptyParamsWhenAllArgsAreServletTypes() throws Throwable {
            stubSignature();
            request.setMethod("POST");
            when(pjp.getArgs()).thenReturn(new Object[]{request});
            when(pjp.proceed()).thenReturn(null);

            aspect.around(pjp);

            assertThat(capturePersisted().getParams()).isEmpty();
        }

        @Test
        void shouldTruncateOverlongParamsToMaxLength() throws Throwable {
            stubSignature();
            request.setMethod("POST");
            when(pjp.getArgs()).thenReturn(new Object[]{"a".repeat(5000)});
            when(pjp.proceed()).thenReturn(null);

            aspect.around(pjp);

            String params = capturePersisted().getParams();
            assertThat(params).hasSize(2000).startsWith("\"");
        }
    }

    // ==================== 失败留痕 ====================

    @Nested
    class FailureWrite {

        @Test
        void shouldPersistFailureStatusAndRethrow() throws Throwable {
            stubSignature();
            request.setMethod("POST");
            when(pjp.getArgs()).thenReturn(new Object[]{"x"});
            when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> aspect.around(pjp))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("boom");

            OperationLogEntity entity = capturePersisted();
            assertThat(entity.getStatus()).isEqualTo("fail");
            assertThat(entity.getErrorMessage()).isEqualTo("boom");
            assertThat(entity.getResult()).isNull();
            assertThat(entity.getParams()).isEqualTo("\"x\"");
        }

        @Test
        void shouldStillReturnResultForReadRequestFailure() throws Throwable {
            when(pjp.getSignature()).thenReturn(signature);
            request.setMethod("GET");
            when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> aspect.around(pjp))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("boom");

            verify(operationLogService, never()).record(any(OperationLogEntity.class));
        }
    }

    // ==================== 用户信息与读操作 ====================

    @Nested
    class UserAndReadOnly {

        @Test
        void shouldIgnoreUserInfoWhenUserContextThrows() throws Throwable {
            stubSignature();
            request.setMethod("POST");
            when(pjp.proceed()).thenReturn(null);
            userContextMock.when(UserContext::getUserId)
                    .thenThrow(new IllegalStateException("not logged in"));

            aspect.around(pjp);

            OperationLogEntity entity = capturePersisted();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getUsername()).isNull();
            assertThat(entity.getStatus()).isEqualTo("success");
        }

        @Test
        void shouldNotPersistReadRequest() throws Throwable {
            when(pjp.getSignature()).thenReturn(signature);
            request.setMethod("GET");
            when(pjp.proceed()).thenReturn("ok");

            assertThat(aspect.around(pjp)).isEqualTo("ok");

            verify(operationLogService, never()).record(any(OperationLogEntity.class));
        }
    }
}
