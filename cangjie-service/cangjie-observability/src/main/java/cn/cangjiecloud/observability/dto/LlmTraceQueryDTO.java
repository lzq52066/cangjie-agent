package cn.cangjiecloud.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * LLM 调用追踪查询参数
 */
@Data
public class LlmTraceQueryDTO {

    /** 链路 ID */
    private String traceId;

    /** OpenAI request ID */
    private String requestId;

    /** 应用 ID */
    private String appId;

    /** 应用名称 */
    private String appName;

    /** 会话 ID */
    private String sessionId;

    /** 用户 ID */
    private String userId;

    /** 模型 ID */
    private String modelId;

    /** 模型名称 */
    private String modelName;

    /** 状态：success / fail */
    private String status;

    /** 开始时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页数量 */
    private Integer pageSize = 10;
}