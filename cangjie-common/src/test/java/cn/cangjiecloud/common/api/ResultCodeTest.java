package cn.cangjiecloud.common.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 结果码枚举 {@link ResultCode} 单元测试。
 */
class ResultCodeTest {

    @Test
    void successAndFailureCodes() {
        assertEquals(200, ResultCode.SUCCESS.getCode());
        assertEquals("操作成功", ResultCode.SUCCESS.getMessage());
        assertEquals(400, ResultCode.FAILURE.getCode());
    }

    @Test
    void httpStyleCodes() {
        assertEquals(401, ResultCode.UN_AUTHORIZED.getCode());
        assertEquals(403, ResultCode.NO_PERMISSION.getCode());
        assertEquals(404, ResultCode.NOT_FOUND.getCode());
        assertEquals(405, ResultCode.METHOD_NOT_SUPPORTED.getCode());
        assertEquals(415, ResultCode.MEDIA_TYPE_NOT_SUPPORTED.getCode());
        assertEquals(500, ResultCode.SERVER_ERROR.getCode());
    }

    @Test
    void parameterCodesAllMapTo400() {
        assertEquals(400, ResultCode.PARAM_MISS.getCode());
        assertEquals(400, ResultCode.PARAM_TYPE_ERROR.getCode());
        assertEquals(400, ResultCode.PARAM_BIND_ERROR.getCode());
        assertEquals(400, ResultCode.PARAM_VALID_ERROR.getCode());
        assertEquals(400, ResultCode.MSG_NOT_READABLE.getCode());
    }

    @Test
    void everyCodeHasNonEmptyMessage() {
        for (ResultCode code : ResultCode.values()) {
            assertNotNull(code.getCode());
            assertNotNull(code.getMessage());
            // 满足 IResultCode 契约
            IResultCode asInterface = code;
            assertEquals(code.getCode(), asInterface.getCode());
        }
    }
}
