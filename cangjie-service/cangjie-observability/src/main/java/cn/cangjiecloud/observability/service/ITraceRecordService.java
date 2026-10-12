package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.observability.dto.TraceQueryDTO;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 调用追踪服务，同时作为 TraceCollector 的数据库实现
 */
public interface ITraceRecordService extends IService<TraceRecordEntity>, TraceCollector {

    /**
     * 记录一条追踪
     */
    void record(TraceRecordEntity entity);

    /**
     * 分页查询追踪记录
     */
    IPage<TraceRecordEntity> pageQuery(TraceQueryDTO query);

    /**
     * 按 spanId 取单条 span 的完整载荷（input / output / metadata）。
     * <p>
     * 链路详情为控制响应体只返精简列，正文在这里按需单条拉取（与 LLM trace 正文同一套约定）。
     */
    TraceRecordEntity payload(String spanId);
}