package cn.cangjiecloud.eval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 评估运行实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "eval_run", autoResultMap = true)
public class EvalRunEntity extends BaseEntity {

    /** 数据集 ID */
    private String datasetId;

    /** 运行配置快照（JSON：modelId / rerank / topK） */
    private String configSnapshot;

    /** 状态：pending / running / completed / failed */
    private String status;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 汇总指标（JSON：avgRecall / avgCorrectness / avgLatency / totalTokens，另带两个指标的出分样本数） */
    private String summary;

    /**
     * 逐 case 结果（已拆到 eval_result 明细表，仅历史运行保留这一坨 JSON）
     */
    private String results;

    /** 错误信息 */
    private String errorMessage;

    /** 进度（0-100） */
    private Integer progress;
}