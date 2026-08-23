package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.observability.entity.SystemMetricEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统指标服务
 */
public interface ISystemMetricService extends IService<SystemMetricEntity> {

    /**
     * 手动采集一次 JVM 指标，返回采集到的指标列表
     */
    List<SystemMetricEntity> collect();

    /**
     * 分页查询系统指标（支持时间范围筛选）
     */
    IPage<SystemMetricEntity> pageQuery(String metricType, LocalDateTime startTime, LocalDateTime endTime, Integer pageNum, Integer pageSize);
}