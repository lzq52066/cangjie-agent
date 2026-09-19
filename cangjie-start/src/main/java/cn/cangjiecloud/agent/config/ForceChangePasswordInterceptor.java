package cn.cangjiecloud.agent.config;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.user.entity.UserEntity;
import cn.cangjiecloud.user.service.IUserService;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 首次登录强制改密拦截器：
 * 当登录用户的 must_change_password=true 时，除改密、身份查询等必要接口外，
 * 一律拒绝其访问管理端业务接口，作为前端强制改密页之外的服务端兜底。
 */
@Component
@RequiredArgsConstructor
public class ForceChangePasswordInterceptor implements HandlerInterceptor {

    private final IUserService userService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 强制改密期间仍允许访问的接口（方法 + 路径） */
    private static final List<String> WHITELIST = List.of(
            "PUT:" + AppConst.ADMIN_API + "/profile/password",
            "GET:" + AppConst.ADMIN_API + "/user/info",
            "GET:" + AppConst.ADMIN_API + "/auth/keep-alive"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        if (!uri.startsWith(AppConst.ADMIN_API + "/") || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 未登录交给后续的 @SaCheckLogin / Sa-Token 处理，这里不拦截
        if (!StpUtil.isLogin()) {
            return true;
        }
        String methodUri = request.getMethod() + ":" + uri;
        for (String pattern : WHITELIST) {
            if (pathMatcher.match(pattern, methodUri)) {
                return true;
            }
        }

        String uid = UserContext.getUserId();
        if (uid != null) {
            UserEntity user = userService.getById(uid);
            if (user != null && Boolean.TRUE.equals(user.getMustChangePassword())) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(
                        "{\"code\":428,\"msg\":\"首次登录请先修改初始密码\",\"data\":null}");
                return false;
            }
        }
        return true;
    }
}
