package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.core.model.ModelPricing;
import cn.cangjiecloud.core.model.ModelPricingSource;
import cn.cangjiecloud.observability.context.TraceContext;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.service.ILlmTraceRecorder;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 调用追踪记录器实现
 * <p>
 * 提供统一的 trace 写入 API，支持从 TraceContext 自动获取 traceId/sessionId/userId，
 * 并支持可配置的脱敏开关。
 * <p>
 * 写入默认走「内存队列 + 定时批量落库」：一次对话会产 2~5 条模型调用，每条都在对话请求线程
 * 里同步写一行带上万字符正文的记录，高峰时 DB 往返会直接规到响应时延上。
 * 实体仍在调用线程组装（traceId / userId 靠 ThreadLocal，到异步线程就取不到了），
 * 只有落库跳到后台线程。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmTraceRecorderImpl implements ILlmTraceRecorder {

    private static final String STATUS_FAIL = "fail";

    private final ILlmTraceService llmTraceService;

    /**
     * 模型单价来源（由模型模块实现）：本模块不依赖模型模块，编译期只认 core 接口。
     * <p>
     * 没挂上时成本不估算，而不是当成免费：看板上的总成本静默偏低比缺一个数更难发现。
     */
    @Autowired(required = false)
    private ModelPricingSource pricingSource;

    /** 是否开启脱敏（默认 false，全量明文存储） */
    @Value("${cangjie.observability.llm-trace.mask.enabled:false}")
    private boolean maskEnabled;

    /** 是否存储完整 prompt/response 内容（默认 true） */
    @Value("${cangjie.observability.llm-trace.store-content:true}")
    private boolean storeContent;

    /** 内容最大存储长度（字符数，0 表示不限制），超长自动截断 */
    @Value("${cangjie.observability.llm-trace.max-content-length:20000}")
    private int maxContentLength;

    /**
     * 成功调用的采样率（0~1），失败一律留档。
     * <p>
     * 不采样时全量写：高流量应用一周就能把 llm_trace 养成几十 GB（prompt/response 是整张表
     * 最占空间的两列）；而把错误一起采掉的可观测性只剩下「一切正常」，等于没有。
     * <p>
     * 采样按 traceId 决定整条链路的去留（见 {@link #sampled(String, String)}）。
     */
    @Value("${cangjie.observability.llm-trace.sample-rate:1.0}")
    private double sampleRate;

    /** 异步批量落库开关；关掉则退回逐条同步写（单测与排障时好比对） */
    @Value("${cangjie.observability.llm-trace.async.enabled:true}")
    private boolean asyncEnabled;

    /** 缓冲队列容量 */
    @Value("${cangjie.observability.llm-trace.async.queue-capacity:2000}")
    private int queueCapacity;

    /** 单批落库行数 */
    @Value("${cangjie.observability.llm-trace.async.batch-size:200}")
    private int batchSize;

    /** 定时冲刷间隔 */
    @Value("${cangjie.observability.llm-trace.async.flush-interval-ms:2000}")
    private long flushIntervalMs;

    /** 容量来自配置，只能在字段注入完成后建队 */
    private BlockingQueue<LlmTraceEntity> queue;

    private ScheduledExecutorService flusher;

    /** 因队列满被丢弃的条数：不报错只计数，但要能看见丢多少 */
    private final AtomicLong dropped = new AtomicLong();

    /** 落库失败丢掉多少条（含批量写抛异常整批丢弃） */
    private final AtomicLong writeFailures = new AtomicLong();

    private volatile long lastReportedDropped;

    @PostConstruct
    void init() {
        queue = new LinkedBlockingQueue<>(Math.max(1, queueCapacity));
        if (!asyncEnabled) {
            return;
        }
        flusher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "llm-trace-flusher");
            // 守护线程：可观测性后台任务不该拖住进程退出
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(200L, flushIntervalMs);
        flusher.scheduleWithFixedDelay(this::flushQuietly, interval, interval, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void destroy() {
        if (flusher != null) {
            flusher.shutdown();
            try {
                // 等正在写的那一轮结束再补一次收尾冲刷：两边同时 drainTo 会把同一批数据拆成两次写
                if (!flusher.awaitTermination(5, TimeUnit.SECONDS)) {
                    flusher.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        // 退出前把内存里剩下的 trace 落下去：正在排障的那几条丢不得
        flushQuietly();
    }

    @Override
    public void recordSuccess(LlmTraceRecord record) {
        save(record, "success", null);
    }

    @Override
    public void recordFailure(LlmTraceRecord record, String errorMessage) {
        save(record, STATUS_FAIL, errorMessage);
    }

    private void save(LlmTraceRecord record, String status, String errorMessage) {
        String traceId = resolveTraceId(record);
        if (!sampled(status, traceId)) {
            return;
        }
        try {
            LlmTraceEntity trace = new LlmTraceEntity();
            trace.setTraceId(traceId);
            trace.setRunId(record.getRunId());
            trace.setRequestId(record.getRequestId());
            trace.setAppId(record.getAppId());
            trace.setAppName(record.getAppName());
            trace.setSessionId(resolveSessionId(record));
            trace.setUserId(resolveUserId(record));
            trace.setModelId(record.getModelId());
            trace.setModelName(record.getModelName());

            // promptContent：优先用调用方给定的文本；消息列表必须走 langchain4j 编解码器，
            // 否则会抛异常并把整条 trace 一起丢掉。
            String promptContent = record.getPromptContent();
            if (promptContent == null && record.getMessages() != null) {
                promptContent = serializeMessages(record.getMessages());
            }
            trace.setPromptContent(applyContentPolicy(promptContent));
            // 调用参数体积小且不含用户内容，不受 storeContent / 脱敏开关影响
            trace.setModelParams(record.getModelParams());

            trace.setInputTokens(record.getInputTokens());
            trace.setOutputTokens(record.getOutputTokens());
            trace.setTotalTokens(record.getTotalTokens());
            trace.setResponseContent(applyContentPolicy(record.getResponseContent()));
            trace.setFinishReason(record.getFinishReason());
            trace.setDuration(record.getDuration());
            trace.setTtft(ttftOf(record));
            trace.setCost(estimateCost(record));
            // 新调用一律先标「未评」：在线评测任务靠这一列捞未打标的样本，留 null 会让它反复重扫同一批
            trace.setEvaluated(Boolean.FALSE);
            trace.setStatus(status);
            trace.setErrorMessage(errorMessage);
            trace.setStartTime(record.getStartTimeMs() != null
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getStartTimeMs()), ZoneId.systemDefault())
                    : LocalDateTime.now());

            persist(trace);
        } catch (Exception ex) {
            log.warn("LLM trace 记录失败", ex);
        }
    }

    /**
     * 要不要留这一条。
     * <p>
     * 采样率配成 0 / 负数相当于「只留失败」而不是全留：配错一个数不该让表重新长回去。
     * <p>
     * 采样决定按 traceId 取，不逐调用掷随机数：一次对话本来就是 2~5 条调用组成的同一条链路，
     * 逐调用采样会随机留下其中几条，链路视图（{@code /traces/{traceId}/llm-calls}）拼出来永远残缺，
     * 而 trace_record 侧不采样、两侧永久对不上。按 traceId 决定则要么整条留、要么整条丢。
     * <p>
     * 注意「被点踩的调用强制留档」在写时采样下做不到：点踩是回答落库之后才发生的，
     * 那几条调用来不及回头改判。所以坏 case 回流（feedback → eval_case）取的是会话消息侧
     * （{@code ConversationSourceImpl}），不依赖这里的 llm_trace 有没有被采掉。
     */
    private boolean sampled(String status, String traceId) {
        if (STATUS_FAIL.equals(status) || sampleRate >= 1.0) {
            return true;
        }
        if (sampleRate <= 0.0) {
            return false;
        }
        return traceBucket(traceId) < sampleRate;
    }

    /**
     * traceId → [0,1) 的稳定桶位。同一时刻同一 traceId 恒得同一值（含其它 JVM），
     * 多实例部署时也不会出现「同一条链路在 A 实例留、在 B 实例丢」。
     */
    private static double traceBucket(String traceId) {
        // 没有 traceId 的行不能全落进同一个桶（那会变成「全留」或「全丢」二选一）：
        // 这种调用本就无链路可归，退回逐条随机
        int hash = traceId == null || traceId.isBlank()
                ? ThreadLocalRandom.current().nextInt()
                : traceId.hashCode();
        // 用 MurmurHash3 的 finalizer 打散低位：traceId 常带公共前缀，裸 hashCode 的低位分布不均
        int h = hash;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        h *= 0x846ca68b;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }

    /**
     * 实体已组装好，只把落库这一下跳出去。
     * <p>
     * 队列满时丢掉刚来的这一条并计数：可观测性数据不值得把对话主线程拖住等库，
     * 也不能无界堆积把堆吃空。
     */
    private void persist(LlmTraceEntity trace) {
        if (!asyncEnabled || queue == null) {
            try {
                llmTraceService.save(trace);
            } catch (Exception ex) {
                log.warn("LLM trace 落库失败", ex);
            }
            return;
        }
        if (!queue.offer(trace)) {
            dropped.incrementAndGet();
        }
    }

    /**
     * 定时线程里把缓冲清完：一批一批写。
     * <p>
     * 整个方法不允许抛出：它跑在 {@code scheduleWithFixedDelay} 上，一次未捕获异常（含 Error）
     * 会让周期任务从此不再调度，队列于是只进不出、静默全丢，且日志里一个字都不会有。
     * 写失败的批次原样塞回队列等下一轮（塞不回才计成丢弃）：一次网络抖动不该让整批观测数据蒸发。
     */
    private void flushQuietly() {
        try {
            flushBatches();
        } catch (Throwable t) {
            log.warn("llm_trace 冲刷任务异常（不影响后续调度）: {}", t.toString(), t);
        } finally {
            try {
                reportDropped();
            } catch (Exception ignored) {
                // 计数上报失败不值得把调度打断
            }
        }
    }

    private void flushBatches() {
        BlockingQueue<LlmTraceEntity> buffer = queue;
        if (buffer == null) {
            return;
        }
        int size = Math.max(1, batchSize);
        while (!buffer.isEmpty()) {
            List<LlmTraceEntity> batch = new ArrayList<>(size);
            buffer.drainTo(batch, size);
            if (batch.isEmpty()) {
                break;
            }
            try {
                llmTraceService.saveBatch(batch);
            } catch (Exception ex) {
                // 逆序塞回：正序塞回会因为队列是 FIFO 把这批重新排到队尾，与后面的新数据交错
                int requeued = 0;
                for (int i = batch.size() - 1; i >= 0; i--) {
                    if (buffer.offer(batch.get(i))) {
                        requeued++;
                    }
                }
                // 写失败按「批」计数：这一轮确实一次都没写进去；
                // 退不回来的那几条才是真丢了，跟缓冲满的丢弃合并计数
                writeFailures.incrementAndGet();
                if (requeued < batch.size()) {
                    dropped.addAndGet(batch.size() - requeued);
                }
                log.warn("llm_trace 批量落库失败，{} 条已退回缓冲等下一轮（{} 条无处可退回已丢弃）: {}",
                        requeued, batch.size() - requeued, ex.toString());
                // 库已经不行了，接着挖队列只会把失败放大成刷屏；下一轮定时再试
                break;
            }
        }
    }

    /** 丢弃只报增量：每轮重报同一个累计值会把日志顶成刷屏 */
    private void reportDropped() {
        long total = dropped.get();
        if (total > lastReportedDropped) {
            log.warn("llm_trace 缓冲已满，累计丢弃 {} 条（本轮新增 {} 条），"
                    + "持续出现请调大 async.queue-capacity 或降低 sample-rate",
                    total, total - lastReportedDropped);
            lastReportedDropped = total;
        }
    }

    /** 当前堆积条数（仅供监控与单测） */
    int queuedSize() {
        return queue == null ? 0 : queue.size();
    }

    /** 队列满丢掉的条数 */
    long droppedCount() {
        return dropped.get();
    }

    /** 批量写失败丢掉的条数 */
    long writeFailureCount() {
        return writeFailures.get();
    }

    /**
     * 首字延迟：只算流式调用（带了首个 token 时间点）且限在 [0, duration] 内。
     * <p>
     * 回调线程时序不保证单调，算出负数或比整段耗时还大的值只会把 P95 带歪，不如不给。
     */
    private static Long ttftOf(LlmTraceRecord record) {
        Long firstTokenMs = record.getFirstTokenMs();
        Long startMs = record.getStartTimeMs();
        if (firstTokenMs == null || startMs == null) {
            return null;
        }
        long ttft = firstTokenMs - startMs;
        if (ttft < 0) {
            return null;
        }
        Long duration = record.getDuration();
        return duration != null && ttft > duration ? duration : ttft;
    }

    /**
     * 按当时单价估算本次调用成本。
     * <p>
     * 写在调用时而不是看板查询时 join：单价会变，事后 join 只能拿到今天的价，历史成本会被重算；
     * 这一行存的是「当时花了多少钱」。
     */
    private BigDecimal estimateCost(LlmTraceRecord record) {
        if (pricingSource == null) {
            return null;
        }
        try {
            ModelPricing pricing = pricingSource.findPricing(record.getModelId());
            return pricing == null ? null : pricing.costOf(record.getInputTokens(), record.getOutputTokens());
        } catch (Exception ex) {
            // 算不出成本不该把整条 trace 一起丢掉，耗时 / token / 正文仍然有价值
            log.warn("成本估算失败: modelId={}, {}", record.getModelId(), ex.toString());
            return null;
        }
    }

    /**
     * 用 langchain4j 自带编解码器序列化请求消息。
     * <p>
     * 不能用通用 ObjectMapper：langchain4j 的消息类型没有 public getter，
     * 裸 Jackson 会抛 InvalidDefinitionException，导致整条 trace 静默丢失。
     */
    private String serializeMessages(List<ChatMessage> messages) {
        try {
            return ChatMessageSerializer.messagesToJson(messages);
        } catch (Exception ex) {
            log.warn("LLM trace 消息序列化失败，本次仅记录元信息: {}", ex.toString());
            return null;
        }
    }

    /**
     * 根据配置对内容做脱敏/截断处理
     */
    private String applyContentPolicy(String content) {
        if (content == null) return null;
        if (!storeContent) {
            // 不存完整内容，保留前 200 字符作为摘要
            return content.length() > 200 ? content.substring(0, 200) + "...[truncated]" : content;
        }
        if (maskEnabled) {
            content = maskSensitiveContent(content);
        }
        if (maxContentLength > 0 && content.length() > maxContentLength) {
            content = content.substring(0, maxContentLength) + "...[truncated]";
        }
        return content;
    }

    /**
     * 脱敏处理：替换敏感关键词
     */
    private String maskSensitiveContent(String content) {
        if (content == null) return null;
        return content
                // JSON 字段脱敏
                .replaceAll("\"(apiKey|api_key|apikey)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"******\"")
                .replaceAll("\"(password|passwd|secret|token|authorization)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"******\"")
                // 手机号脱敏
                .replaceAll("1[3-9]\\d{9}", "******")
                // 身份证脱敏
                .replaceAll("\\d{17}[\\dXx]", "******");
    }

    private String resolveTraceId(LlmTraceRecord record) {
        if (record.getTraceId() != null) return record.getTraceId();
        return TraceContext.getTraceId();
    }

    private String resolveSessionId(LlmTraceRecord record) {
        if (record.getSessionId() != null) return record.getSessionId();
        return TraceContext.getSessionId();
    }

    private String resolveUserId(LlmTraceRecord record) {
        if (record.getUserId() != null) return record.getUserId();
        return TraceContext.getUserId();
    }
}