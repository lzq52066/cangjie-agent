package cn.cangjiecloud.observability.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调用追踪记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "trace_record", autoResultMap = true)
public class TraceRecordEntity extends BaseEntity {

    /**
     * 载荷列：单条可达数十 KB，列表与链路详情一律不选，
     * 正文由 {@code ITraceRecordService#payload(String)} 按 spanId 单条拉取。
     */
    public static final List<String> PAYLOAD_COLUMNS = List.of("input_payload", "output_payload", "metadata");

    /** 链路 ID */
    private String traceId;

    /** 模块 */
    private String module;

    /** 动作 */
    private String action;

    /** Span ID */
    private String spanId;

    /** 父 Span ID */
    private String parentSpanId;

    /** 耗时（毫秒） */
    private Long duration;

    /** 状态：success / fail */
    private String status;

    /** 消息 */
    private String message;

    /** 服务名 */
    private String serviceName;

    /** 节点输入载荷（JSON），如检索的 query / 改写词 / topK */
    private String inputPayload;

    /** 节点输出载荷（JSON），如召回片段的各通道分数与名次 */
    private String outputPayload;

    /** 节点附加维度（JSON），如候选数、命中数 */
    private String metadata;

    /** 开始时间 */
    private LocalDateTime startTime;
}