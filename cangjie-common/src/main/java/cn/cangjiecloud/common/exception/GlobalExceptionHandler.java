package cn.cangjiecloud.common.exception;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.api.ResultCode;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public R<Void> handleApiException(ApiException e) {
        log.error("业务异常：{}", e.getMessage());
        return R.fail(e.getResultCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLoginException(NotLoginException e) {
        return R.fail(ResultCode.UN_AUTHORIZED);
    }

    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermissionException(NotPermissionException e) {
        return R.fail(ResultCode.NO_PERMISSION);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleError(MissingServletRequestParameterException e) {
        log.warn("缺少必要的请求参数：{}", e.getMessage());
        return R.fail(ResultCode.PARAM_MISS, String.format("缺少必要的请求参数：%s", e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleError(MethodArgumentTypeMismatchException e) {
        log.warn("请求参数类型错误：{}", e.getMessage());
        return R.fail(ResultCode.PARAM_TYPE_ERROR, String.format("请求参数类型错误：%s", e.getName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleError(MethodArgumentNotValidException e) {
        log.warn("参数校验失败：{}", e.getMessage());
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage()).collect(Collectors.joining(", "));
        return R.fail(ResultCode.PARAM_VALID_ERROR, message);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleError(BindException e) {
        log.warn("参数绑定错误：{}", e.getMessage());
        FieldError error = e.getBindingResult().getFieldError();
        String message = error == null ? ResultCode.PARAM_BIND_ERROR.getMessage()
                : error.getField() + ": " + error.getDefaultMessage();
        return R.fail(ResultCode.PARAM_BIND_ERROR, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleError(ConstraintViolationException e) {
        log.warn("参数校验失败：{}", e.getMessage());
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
        return R.fail(ResultCode.PARAM_VALID_ERROR, message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleError(HttpRequestMethodNotSupportedException e) {
        log.error("不支持当前请求方法：{}", e.getMessage());
        return R.fail(ResultCode.METHOD_NOT_SUPPORTED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public R<Void> handleError(HttpMediaTypeNotSupportedException e) {
        log.error("不支持当前媒体类型：{}", e.getMessage());
        return R.fail(ResultCode.MEDIA_TYPE_NOT_SUPPORTED);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleError(HttpMessageNotReadableException e) {
        log.warn("消息不能读取：{}", e.getMessage());
        return R.fail(ResultCode.MSG_NOT_READABLE);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleError(Throwable e) {
        log.error("服务器内部错误", e);
        return R.fail(ResultCode.SERVER_ERROR);
    }
}
