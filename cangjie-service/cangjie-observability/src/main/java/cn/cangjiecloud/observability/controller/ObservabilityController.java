package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.observability.dto.TraceQueryDTO;
import cn.cangjiecloud.observability.entity.OperationLogEntity;
import cn.cangjiecloud.observability.entity.SystemMetricEntity;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import cn.cangjiecloud.observability.service.IOperationLogService;
import cn.cangjiecloud.observability.service.ISystemMetricService;
import cn.cangjiecloud.observability.service.ITraceRecordService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可观测性接口：操作日志 / 系统指标 / 调用追踪 / 汇总面板
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/observability")
public class ObservabilityController {

    private final IOperationLogService operationLogService;
    private final ISystemMetricService systemMetricService;
    private final ITraceRecordService traceRecordService;
    private final cn.cangjiecloud.observability.mapper.ObservabilityStatsMapper observabilityStatsMapper;

    /**
     * 操作日志分页
     */
    @GetMapping("/logs")
    public R<IPage<OperationLogEntity>> logs(@RequestParam(required = false) String module,
                                             @RequestParam(required = false) String action,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(operationLogService.pageQuery(module, action, status, pageNum, pageSize));
    }

    /**
     * 系统指标分页（支持时间范围筛选）
     */
    @GetMapping("/metrics")
    public R<IPage<SystemMetricEntity>> metrics(
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(systemMetricService.pageQuery(metricType, startTime, endTime, pageNum, pageSize));
    }

    /**
     * 手动采集一次系统指标
     */
    @GetMapping("/metrics/collect")
    public R<List<SystemMetricEntity>> collect() {
        return R.data(systemMetricService.collect());
    }

    /**
     * 调用追踪分页
     */
    @GetMapping("/traces")
    public R<IPage<TraceRecordEntity>> traces(TraceQueryDTO query) {
        return R.data(traceRecordService.pageQuery(query));
    }

    /**
     * 系统指标图表数据：支持时间范围过滤，按 metricType 分组多 series 返回
     */
    @GetMapping("/metrics/chart")
    public R<Map<String, Object>> chart(
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        if (startTime == null) startTime = LocalDateTime.now().minusHours(2);
        if (endTime == null) endTime = LocalDateTime.now();

        List<SystemMetricEntity> rows = systemMetricService.list(
                new LambdaQueryWrapper<SystemMetricEntity>()
                        .eq(StringUtils.hasText(metricType), SystemMetricEntity::getMetricType, metricType)
                        .ge(SystemMetricEntity::getCollectTime, startTime)
                        .le(SystemMetricEntity::getCollectTime, endTime)
                        .orderByAsc(SystemMetricEntity::getCollectTime));

        // 按 metricType 分组，每组下多个 series（按 metricName）
        Map<String, Map<String, Object>> typeGroups = new LinkedHashMap<>();
        for (SystemMetricEntity row : rows) {
            String type = row.getMetricType();
            String name = row.getMetricName();
            String unit = row.getUnit();

            Map<String, Object> typeGroup = typeGroups.computeIfAbsent(type, k -> {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("metricType", type);
                g.put("series", new ArrayList<Map<String, Object>>());
                return g;
            });

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> seriesList = (List<Map<String, Object>>) typeGroup.get("series");

            // 找到或创建对应 metricName 的 series
            Map<String, Object> seriesObj = null;
            for (Map<String, Object> s : seriesList) {
                if (name.equals(s.get("name"))) {
                    seriesObj = s;
                    break;
                }
            }
            if (seriesObj == null) {
                seriesObj = new LinkedHashMap<>();
                seriesObj.put("name", name);
                seriesObj.put("unit", unit);
                seriesObj.put("data", new ArrayList<Map<String, Object>>());
                seriesList.add(seriesObj);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) seriesObj.get("data");
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", row.getCollectTime() != null ? row.getCollectTime().toString() : "");
            point.put("value", row.getMetricValue());
            data.add(point);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groups", new ArrayList<>(typeGroups.values()));
        return R.data(result);
    }

    /**
     * 汇总面板数据（今日对话数、模型调用数、错误数、平均耗时等）
     */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> data = new LinkedHashMap<>();

        // 今日对话数（user 消息数）
        Long todayChatCount = observabilityStatsMapper.countUserMessagesSince(todayStart);
        // 今日模型调用数（assistant 消息数）
        Long todayModelCallCount = observabilityStatsMapper.countAssistantMessagesSince(todayStart);
        // 今日错误数
        Long todayErrorCount = operationLogService.count(new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getStatus, "fail")
                .ge(OperationLogEntity::getCreateTime, todayStart));
        // 操作平均耗时（毫秒）
        Double avgDuration = observabilityStatsMapper.avgOperationDuration();
        // 今日操作日志数
        Long todayLogCount = operationLogService.count(new LambdaQueryWrapper<OperationLogEntity>()
                .ge(OperationLogEntity::getCreateTime, todayStart));
        // 今日调用追踪数
        Long todayTraceCount = traceRecordService.count(new LambdaQueryWrapper<TraceRecordEntity>()
                .ge(TraceRecordEntity::getStartTime, todayStart));

        data.put("todayChatCount", todayChatCount);
        data.put("todayModelCallCount", todayModelCallCount);
        data.put("todayErrorCount", todayErrorCount);
        data.put("avgDuration", avgDuration);
        data.put("todayLogCount", todayLogCount);
        data.put("todayTraceCount", todayTraceCount);
        return R.data(data);
    }
}