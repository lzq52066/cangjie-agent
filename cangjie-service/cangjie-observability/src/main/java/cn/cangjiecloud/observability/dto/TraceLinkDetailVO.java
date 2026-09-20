package cn.cangjiecloud.observability.dto;

import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import lombok.Data;

import java.util.List;

/**
 * 一条链路的完整明细：概览 + 请求级 span + 各次 Agent 执行（含步骤与模型调用）+ 无 run 归属的模型调用。
 * <p>
 * 三段数据原本分属「调用追踪」「LLM 调用」「Agent 执行」三个页面，这里按 traceId 合成一个视图。
 */
@Data
public class TraceLinkDetailVO {

    private TraceLinkVO summary;

    /** 链路内的全部 span，按开始时间升序；spanId == traceId 者为入口 span */
    private List<TraceRecordEntity> spans;

    /** 链路内的全部 Agent 执行，按开始时间升序；层级由 run.parentRunId / depth 还原，故不重复带 children */
    private List<AgentRunDetailVO> runs;

    /** 挂在本链路下但不属于任何 Agent 执行的模型调用（会话摘要、记忆合并等），精简列不含正文 */
    private List<LlmTraceEntity> backgroundLlmTraces;
}
