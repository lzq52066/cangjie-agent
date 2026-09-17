package cn.cangjiecloud.agent.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    /**
     * 单次分页的最大行数兜底：各列表接口的 pageSize 由调用方传入，服务端不做逐接口钳制，
     * 统一以此上限防止构造超大 pageSize 拉取全表（系统指标图表接口按时间范围取明细，需要该额度）。
     */
    private static final long MAX_LIMIT = 5000L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        try {
            // MP 3.5.9 起分页插件拆分到 mybatis-plus-jsqlparser，包名改为 .inner
            Class<?> clazz = Class.forName("com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor");
            Object pagination = clazz.getConstructor(DbType.class).newInstance(DbType.POSTGRE_SQL);
            clazz.getMethod("setMaxLimit", Long.class).invoke(pagination, MAX_LIMIT);
            // 页码超出总页数时回到第一页，而不是返回空列表（深翻页的 offset 也已被 maxLimit 钳制）
            clazz.getMethod("setOverflow", boolean.class).invoke(pagination, true);
            interceptor.addInnerInterceptor((com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor) pagination);
        } catch (Exception e) {
            throw new IllegalStateException("分页拦截器初始化失败，请确认 mybatis-plus-jsqlparser 依赖存在", e);
        }
        return interceptor;
    }
}
