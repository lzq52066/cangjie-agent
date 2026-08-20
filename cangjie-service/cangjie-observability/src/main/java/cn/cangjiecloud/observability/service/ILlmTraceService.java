package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * LLM 调用明细追踪服务
 */
public interface ILlmTraceService extends IService<LlmTraceEntity> {

    /**
     * 分页查询 LLM 调用追踪
     */
    IPage<LlmTraceEntity> pageQuery(LlmTraceQueryDTO query);
}