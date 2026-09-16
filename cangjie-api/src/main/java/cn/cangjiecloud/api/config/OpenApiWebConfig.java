package cn.cangjiecloud.api.config;

import cn.cangjiecloud.common.constant.AppConst;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 对外接口 Web 配置
 * <p>
 * 注册 {@link OpenApiAuthInterceptor}，对 /api/open/** 统一做鉴权（应用 API Key 或企业 OIDC JWT）。
 * /api/open/** 已在 Sa-Token 拦截器白名单中（免登录），故此处即为唯一鉴权入口。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiWebConfig implements WebMvcConfigurer {

    private final OpenApiAuthInterceptor openApiAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(openApiAuthInterceptor)
                .addPathPatterns(AppConst.OPEN_API + "/**")
                // 登录/登出发生在持有任何凭据之前，不属于应用 API Key 体系
                .excludePathPatterns(AppConst.OPEN_API + "/auth/**");
    }
}