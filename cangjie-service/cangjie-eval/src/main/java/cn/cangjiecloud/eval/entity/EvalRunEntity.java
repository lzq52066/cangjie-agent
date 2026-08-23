package cn.cangjiecloud.eval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
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
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 汇总指标（JSON：avgRecall / avgCorrectness / avgLatency / totalTokens） */
    private String summary;

    /** 逐 case 结果（JSON 数组，每条含 question / answer / retrievalSources / scores / latency） */
    private String results;

    /** 错误信息 */
    private String errorMessage;

    /** 进度（0-100） */
    private Integer progress;
}