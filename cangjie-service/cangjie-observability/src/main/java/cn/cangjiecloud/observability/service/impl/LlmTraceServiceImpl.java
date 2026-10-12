package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.dto.LlmTraceQueryDTO;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.mapper.LlmTraceMapper;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmTraceServiceImpl extends ServiceImpl<LlmTraceMapper, LlmTraceEntity>
        implements ILlmTraceService {

    /**
     * 保留天数，<=0 表示不清理。
     * <p>
     * 默认关：这是物理 DELETE，老库升级上来第一次调度就会把保留期之前的历史调用记录删光，
     * 而「装新版本」不该顺带删历史。确认历史可弃后由部署方显式打开。
     */
    @Value("${cangjie.observability.llm-trace.retention-days:0}")
    private int retentionDays;

    private static final int PURGE_BATCH_SIZE = 2000;

    @Override
    public IPage<LlmTraceEntity> pageQuery(LlmTraceQueryDTO query) {
        // 同样只取精简列：一页 10 条各带两三万字符正文，列表接口会先把自己压垮
        LambdaQueryWrapper<LlmTraceEntity> wrapper = lightColumns()
                .like(StringUtils.hasText(query.getTraceId()), LlmTraceEntity::getTraceId, query.getTraceId())
                .like(StringUtils.hasText(query.getRequestId()), LlmTraceEntity::getRequestId, query.getRequestId())
                .eq(StringUtils.hasText(query.getAppId()), LlmTraceEntity::getAppId, query.getAppId())
                .like(StringUtils.hasText(query.getAppName()), LlmTraceEntity::getAppName, query.getAppName())
                .eq(StringUtils.hasText(query.getSessionId()), LlmTraceEntity::getSessionId, query.getSessionId())
                .eq(StringUtils.hasText(query.getUserId()), LlmTraceEntity::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getModelId()), LlmTraceEntity::getModelId, query.getModelId())
                .like(StringUtils.hasText(query.getModelName()), LlmTraceEntity::getModelName, query.getModelName())
                .eq(StringUtils.hasText(query.getStatus()), LlmTraceEntity::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getPromptKeyword()), LlmTraceEntity::getPromptContent, query.getPromptKeyword())
                .like(StringUtils.hasText(query.getResponseKeyword()), LlmTraceEntity::getResponseContent, query.getResponseKeyword())
                .ge(query.getStartTime() != null, LlmTraceEntity::getStartTime, query.getStartTime())
                .le(query.getEndTime() != null, LlmTraceEntity::getStartTime, query.getEndTime())
                .orderByDesc(LlmTraceEntity::getStartTime);
        return page(new Page<>(query.getPageNum() == null ? 1 : query.getPageNum(),
                query.getPageSize() == null ? 10 : query.getPageSize()), wrapper);
    }

    @Override
    public LlmTraceEntity getDetail(String id) {
        return getById(id);
    }

    @Override
    public List<LlmTraceEntity> timeline(String sessionId) {
        LambdaQueryWrapper<LlmTraceEntity> wrapper = new LambdaQueryWrapper<LlmTraceEntity>()
                .eq(LlmTraceEntity::getSessionId, sessionId)
                .orderByAsc(LlmTraceEntity::getStartTime);
        return list(wrapper);
    }

    @Override
    public List<LlmTraceEntity> listByRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            return List.of();
        }
        return list(lightColumns().eq(LlmTraceEntity::getRunId, runId)
                .orderByAsc(LlmTraceEntity::getStartTime));
    }

    @Override
    public List<LlmTraceEntity> listBackgroundByTrace(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return List.of();
        }
        return list(lightColumns().eq(LlmTraceEntity::getTraceId, traceId)
                .isNull(LlmTraceEntity::getRunId)
                .orderByAsc(LlmTraceEntity::getStartTime));
    }

    @Override
    public LlmTraceEntity getBrief(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        return getOne(lightColumns().eq(LlmTraceEntity::getId, id).last("LIMIT 1"));
    }

    /**
     * 保留期清理：每天 03:50 分批物理删除超期调用追踪。
     * <p>
     * prompt / response 两列正文是整张表最占空间的部分，逻辑删除回收不了；
     * 保留期与 agent_run 对齐，超期后链路详情里的模型调用一并消失，不会出现「有链路无正文」的半截数据。
     */
    @Scheduled(cron = "0 50 3 * * ?")
    public void scheduledPurge() {
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int total = 0;
        int removed;
        do {
            removed = baseMapper.purgeBatch(cutoff, PURGE_BATCH_SIZE);
            total += removed;
        } while (removed == PURGE_BATCH_SIZE);
        if (total > 0) {
            log.info("llm_trace 保留期清理完成，删除 {} 条（开始时间早于 {}）", total, cutoff);
        }
    }

    /**
     * 列表与时间线共用的精简列：正文可能各占数万字符，展开某条调用时再按 id 单条拉。
     * <p>
     * ttft / cost / evaluated / eval_scores 体积小但都是列表直接展示的列，不能为了精简把它们剔掉：
     * 前端为补一列而逐条拉详情，比一次多读几字节贵得多。
     */
    private LambdaQueryWrapper<LlmTraceEntity> lightColumns() {
        return new LambdaQueryWrapper<LlmTraceEntity>()
                .select(LlmTraceEntity::getId, LlmTraceEntity::getRunId, LlmTraceEntity::getTraceId,
                        LlmTraceEntity::getRequestId, LlmTraceEntity::getAppId, LlmTraceEntity::getAppName,
                        LlmTraceEntity::getSessionId, LlmTraceEntity::getUserId,
                        LlmTraceEntity::getModelId, LlmTraceEntity::getModelName,
                        LlmTraceEntity::getModelParams,
                        LlmTraceEntity::getInputTokens, LlmTraceEntity::getOutputTokens, LlmTraceEntity::getTotalTokens,
                        LlmTraceEntity::getFinishReason, LlmTraceEntity::getDuration, LlmTraceEntity::getTtft,
                        LlmTraceEntity::getCost, LlmTraceEntity::getEvaluated, LlmTraceEntity::getEvalScores,
                        LlmTraceEntity::getStatus,
                        LlmTraceEntity::getErrorMessage, LlmTraceEntity::getStartTime);
    }
}