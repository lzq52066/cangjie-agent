package cn.cangjiecloud.oss.service.impl;

import cn.cangjiecloud.oss.api.dto.TraceQueryDTO;
import cn.cangjiecloud.oss.entity.TraceRecordEntity;
import cn.cangjiecloud.oss.mapper.TraceRecordMapper;
import cn.cangjiecloud.oss.service.ITraceRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceRecordServiceImpl extends ServiceImpl<TraceRecordMapper, TraceRecordEntity>
        implements ITraceRecordService {

    @Override
    public void record(TraceRecordEntity entity) {
        if (entity == null) {
            return;
        }
        save(entity);
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
     * TraceCollector 实现：将 span 信息写入 trace_record 表，供 AOP / 手动调用
     */
    @Override
    public void record(String module, String action, String traceId, long duration, String status, String message) {
        TraceRecordEntity entity = new TraceRecordEntity();
        entity.setTraceId(traceId);
        entity.setModule(module);
        entity.setAction(action);
        entity.setSpanId(UUID.randomUUID().toString().replace("-", ""));
        entity.setDuration(duration);
        entity.setStatus(status);
        entity.setMessage(message);
        entity.setServiceName("cangjie-agent");
        entity.setStartTime(LocalDateTime.now());
        record(entity);
    }
}
