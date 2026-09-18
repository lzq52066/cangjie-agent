package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.core.harness.AgentRunRecorder;
import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.ResumeState;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.observability.context.TraceContext;
import cn.cangjiecloud.observability.dto.AgentRunDetailVO;
import cn.cangjiecloud.observability.dto.AgentRunQueryDTO;
import cn.cangjiecloud.observability.entity.AgentApprovalEntity;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import cn.cangjiecloud.observability.mapper.AgentApprovalMapper;
import cn.cangjiecloud.observability.mapper.AgentRunMapper;
import cn.cangjiecloud.observability.mapper.AgentRunStepMapper;
import cn.cangjiecloud.observability.service.IAgentRunService;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Agent run 留痕实现。
 * <p>
 * 写入策略：run 主记录与检查点同步落库（必须保证挂起后可恢复），高频的步骤明细异步落库，
 * 任何留痕异常都只降级为日志，绝不打断对话主流程。
 */
@Slf4j
@Service
public class AgentRunServiceImpl extends ServiceImpl<AgentRunMapper, AgentRunEntity> implements IAgentRunService {

    private final AgentRunStepMapper stepMapper;
    private final AgentApprovalMapper approvalMapper;
    private final Executor businessExecutor;

    /** 步骤内容最大存储长度（0 表示不限制） */
    @Value("${cangjie.observability.agent-run.max-step-content-length:4000}")
    private int maxStepContentLength;

    /** 是否记录步骤明细 */
    @Value("${cangjie.observability.agent-run.record-steps:true}")
    private boolean recordSteps;

    /** 保留天数，<=0 表示不清理 */
    @Value("${cangjie.observability.agent-run.retention-days:30}")
    private int retentionDays;

    private static final int PURGE_BATCH_SIZE = 2000;

    public AgentRunServiceImpl(AgentRunStepMapper stepMapper,
                              AgentApprovalMapper approvalMapper,
                              @Qualifier("businessExecutor") Executor businessExecutor) {
        this.stepMapper = stepMapper;
        this.approvalMapper = approvalMapper;
        this.businessExecutor = businessExecutor;
    }

    // ==================== AgentRunRecorder SPI ====================

    @Override
    public String startRun(HarnessRequest request) {
        try {
            AgentRunEntity run = new AgentRunEntity();
            run.setTraceId(firstNonBlank(request.getTraceId(), TraceContext.getTraceId()));
            run.setSessionId(firstNonBlank(request.getSessionId(), TraceContext.getSessionId()));
            run.setUserId(firstNonBlank(request.getUserId(), TraceContext.getUserId()));
            run.setAppId(request.getApplicationId());
            run.setAppName(request.getApplicationName());
            run.setModelId(request.getModelId());
            run.setModelName(request.getModelName());
            run.setHarnessType(StringUtils.hasText(request.getHarnessType()) ? request.getHarnessType() : "chat");
            run.setParentRunId(request.getParentRunId());
            run.setDepth(request.getDepth());
            run.setStatus(RunStatus.RUNNING.value());
            run.setRounds(0);
            run.setToolCallCount(0);
            run.setInputTokens(0L);
            run.setOutputTokens(0L);
            run.setTotalTokens(0L);
            if (request.getConfig() != null) {
                run.setTokenBudget(request.getConfig().getRunTokenBudget());
            }
            run.setStartTime(LocalDateTime.now());
            save(run);
            request.setRunId(run.getId());
            return run.getId();
        } catch (Exception ex) {
            log.warn("agent_run 开启失败，降级为不留痕: {}", ex.getMessage());
            return request.getRunId() != null ? request.getRunId() : IdUtil.fastSimpleUUID();
        }
    }

    @Override
    public void recordContext(String runId, List<cn.cangjiecloud.core.harness.context.ContextFragment> fragments) {
        if (!StringUtils.hasText(runId) || fragments == null || fragments.isEmpty()) {
            return;
        }
        List<Map<String, Object>> usage = new ArrayList<>();
        for (var fragment : fragments) {
            usage.add(Map.of(
                    "slot", fragment.getSlot() == null ? "unknown" : fragment.getSlot().key(),
                    "source", fragment.getSource() == null ? "" : fragment.getSource(),
                    "messages", fragment.size(),
                    "estTokens", fragment.getEstTokens(),
                    "budgetTokens", fragment.getBudgetTokens(),
                    "droppedTokens", fragment.getDroppedTokens()));
        }
        businessExecutor.execute(() -> {
            try {
                update(new LambdaUpdateWrapper<AgentRunEntity>()
                        .eq(AgentRunEntity::getId, runId)
                        .set(AgentRunEntity::getContextUsage, JSON.toJSONString(usage)));
            } catch (Exception ex) {
                log.warn("agent_run 上下文留痕失败: {}", ex.getMessage());
            }
        });
    }

    @Override
    public void recordStep(StepRecord record) {
        if (!recordSteps || record == null || !StringUtils.hasText(record.getRunId())) {
            return;
        }
        businessExecutor.execute(() -> {
            try {
                stepMapper.insert(toEntity(record));
            } catch (Exception ex) {
                log.warn("agent_run_step 写入失败: {}", ex.getMessage());
            }
        });
    }

    @Override
    public String checkpoint(HarnessContext context, RunStatus status) {
        String token = IdUtil.fastSimpleUUID();
        try {
            ResumeState snapshot = context.snapshot();
            update(new LambdaUpdateWrapper<AgentRunEntity>()
                    .eq(AgentRunEntity::getId, context.getRunId())
                    .set(AgentRunEntity::getStatus, status.value())
                    .set(AgentRunEntity::getContextSnapshot, JSON.toJSONString(snapshot))
                    .set(AgentRunEntity::getResumeToken, token)
                    .set(AgentRunEntity::getRounds, snapshot.getRound())
                    .set(AgentRunEntity::getToolCallCount, snapshot.getToolCallCount())
                    .set(AgentRunEntity::getInputTokens, snapshot.getInputTokens())
                    .set(AgentRunEntity::getOutputTokens, snapshot.getOutputTokens())
                    .set(AgentRunEntity::getTotalTokens, snapshot.getTotalTokens()));
        } catch (Exception ex) {
            log.warn("agent_run 检查点写入失败，run 将无法恢复: {}", ex.getMessage());
            return null;
        }
        return token;
    }

    @Override
    public void finishRun(String runId, HarnessOutcome outcome) {
        if (!StringUtils.hasText(runId) || outcome == null) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<AgentRunEntity> wrapper = new LambdaUpdateWrapper<AgentRunEntity>()
                    .eq(AgentRunEntity::getId, runId)
                    .set(AgentRunEntity::getStatus, outcome.getStatus().value())
                    .set(AgentRunEntity::getRounds, outcome.getRounds())
                    .set(AgentRunEntity::getToolCallCount, outcome.getToolCallCount())
                    .set(AgentRunEntity::getInputTokens, outcome.getInputTokens())
                    .set(AgentRunEntity::getOutputTokens, outcome.getOutputTokens())
                    .set(AgentRunEntity::getTotalTokens, outcome.getTotalTokens())
                    .set(AgentRunEntity::getFinishReason, outcome.getFinishReason())
                    .set(AgentRunEntity::getFinalText, truncate(outcome.getFinalText(), maxStepContentLength))
                    .set(AgentRunEntity::getErrorMessage, outcome.getErrorMessage())
                    .set(AgentRunEntity::getDuration, outcome.getDurationMs())
                    .set(AgentRunEntity::getEndTime, now);
            // 非挂起终态清掉检查点，避免误恢复（审批挂起与本地工具挂起都需保留）
            if (outcome.getStatus() != RunStatus.WAITING_APPROVAL
                    && outcome.getStatus() != RunStatus.WAITING_LOCAL) {
                wrapper.set(AgentRunEntity::getContextSnapshot, null)
                        .set(AgentRunEntity::getResumeToken, null);
            }
            update(wrapper);
        } catch (Exception ex) {
            log.warn("agent_run 结束态写入失败: {}", ex.getMessage());
        }
    }

    @Override
    public boolean abandonWaitingRun(String runId, String finishReason, String errorMessage) {
        if (!StringUtils.hasText(runId)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AgentRunEntity> wrapper = new LambdaUpdateWrapper<AgentRunEntity>()
                .eq(AgentRunEntity::getId, runId)
                .in(AgentRunEntity::getStatus,
                        RunStatus.WAITING_APPROVAL.value(), RunStatus.WAITING_LOCAL.value())
                .set(AgentRunEntity::getStatus, RunStatus.FAILED.value())
                .set(AgentRunEntity::getFinishReason, finishReason)
                .set(AgentRunEntity::getErrorMessage, errorMessage)
                .set(AgentRunEntity::getEndTime, now)
                // 清掉检查点与令牌，run 不再可恢复
                .set(AgentRunEntity::getContextSnapshot, null)
                .set(AgentRunEntity::getResumeToken, null);
        // 挂起时的统计已随检查点落库，这里只补耗时（从原始启动时刻起算）
        AgentRunEntity run = getById(runId);
        if (run != null && run.getStartTime() != null) {
            wrapper.set(AgentRunEntity::getDuration, Duration.between(run.getStartTime(), now).toMillis());
        }
        return update(wrapper);
    }

    @Override
    public PausedRun loadPaused(String runId, String resumeToken) {
        AgentRunEntity run = getById(runId);
        if (run == null || !StringUtils.hasText(run.getContextSnapshot())) {
            return null;
        }
        boolean suspended = RunStatus.WAITING_APPROVAL.value().equals(run.getStatus())
                || RunStatus.WAITING_LOCAL.value().equals(run.getStatus());
        if (!suspended) {
            return null;
        }
        if (!StringUtils.hasText(run.getResumeToken()) || !run.getResumeToken().equals(resumeToken)) {
            log.warn("agent_run 恢复令牌校验失败: runId={}", runId);
            return null;
        }
        ResumeState state = JSON.parseObject(run.getContextSnapshot(), ResumeState.class);
        if (state == null) {
            return null;
        }
        // 令牌一次性消费：先置空再执行，重复调用会被上面的校验挡掉
        update(new LambdaUpdateWrapper<AgentRunEntity>()
                .eq(AgentRunEntity::getId, runId)
                .set(AgentRunEntity::getResumeToken, null)
                .set(AgentRunEntity::getContextSnapshot, null)
                .set(AgentRunEntity::getStatus, RunStatus.RUNNING.value()));
        AgentApprovalEntity approval = approvalMapper.selectOne(new LambdaQueryWrapper<AgentApprovalEntity>()
                .eq(AgentApprovalEntity::getRunId, runId)
                .in(AgentApprovalEntity::getStatus, "approved", "rejected")
                .orderByDesc(AgentApprovalEntity::getDecideTime)
                .last("LIMIT 1"));
        return PausedRun.builder()
                .runId(runId)
                .status(run.getStatus())
                .state(state)
                .approvalId(approval == null ? null : approval.getId())
                .traceId(run.getTraceId())
                .applicationId(run.getAppId())
                .applicationName(run.getAppName())
                .sessionId(run.getSessionId())
                .userId(run.getUserId())
                .modelId(run.getModelId())
                .modelName(run.getModelName())
                .harnessType(run.getHarnessType())
                .parentRunId(run.getParentRunId())
                .depth(run.getDepth() == null ? 0 : run.getDepth())
                .build();
    }

    // ==================== 查询 ====================

    @Override
    public IPage<AgentRunEntity> pageQuery(AgentRunQueryDTO query) {
        LambdaQueryWrapper<AgentRunEntity> wrapper = new LambdaQueryWrapper<AgentRunEntity>()
                .like(StringUtils.hasText(query.getTraceId()), AgentRunEntity::getTraceId, query.getTraceId())
                .eq(StringUtils.hasText(query.getSessionId()), AgentRunEntity::getSessionId, query.getSessionId())
                .eq(StringUtils.hasText(query.getAppId()), AgentRunEntity::getAppId, query.getAppId())
                .eq(StringUtils.hasText(query.getUserId()), AgentRunEntity::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getStatus()), AgentRunEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getHarnessType()), AgentRunEntity::getHarnessType, query.getHarnessType())
                .eq(StringUtils.hasText(query.getParentRunId()), AgentRunEntity::getParentRunId, query.getParentRunId())
                .like(StringUtils.hasText(query.getModelName()), AgentRunEntity::getModelName, query.getModelName())
                .ge(query.getStartTime() != null, AgentRunEntity::getStartTime, query.getStartTime())
                .le(query.getEndTime() != null, AgentRunEntity::getStartTime, query.getEndTime())
                .orderByDesc(AgentRunEntity::getStartTime);
        return page(new Page<>(query.getPageNum() == null ? 1 : query.getPageNum(),
                query.getPageSize() == null ? 10 : query.getPageSize()), wrapper);
    }

    @Override
    public AgentRunDetailVO detail(String runId) {
        AgentRunEntity run = getById(runId);
        if (run == null) {
            return null;
        }
        AgentRunDetailVO vo = new AgentRunDetailVO();
        vo.setRun(run);
        vo.setSteps(listSteps(runId));
        vo.setApprovals(approvalMapper.selectList(new LambdaQueryWrapper<AgentApprovalEntity>()
                .eq(AgentApprovalEntity::getRunId, runId)
                .orderByAsc(AgentApprovalEntity::getCreateTime)));
        vo.setChildren(list(new LambdaQueryWrapper<AgentRunEntity>()
                .eq(AgentRunEntity::getParentRunId, runId)
                .orderByAsc(AgentRunEntity::getStartTime)));
        return vo;
    }

    @Override
    public List<AgentRunStepEntity> listSteps(String runId) {
        return stepMapper.selectList(new LambdaQueryWrapper<AgentRunStepEntity>()
                .eq(AgentRunStepEntity::getRunId, runId)
                .orderByAsc(AgentRunStepEntity::getStepNo));
    }

    @Override
    public List<AgentRunEntity> listBySession(String sessionId) {
        return list(new LambdaQueryWrapper<AgentRunEntity>()
                .eq(AgentRunEntity::getSessionId, sessionId)
                .orderByAsc(AgentRunEntity::getStartTime));
    }

    /**
     * 保留期清理：每天 03:40 分批物理删除超期 run 与步骤
     */
    @Scheduled(cron = "0 40 3 * * ?")
    public void scheduledPurge() {
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int steps = 0;
        int removed;
        do {
            removed = stepMapper.purgeBatch(cutoff, PURGE_BATCH_SIZE);
            steps += removed;
        } while (removed == PURGE_BATCH_SIZE);
        int runs = 0;
        do {
            removed = baseMapper.purgeBatch(cutoff, PURGE_BATCH_SIZE);
            runs += removed;
        } while (removed == PURGE_BATCH_SIZE);
        if (steps > 0 || runs > 0) {
            log.info("agent_run 保留期清理完成，删除 step {} 条 / run {} 条", steps, runs);
        }
    }

    private AgentRunStepEntity toEntity(StepRecord record) {
        AgentRunStepEntity entity = new AgentRunStepEntity();
        entity.setRunId(record.getRunId());
        entity.setStepNo(record.getStepNo());
        entity.setRound(record.getRound());
        entity.setType(record.getType());
        entity.setName(record.getName());
        entity.setStatus(record.getStatus());
        entity.setInput(truncate(record.getInput(), maxStepContentLength));
        entity.setOutput(truncate(record.getOutput(), maxStepContentLength));
        entity.setErrorMessage(record.getErrorMessage());
        entity.setInputTokens(record.getInputTokens());
        entity.setOutputTokens(record.getOutputTokens());
        entity.setDuration(record.getDurationMs());
        entity.setTruncated(record.isTruncated() || isOverLength(record.getOutput()) ? 1 : 0);
        entity.setStartTime(toDateTime(record.getStartMs()));
        entity.setEndTime(toDateTime(record.getEndMs()));
        return entity;
    }

    private LocalDateTime toDateTime(Long epochMilli) {
        if (epochMilli == null || epochMilli <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    private boolean isOverLength(String content) {
        return maxStepContentLength > 0 && content != null && content.length() > maxStepContentLength;
    }

    private String truncate(String content, int maxLength) {
        if (content == null) {
            return null;
        }
        if (maxLength > 0 && content.length() > maxLength) {
            return content.substring(0, maxLength) + "...[truncated]";
        }
        return content;
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }
}
