package cn.cangjiecloud.common.util;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class I18nUtil {

    private static MessageSource messageSource;

    public I18nUtil(MessageSource messageSource) {
        I18nUtil.messageSource = messageSource;
    }

    public static String get(String key, Object... args) {
        try {
            return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return key;
        }
    }
}
