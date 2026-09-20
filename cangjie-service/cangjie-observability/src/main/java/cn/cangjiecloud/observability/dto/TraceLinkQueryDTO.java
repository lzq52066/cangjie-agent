package cn.cangjiecloud.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 链路聚合查询参数。
 * <p>
 * 链路 = 同一个 traceId 下的全部留痕（入口 span + Agent 执行 + 模型调用）。
 */
@Data
public class TraceLinkQueryDTO {

    /** 链路 ID，支持模糊匹配 */
    private String traceId;

    /**
     * 精确链路 ID：仅查单条链路时使用，让三个分支各自按 trace_id 等值裁剪命中索引。
     * 列表查询不使用该字段（孤立链路的 #kind#id 键由服务层在 Java 侧解析，不走这里）。
     */
    private String linkId;

    /**
     * 孤立链路键 {@code #kind#记录ID} 中的记录 ID：按各表主键裁剪，与 linkId 的 trace_id 裁剪互斥。
     */
    private String orphanId;

    private String sessionId;

    private String appId;

    private String userId;

    /** 聚合状态：success / fail / running / cancelled */
    private String status;

    /** 链路起始时间下限；为空时由服务按默认回溯窗口兜底，避免全表聚合 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
