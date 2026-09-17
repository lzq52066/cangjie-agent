package cn.cangjiecloud.common.exception;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.api.ResultCode;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 全局异常处理 {@link GlobalExceptionHandler} 单元测试。
 * 通过直接调用各 handler 方法校验返回的 {@link R} 状态码与消息，无需 Spring MVC。
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void apiExceptionKeepsCodeAndMessage() {
        R<Void> r = handler.handleApiException(new ApiException(ResultCode.NOT_FOUND));
        assertThat(r.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(r.getMsg()).isEqualTo(ResultCode.NOT_FOUND.getMessage());
    }

    @Test
    void notLoginMapsToUnauthorized() {
        R<Void> r = handler.handleNotLoginException(mock(NotLoginException.class));
        assertThat(r.getCode()).isEqualTo(ResultCode.UN_AUTHORIZED.getCode());
    }

    @Test
    void notPermissionMapsToNoPermission() {
        R<Void> r = handler.handleNotPermissionException(mock(NotPermissionException.class));
        assertThat(r.getCode()).isEqualTo(ResultCode.NO_PERMISSION.getCode());
    }

    @Test
    void missingParameterIncludesName() {
        MissingServletRequestParameterException e = mock(MissingServletRequestParameterException.class);
        when(e.getParameterName()).thenReturn("id");
        R<Void> r = handler.handleError(e);
        assertThat(r.getCode()).isEqualTo(ResultCode.PARAM_MISS.getCode());
        assertThat(r.getMsg()).isEqualTo("缺少必要的请求参数：id");
    }

    @Test
    void typeMismatchIncludesName() {
        MethodArgumentTypeMismatchException e = mock(MethodArgumentTypeMismatchException.class);
        when(e.getName()).thenReturn("age");
        R<Void> r = handler.handleError(e);
        assertThat(r.getCode()).isEqualTo(ResultCode.PARAM_TYPE_ERROR.getCode());
        assertThat(r.getMsg()).isEqualTo("请求参数类型错误：age");
    }

    @Test
    void methodArgumentNotValidJoinsFieldErrors() {
        MethodArgumentNotValidException e = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        FieldError fe = mock(FieldError.class);
        when(fe.getField()).thenReturn("name");
        when(fe.getDefaultMessage()).thenReturn("不能为空");
        when(e.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(fe));
        R<Void> r = handler.handleError(e);
        assertThat(r.getCode()).isEqualTo(ResultCode.PARAM_VALID_ERROR.getCode());
        assertThat(r.getMsg()).isEqualTo("name: 不能为空");
    }

    @Test
    void bindExceptionUsesFieldError() {
        BindException e = mock(BindException.class);
        BindingResult br = mock(BindingResult.class);
        FieldError fe = mock(FieldError.class);
        when(fe.getField()).thenReturn("city");
        when(fe.getDefaultMessage()).thenReturn("必填");
        when(e.getBindingResult()).thenReturn(br);
        when(br.getFieldError()).thenReturn(fe);
        R<Void> r = handler.handleError(e);
        assertThat(r.getMsg()).isEqualTo("city: 必填");
    }

    @Test
    void bindExceptionWithoutFieldErrorFallsBackToDefaultMessage() {
        BindException e = mock(BindException.class);
        BindingResult br = mock(BindingResult.class);
        when(e.getBindingResult()).thenReturn(br);
        when(br.getFieldError()).thenReturn(null);
        R<Void> r = handler.handleError(e);
        assertThat(r.getCode()).isEqualTo(ResultCode.PARAM_BIND_ERROR.getCode());
        assertThat(r.getMsg()).isEqualTo(ResultCode.PARAM_BIND_ERROR.getMessage());
    }

    @Test
    void constraintViolationJoinsMessages() {
        ConstraintViolationException e = mock(ConstraintViolationException.class);
        @SuppressWarnings("rawtypes")
        ConstraintViolation v = mock(ConstraintViolation.class);
        when(v.getMessage()).thenReturn("必须大于0");
        when(e.getConstraintViolations()).thenReturn(Set.of(v));
        R<Void> r = handler.handleError(e);
        assertThat(r.getCode()).isEqualTo(ResultCode.PARAM_VALID_ERROR.getCode());
        assertThat(r.getMsg()).isEqualTo("必须大于0");
    }

    @Test
    void methodNotSupported() {
        R<Void> r = handler.handleError(mock(HttpRequestMethodNotSupportedException.class));
        assertThat(r.getCode()).isEqualTo(ResultCode.METHOD_NOT_SUPPORTED.getCode());
    }

    @Test
    void mediaTypeNotSupported() {
        R<Void> r = handler.handleError(mock(HttpMediaTypeNotSupportedException.class));
        assertThat(r.getCode()).isEqualTo(ResultCode.MEDIA_TYPE_NOT_SUPPORTED.getCode());
    }

    @Test
    void messageNotReadable() {
        R<Void> r = handler.handleError(mock(HttpMessageNotReadableException.class));
        assertThat(r.getCode()).isEqualTo(ResultCode.MSG_NOT_READABLE.getCode());
    }

    @Test
    void genericThrowableMapsToServerError() {
        R<Void> r = handler.handleError(new RuntimeException("boom"));
        assertThat(r.getCode()).isEqualTo(ResultCode.SERVER_ERROR.getCode());
    }
}
