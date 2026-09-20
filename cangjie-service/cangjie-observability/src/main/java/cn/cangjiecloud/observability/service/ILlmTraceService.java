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

    /**
     * 查询某次 Agent 执行名下的全部模型调用（按开始时间升序）。
     * <p>
     * 一次 run 的多轮、重试与供应商降级都会各产生一条记录。为避免详情响应被大字段撑爆，
     * 这里不返回 promptContent / responseContent，正文由调用方按需调 {@link #getDetail(String)} 单条拉取。
     */
    List<LlmTraceEntity> listByRun(String runId);
}