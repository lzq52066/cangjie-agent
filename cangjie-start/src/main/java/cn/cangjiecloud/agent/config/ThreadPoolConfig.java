package cn.cangjiecloud.agent.config;

import cn.cangjiecloud.observability.context.TraceContextTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "businessExecutor")
    public Executor businessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(512);
        executor.setThreadNamePrefix("business-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new TraceContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * 评估运行调度池：只用来把一次评测从 HTTP 请求线程上摘下来。
     * <p>
     * 必须是独立池且用 AbortPolicy，两个理由：
     * <ul>
     *   <li>businessExecutor 用的是 CallerRunsPolicy，池满时任务会退回调用线程就地跑完 ——
     *       一次评测动辄几分钟，那等于把提交请求的线程按住几分钟；</li>
     *   <li>评测任务自己还会在 {@code runInParallel} 里建子池并等回棒，借同一个共享池
     *       就是「池内任务往同池提交子任务再等待」，池满时互等死。</li>
     * </ul>
     * 拒绝时抛 {@code RejectedExecutionException}，调用方据此把「排不进去」如实告诉用户，
     * 而不是留下一个没人认领的 pending run。
     */
    @Bean(name = "evalRunExecutor")
    public ThreadPoolTaskExecutor evalRunExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("eval-run-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(new TraceContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean(name = "chatExecutor")
    public Executor chatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(128);
        executor.setQueueCapacity(1024);
        executor.setThreadNamePrefix("chat-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new TraceContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * 流式 LLM 生产者线程池：承载模型 token 流读取任务，
     * 替代裸 new Thread，便于监控、限流与优雅关闭
     */
    @Bean(name = "llmStreamExecutor")
    public ThreadPoolTaskExecutor llmStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(128);
        executor.setThreadNamePrefix("llm-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new TraceContextTaskDecorator());
        executor.initialize();
        return executor;
    }
}
