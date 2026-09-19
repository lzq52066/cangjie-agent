package cn.cangjiecloud.agent.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import cn.cangjiecloud.common.constant.AppConst;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    private final ForceChangePasswordInterceptor forceChangePasswordInterceptor;

    public SaTokenConfigure(ForceChangePasswordInterceptor forceChangePasswordInterceptor) {
        this.forceChangePasswordInterceptor = forceChangePasswordInterceptor;
    }

    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForSimple();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 路由级鉴权由注解 @SaCheckLogin 处理，这里只注册拦截器让 Sa-Token 生效
        })).addPathPatterns("/**")
          .excludePathPatterns(
                AppConst.OPEN_API + "/**",
                AppConst.CHAT_API + "/**",
                AppConst.TRIGGER_API + "/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**",
                "/doc.html",
                "/webjars/**",
                "/favicon.ico",
                "/static/**",
                "/admin/**",
                "/chat/**",
                "/embed/**",
                "/",
                "/*.html",
                "/assets/**"
          );
        // 首次登录强制改密兜底拦截，注册在 Sa-Token 之后
        registry.addInterceptor(forceChangePasswordInterceptor).addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
