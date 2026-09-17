package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.observability.entity.AgentApprovalEntity;
import cn.cangjiecloud.observability.mapper.AgentApprovalMapper;
import cn.cangjiecloud.observability.service.IAgentApprovalService;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 审批单存储实现。
 * <p>
 * 决策使用"仅当 status=pending 时更新"的条件语句保证幂等，避免并发点击同时通过和拒绝。
 */
@Slf4j
@Service
public class AgentApprovalServiceImpl extends ServiceImpl<AgentApprovalMapper, AgentApprovalEntity>
        implements IAgentApprovalService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_EXPIRED = "expired";

    /** 审批默认有效期（秒），仅当引擎未写入过期时间时兜底，与 cangjie.harness.approval-timeout-seconds 同键 */
    @Value("${cangjie.harness.approval-timeout-seconds:1800}")
    private int defaultTimeoutSeconds;

    @Override
    public ApprovalRequest create(ApprovalRequest request) {
        try {
            if (!StringUtils.hasText(request.getResumeToken())) {
                request.setResumeToken(IdUtil.fastSimpleUUID());
            }
            if (request.getExpireAt() <= 0) {
                request.setExpireAt(System.currentTimeMillis() + defaultTimeoutSeconds * 1000L);
            }
            AgentApprovalEntity entity = new AgentApprovalEntity();
            entity.setId(StringUtils.hasText(request.getApprovalId()) ? request.getApprovalId() : null);
            entity.setRunId(request.getRunId());
            entity.setStepId(request.getStepId());
            entity.setSessionId(request.getSessionId());
            entity.setAppId(request.getApplicationId());
            entity.setToolName(request.getToolName());
            entity.setToolType(request.getToolType());
            entity.setArguments(request.getArguments());
            entity.setReason(request.getReason());
            entity.setRiskLevel(request.getRiskLevel());
            entity.setStatus(STATUS_PENDING);
            entity.setResumeToken(request.getResumeToken());
            entity.setExpireTime(toDateTime(request.getExpireAt()));
            save(entity);
            request.setApprovalId(entity.getId());
            return request;
        } catch (Exception ex) {
            log.warn("审批单创建失败: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public ApprovalRequest find(String approvalId) {
        return toRequest(getById(approvalId));
    }

    @Override
    public boolean decide(String approvalId, String status, String decidedBy, String remark) {
        String target = normalizeDecision(status);
        if (target == null) {
            return false;
        }
        return update(new LambdaUpdateWrapper<AgentApprovalEntity>()
                .eq(AgentApprovalEntity::getId, approvalId)
                .eq(AgentApprovalEntity::getStatus, STATUS_PENDING)
                .set(AgentApprovalEntity::getStatus, target)
                .set(AgentApprovalEntity::getDecidedBy, decidedBy)
                .set(AgentApprovalEntity::getDecideRemark, remark)
                .set(AgentApprovalEntity::getDecideTime, LocalDateTime.now()));
    }

    @Override
    public List<String> expireOverdue(String defaultDecision) {
        List<AgentApprovalEntity> overdue = list(new LambdaQueryWrapper<AgentApprovalEntity>()
                .eq(AgentApprovalEntity::getStatus, STATUS_PENDING)
                .isNotNull(AgentApprovalEntity::getExpireTime)
                .lt(AgentApprovalEntity::getExpireTime, LocalDateTime.now()));
        if (overdue.isEmpty()) {
            return List.of();
        }
        String target = normalizeDecision(defaultDecision) == null
                ? STATUS_REJECTED : normalizeDecision(defaultDecision);
        List<String> runIds = new ArrayList<>();
        for (AgentApprovalEntity entity : overdue) {
            boolean flipped = update(new LambdaUpdateWrapper<AgentApprovalEntity>()
                    .eq(AgentApprovalEntity::getId, entity.getId())
                    .eq(AgentApprovalEntity::getStatus, STATUS_PENDING)
                    .set(AgentApprovalEntity::getStatus, STATUS_EXPIRED)
                    .set(AgentApprovalEntity::getDecideTime, LocalDateTime.now())
                    .set(AgentApprovalEntity::getDecideRemark, "审批超时未决策，运行已终止（默认决策留痕：" + target + "）"));
            if (flipped) {
                runIds.add(entity.getRunId());
            }
        }
        return runIds;
    }

    @Override
    public ApprovalRequest findPendingByRun(String runId) {
        AgentApprovalEntity entity = getOne(new LambdaQueryWrapper<AgentApprovalEntity>()
                .eq(AgentApprovalEntity::getRunId, runId)
                .eq(AgentApprovalEntity::getStatus, STATUS_PENDING)
                .orderByDesc(AgentApprovalEntity::getCreateTime)
                .last("LIMIT 1"));
        return toRequest(entity);
    }

    @Override
    public ApprovalRequest findPendingBySession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        // 扫描任务按周期置 expired，两次扫描之间存在"已超时但仍 pending"的窗口，
        // 查询时直接按 expire_time 排除，避免前端恢复出一个必然失败的卡片
        AgentApprovalEntity entity = getOne(new LambdaQueryWrapper<AgentApprovalEntity>()
                .eq(AgentApprovalEntity::getSessionId, sessionId)
                .eq(AgentApprovalEntity::getStatus, STATUS_PENDING)
                .and(w -> w.isNull(AgentApprovalEntity::getExpireTime)
                        .or().gt(AgentApprovalEntity::getExpireTime, LocalDateTime.now()))
                .orderByDesc(AgentApprovalEntity::getCreateTime)
                .last("LIMIT 1"));
        return toRequest(entity);
    }

    @Override
    public IPage<AgentApprovalEntity> pageQuery(String status, String appId, String userId,
                                                Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<AgentApprovalEntity> wrapper = new LambdaQueryWrapper<AgentApprovalEntity>()
                .eq(StringUtils.hasText(status), AgentApprovalEntity::getStatus, status)
                .eq(StringUtils.hasText(appId), AgentApprovalEntity::getAppId, appId)
                .eq(StringUtils.hasText(userId), AgentApprovalEntity::getUserId, userId)
                .orderByDesc(AgentApprovalEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    /**
     * 决策值归一：approve/approved -> approved，reject/rejected/deny -> rejected
     */
    private String normalizeDecision(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String value = status.trim().toLowerCase();
        if (value.startsWith("appro") || "accept".equals(value) || "true".equals(value)) {
            return STATUS_APPROVED;
        }
        if (value.startsWith("reject") || value.startsWith("deny") || "false".equals(value)) {
            return STATUS_REJECTED;
        }
        return null;
    }

    private ApprovalRequest toRequest(AgentApprovalEntity entity) {
        if (entity == null) {
            return null;
        }
        return ApprovalRequest.builder()
                .approvalId(entity.getId())
                .runId(entity.getRunId())
                .stepId(entity.getStepId())
                .sessionId(entity.getSessionId())
                .applicationId(entity.getAppId())
                .toolName(entity.getToolName())
                .toolType(entity.getToolType())
                .arguments(entity.getArguments())
                .reason(entity.getReason())
                .riskLevel(entity.getRiskLevel())
                .resumeToken(entity.getResumeToken())
                .expireAt(entity.getExpireTime() == null ? 0
                        : entity.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
    }

    private LocalDateTime toDateTime(long epochMilli) {
        if (epochMilli <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }
}
