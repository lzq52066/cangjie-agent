package cn.cangjiecloud.common.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一响应体 {@link R} 单元测试。
 */
class RTest {

    @Test
    void okShouldUseSuccessCodeAndNoData() {
        R<Object> r = R.ok();
        assertEquals(ResultCode.SUCCESS.getCode(), r.getCode());
        assertEquals(ResultCode.SUCCESS.getMessage(), r.getMsg());
        assertNull(r.getData());
        assertNotNull(r.getTimestamp());
        assertTrue(r.isSuccess());
    }

    @Test
    void dataShouldCarryPayload() {
        R<String> r = R.data("hello");
        assertTrue(r.isSuccess());
        assertEquals("hello", r.getData());
        assertEquals(200, r.getCode());
    }

    @Test
    void okWithCustomMessageShouldOverrideMsg() {
        R<Object> r = R.ok("自定义提示");
        assertEquals("自定义提示", r.getMsg());
        assertEquals(ResultCode.SUCCESS.getCode(), r.getCode());
    }

    @Test
    void failShouldUseFailureCode() {
        R<Object> r = R.fail();
        assertEquals(ResultCode.FAILURE.getCode(), r.getCode());
        assertEquals(ResultCode.FAILURE.getMessage(), r.getMsg());
        assertFalse(r.isSuccess());
    }

    @Test
    void failWithMessageShouldOverrideMsg() {
        R<Object> r = R.fail("出错了");
        assertEquals("出错了", r.getMsg());
        assertEquals(ResultCode.FAILURE.getCode(), r.getCode());
    }

    @Test
    void failWithResultCodeShouldUseItsCodeAndMessage() {
        R<Object> r = R.fail(ResultCode.NOT_FOUND);
        assertEquals(ResultCode.NOT_FOUND.getCode(), r.getCode());
        assertEquals(ResultCode.NOT_FOUND.getMessage(), r.getMsg());
    }

    @Test
    void failWithResultCodeAndMessageShouldOverrideMsg() {
        R<Object> r = R.fail(ResultCode.NO_PERMISSION, "越权了");
        assertEquals(ResultCode.NO_PERMISSION.getCode(), r.getCode());
        assertEquals("越权了", r.getMsg());
    }

    @Test
    void statusShouldMapBooleanToResult() {
        assertTrue(R.status(true).isSuccess());
        assertFalse(R.status(false).isSuccess());
    }

    @Test
    void isSuccessShouldBeFalseWhenCodeIsNull() {
        R<Object> r = new R<>();
        r.setCode(null);
        assertFalse(r.isSuccess());
    }
}
