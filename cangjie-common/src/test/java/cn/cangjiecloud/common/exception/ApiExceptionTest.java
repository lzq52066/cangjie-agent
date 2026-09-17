package cn.cangjiecloud.common.exception;

import cn.cangjiecloud.common.api.ResultCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 业务异常 {@link ApiException} 单元测试。
 */
class ApiExceptionTest {

    @Test
    void messageOnlyDefaultsToFailure() {
        ApiException e = new ApiException("boom");
        assertEquals("boom", e.getMessage());
        assertEquals(ResultCode.FAILURE, e.getResultCode());
    }

    @Test
    void resultCodeOnlyUsesItsMessage() {
        ApiException e = new ApiException(ResultCode.NOT_FOUND);
        assertEquals(ResultCode.NOT_FOUND, e.getResultCode());
        assertEquals(ResultCode.NOT_FOUND.getMessage(), e.getMessage());
    }

    @Test
    void resultCodeWithCause() {
        Throwable cause = new IllegalStateException("root");
        ApiException e = new ApiException(ResultCode.SERVER_ERROR, cause);
        assertSame(cause, e.getCause());
        assertEquals(ResultCode.SERVER_ERROR, e.getResultCode());
    }

    @Test
    void resultCodeWithCustomMessage() {
        ApiException e = new ApiException(ResultCode.NO_PERMISSION, "越权了");
        assertEquals("越权了", e.getMessage());
        assertEquals(ResultCode.NO_PERMISSION, e.getResultCode());
    }

    @Test
    void messageWithCauseDefaultsToFailure() {
        Throwable cause = new RuntimeException("root");
        ApiException e = new ApiException("oops", cause);
        assertSame(cause, e.getCause());
        assertEquals(ResultCode.FAILURE, e.getResultCode());
    }
}
