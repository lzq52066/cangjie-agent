package cn.cangjiecloud.oss.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统指标实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "system_metric", autoResultMap = true)
public class SystemMetricEntity extends BaseEntity {

    /** 指标类型：cpu / memory / thread / disk / gc */
    private String metricType;

    /** 指标名称 */
    private String metricName;

    /** 指标值 */
    private Double metricValue;

    /** 单位 */
    private String unit;

    /** 主机名 */
    private String host;

    /** 采集时间 */
    private LocalDateTime collectTime;
}
