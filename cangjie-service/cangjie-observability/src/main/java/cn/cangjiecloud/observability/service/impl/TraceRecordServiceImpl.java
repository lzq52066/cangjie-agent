package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.dto.TraceQueryDTO;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import cn.cangjiecloud.observability.mapper.TraceRecordMapper;
import cn.cangjiecloud.observability.service.ITraceRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceRecordServiceImpl extends ServiceImpl<TraceRecordMapper, TraceRecordEntity>
        implements ITraceRecordService {

    @Autowired
    @Qualifier("businessExecutor")
    private Executor businessExecutor;

    @Override
    public void record(TraceRecordEntity entity) {
        if (entity == null) {
            return;
        }
        // 异步独立落库：span 不能跟着业务事务一起回滚，否则失败调用反而查不到
        businessExecutor.execute(() -> {
            try {
                save(entity);
            } catch (Exception ex) {
                log.warn("trace_record 写入失败: module={}, action={}, {}",
                        entity.getModule(), entity.getAction(), ex.getMessage());
            }
        });
    }

    @Override
    public IPage<TraceRecordEntity> pageQuery(TraceQueryDTO query) {
        LambdaQueryWrapper<TraceRecordEntity> wrapper = new LambdaQueryWrapper<TraceRecordEntity>()
                .eq(StringUtils.hasText(query.getTraceId()), TraceRecordEntity::getTraceId, query.getTraceId())
                .eq(StringUtils.hasText(query.getModule()), TraceRecordEntity::getModule, query.getModule())
                .eq(StringUtils.hasText(query.getAction()), TraceRecordEntity::getAction, query.getAction())
                .ge(query.getStartTime() != null, TraceRecordEntity::getStartTime, query.getStartTime())
                .le(query.getEndTime() != null, TraceRecordEntity::getStartTime, query.getEndTime())
                .orderByDesc(TraceRecordEntity::getStartTime);
        return page(new Page<>(query.getPageNum() == null ? 1 : query.getPageNum(),
                query.getPageSize() == null ? 10 : query.getPageSize()), wrapper);
    }

    /**
     * TraceCollector 实现：将子 span 写入 trace_record 表，供 AOP / 手动调用。
     * <p>
     * 父子关系靠「入口 span 的 spanId == traceId」这一约定还原，无需额外的线程上下文：
     * 同链路内的子 span 直接把 parentSpanId 指回 traceId 即可挂到入口 span 下。
     */
    @Override
    public void record(String module, String action, String traceId, long duration, String status, String message) {
        TraceRecordEntity entity = buildSpan(module, action, traceId, duration, status, message);
        entity.setSpanId(UUID.randomUUID().toString().replace("-", ""));
        entity.setParentSpanId(traceId);
        record(entity);
    }

    @Override
    public void recordEntry(String module, String action, String traceId, long duration,
                            String status, String message) {
        TraceRecordEntity entity = buildSpan(module, action, traceId, duration, status, message);
        // 入口 span 用 traceId 自身作 spanId：子 span 只带着 traceId 也能定位到父节点
        entity.setSpanId(StringUtils.hasText(traceId) ? traceId : UUID.randomUUID().toString().replace("-", ""));
        record(entity);
    }

    private TraceRecordEntity buildSpan(String module, String action, String traceId, long duration,
                                        String status, String message) {
        TraceRecordEntity entity = new TraceRecordEntity();
        entity.setTraceId(traceId);
        entity.setModule(module);
        entity.setAction(action);
        entity.setDuration(duration);
        entity.setStatus(status);
        entity.setMessage(message);
        entity.setServiceName("cangjie-agent");
        entity.setStartTime(LocalDateTime.now());
        return entity;
    }
}