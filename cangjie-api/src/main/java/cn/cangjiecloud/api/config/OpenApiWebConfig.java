package cn.cangjiecloud.api.config;

import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.common.constant.AppConst;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 对外接口 Web 配置
 * <p>
 * 注册 {@link OpenApiAuthInterceptor}，对 /api/open/** 统一做 API Key 鉴权。
 * /api/open/** 已在 Sa-Token 拦截器白名单中（免登录），故此处即为唯一鉴权入口。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiWebConfig implements WebMvcConfigurer {

    private final IApplicationService applicationService;

    /** 是否允许网页匿名聊天（免 API Key），本地部署默认开启 */
    @Value("${cangjie.openapi.web-anonymous:true}")
    private boolean webAnonymousEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OpenApiAuthInterceptor(applicationService, webAnonymousEnabled))
                .addPathPatterns(AppConst.OPEN_API + "/**")
                // 登录/登出发生在持有任何凭据之前，不属于应用 API Key 体系
                .excludePathPatterns(AppConst.OPEN_API + "/auth/**");
    }
}