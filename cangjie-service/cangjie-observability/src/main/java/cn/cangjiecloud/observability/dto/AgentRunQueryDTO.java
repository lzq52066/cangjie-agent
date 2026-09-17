package cn.cangjiecloud.observability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Agent run 查询参数
 */
@Data
public class AgentRunQueryDTO {

    private String traceId;

    private String sessionId;

    private String appId;

    private String userId;

    /** running / completed / failed / cancelled / waiting_approval */
    private String status;

    /** chat / workflow / subagent / debug */
    private String harnessType;

    private String parentRunId;

    private String modelName;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
