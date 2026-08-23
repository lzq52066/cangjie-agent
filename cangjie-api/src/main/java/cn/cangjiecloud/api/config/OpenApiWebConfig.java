package cn.cangjiecloud.api.config;

import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.common.constant.AppConst;
import lombok.RequiredArgsConstructor;
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OpenApiAuthInterceptor(applicationService))
                .addPathPatterns(AppConst.OPEN_API + "/**");
    }
}