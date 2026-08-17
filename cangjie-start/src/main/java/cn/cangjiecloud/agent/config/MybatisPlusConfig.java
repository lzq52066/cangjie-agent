package cn.cangjiecloud.agent.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        try {
            // MP 3.5.9 起分页插件拆分到 mybatis-plus-jsqlparser，包名改为 .inner
            Class<?> clazz = Class.forName("com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor");
            Object pagination = clazz.getConstructor(DbType.class).newInstance(DbType.POSTGRE_SQL);
            interceptor.addInnerInterceptor((com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor) pagination);
        } catch (Exception e) {
            throw new IllegalStateException("分页拦截器初始化失败，请确认 mybatis-plus-jsqlparser 依赖存在", e);
        }
        return interceptor;
    }
}
