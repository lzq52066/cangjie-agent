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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final JdbcTemplate jdbcTemplate;

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
     * 系统指标分页
     */
    @GetMapping("/metrics")
    public R<IPage<SystemMetricEntity>> metrics(@RequestParam(required = false) String metricType,
                                                @RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(systemMetricService.pageQuery(metricType, pageNum, pageSize));
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
     * 汇总面板数据（今日对话数、模型调用数、错误数、平均耗时等）
     */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> data = new LinkedHashMap<>();

        // 今日对话数（user 消息数）
        Long todayChatCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM chat_message WHERE role = 'user' AND deleted = 0 AND create_time >= ?",
                Long.class, todayStart);
        // 今日模型调用数（assistant 消息数）
        Long todayModelCallCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM chat_message WHERE role = 'assistant' AND deleted = 0 AND create_time >= ?",
                Long.class, todayStart);
        // 今日错误数
        Long todayErrorCount = operationLogService.count(new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getStatus, "fail")
                .ge(OperationLogEntity::getCreateTime, todayStart));
        // 操作平均耗时（毫秒）
        Double avgDuration = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(duration), 0) FROM operation_log WHERE deleted = 0", Double.class);
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