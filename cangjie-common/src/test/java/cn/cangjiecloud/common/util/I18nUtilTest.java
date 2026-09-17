package cn.cangjiecloud.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 国际化工具 {@link I18nUtil} 单元测试。
 * 通过构造函数注入 mock 的 MessageSource 来初始化其静态字段。
 */
class I18nUtilTest {

    @Test
    void getShouldReturnTranslatedMessage() {
        MessageSource ms = mock(MessageSource.class);
        when(ms.getMessage(eq("user.name"), any(), eq("user.name"), any())).thenReturn("用户名");
        new I18nUtil(ms);

        assertEquals("用户名", I18nUtil.get("user.name"));
    }

    @Test
    void getShouldReturnKeyWhenLookupThrows() {
        MessageSource ms = mock(MessageSource.class);
        when(ms.getMessage(anyString(), any(), anyString(), any()))
                .thenThrow(new RuntimeException("missing"));
        new I18nUtil(ms);

        assertEquals("any.key", I18nUtil.get("any.key", "a1"));
    }
}
