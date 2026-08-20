package cn.cangjiecloud.observability.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 调用追踪记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "trace_record", autoResultMap = true)
public class TraceRecordEntity extends BaseEntity {

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

    /** 开始时间 */
    private LocalDateTime startTime;
}