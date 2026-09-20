package cn.cangjiecloud.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条链路的聚合视图：trace_record + llm_trace + agent_run 按 traceId 归并后的单行摘要。
 */
@Data
public class TraceLinkVO {

    /** 链路 ID；无 traceId 的孤立留痕用 {@code #kind#记录ID} 占位，展开时按该键回查单条 */
    private String traceId;

    /** 入口类型：span（请求级 span）/ run（Agent 执行）/ llm（无归属的模型调用） */
    private String entryKind;

    /** 入口标签：span 为 module/action，run 为 harness_type，llm 为模型名 */
    private String entryLabel;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 链路内最晚结束的节点时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 端到端耗时：取链路内最长的一段（正常即入口 span） */
    private Long duration;

    private String appId;

    private String appName;

    private String sessionId;

    private String userId;

    private String modelName;

    /** Agent 执行次数（含子 Agent） */
    private Integer runCount;

    private Integer llmCallCount;

    private Integer spanCount;

    private Long totalTokens;

    /** success / fail / running / cancelled */
    private String status;

    /** 首个失败节点的错误信息 */
    private String errorMessage;
}
