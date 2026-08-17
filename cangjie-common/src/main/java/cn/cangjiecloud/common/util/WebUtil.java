package cn.cangjiecloud.common.util;

import cn.dev33.satoken.context.SaHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public class WebUtil {

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    public static String getTokenValue() {
        try {
            return SaHolder.getRequest().getHeader("Authorization");
        } catch (Exception e) {
            HttpServletRequest req = getRequest();
            if (req == null) return null;
            String auth = req.getHeader("Authorization");
            if (auth == null) {
                auth = req.getParameter("token");
            }
            return auth;
        }
    }

    public static String getBearerToken() {
        String token = getTokenValue();
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }

    public static String getClientIP() {
        HttpServletRequest req = getRequest();
        if (req == null) return "";
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        return Optional.ofNullable(ip).map(s -> s.split(",")[0].trim()).orElse("");
    }
}
