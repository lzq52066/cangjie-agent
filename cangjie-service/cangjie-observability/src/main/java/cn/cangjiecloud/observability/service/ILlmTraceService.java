package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.observability.dto.LlmTraceQueryDTO;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * LLM 调用明细追踪服务
 */
public interface ILlmTraceService extends IService<LlmTraceEntity> {

    /**
     * 分页查询 LLM 调用追踪
     */
    IPage<LlmTraceEntity> pageQuery(LlmTraceQueryDTO query);

    /**
     * 查询单条详情
     */
    LlmTraceEntity getDetail(String id);

    /**
     * 按会话 ID 查询时间线（按时间升序）
     */
    List<LlmTraceEntity> timeline(String sessionId);
}