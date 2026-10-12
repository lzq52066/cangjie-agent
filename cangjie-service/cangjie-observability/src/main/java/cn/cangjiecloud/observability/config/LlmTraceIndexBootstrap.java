package cn.cangjiecloud.observability.config;

import cn.cangjiecloud.observability.mapper.LlmTraceIndexMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * llm_trace 正文 trgm 索引的启动后自建。
 * <p>
 * 为什么不在 Flyway 迁移里建：迁移跑在事务里，而 {@code CREATE INDEX CONCURRENTLY} 不能在事务内
 * 执行；不带 CONCURRENTLY 建则持 SHARE 锁，大表上等于在升级期间把对话写入整段停掉。
 * 所以迁移只补扩展，索引交给这里，在启动完成后的后台线程建，全程不碰主链路。
 * <p>
 * 建索引期间关键词检索照常可用（只是回到顺序扫描），因此这一步失败不影响功能，只影响速度；
 * 单次失败也不会重试刷屏 —— 下次重启会再试一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cangjie.observability.llm-trace.ensure-trgm-index", havingValue = "true", matchIfMissing = true)
public class LlmTraceIndexBootstrap {

    private final LlmTraceIndexMapper indexMapper;

    /** 只建一次：事件重复投递（测试上下文、手工 publish）不该再抢一次库连接 */
    private final AtomicBoolean started = new AtomicBoolean();

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::ensureIndexes, "llm-trace-trgm-index");
        // 守护线程：建索引不该拖住进程退出
        worker.setDaemon(true);
        worker.start();
    }

    private void ensureIndexes() {
        try {
            indexMapper.ensureTrgmExtension();
        } catch (Exception ex) {
            // 精简镜像里没有 pg_trgm 共享库：没有索引照样能查，只是回到顺序扫描
            log.info("pg_trgm 扩展不可用，跳过正文关键词索引: {}", ex.getMessage());
            return;
        }
        build("llm_trace_prompt_trgm_idx", indexMapper::createPromptTrgmIndex);
        build("llm_trace_response_trgm_idx", indexMapper::createResponseTrgmIndex);
    }

    private void build(String indexName, Runnable task) {
        long begin = System.currentTimeMillis();
        try {
            task.run();
            log.info("llm_trace 正文索引就绪: {}（{} ms）", indexName, System.currentTimeMillis() - begin);
        } catch (Exception ex) {
            log.warn("llm_trace 正文索引创建失败，关键词检索退回顺序扫描: {}（下次启动再试）, {}",
                    indexName, ex.getMessage());
        }
    }
}
