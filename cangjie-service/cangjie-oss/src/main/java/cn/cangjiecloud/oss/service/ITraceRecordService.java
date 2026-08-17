package cn.cangjiecloud.oss.service;

import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.oss.api.dto.TraceQueryDTO;
import cn.cangjiecloud.oss.entity.TraceRecordEntity;
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
}
