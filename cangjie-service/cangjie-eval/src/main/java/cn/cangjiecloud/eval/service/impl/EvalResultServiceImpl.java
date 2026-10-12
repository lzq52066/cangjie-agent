package cn.cangjiecloud.eval.service.impl;

import cn.cangjiecloud.eval.entity.EvalResultEntity;
import cn.cangjiecloud.eval.mapper.EvalResultMapper;
import cn.cangjiecloud.eval.service.IEvalResultService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 评估结果明细服务
 * <p>
 * 明细落库失败不能把整个评估运行带崩：跑完一个 case 才写一行，写失败只记日志并把「没写成」
 * 回传给调用方汇总；汇总指标仍在 eval_run.summary 里，最多是这条 case 的明细缺失。
 */
@Slf4j
@Service
public class EvalResultServiceImpl extends ServiceImpl<EvalResultMapper, EvalResultEntity>
        implements IEvalResultService {

    @Override
    public boolean record(EvalResultEntity result) {
        if (result == null) {
            return false;
        }
        try {
            return save(result);
        } catch (Exception e) {
            log.error("评估结果明细落库失败: runId={}, caseId={}", result.getRunId(), result.getCaseId(), e);
            return false;
        }
    }

    @Override
    public List<EvalResultEntity> listByRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            return List.of();
        }
        return list(briefWrapper()
                .eq(EvalResultEntity::getRunId, runId)
                .orderByAsc(EvalResultEntity::getCreateTime)
                .orderByAsc(EvalResultEntity::getId));
    }

    @Override
    public EvalResultEntity detail(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        return getById(id);
    }

    /**
     * 只选精简列的 wrapper：正文三列跟着轮询重传没有意义，由 {@link #detail(String)} 按行拉取
     */
    private LambdaQueryWrapper<EvalResultEntity> briefWrapper() {
        return new LambdaQueryWrapper<EvalResultEntity>()
                .select(EvalResultEntity.class,
                        info -> !EvalResultEntity.HEAVY_COLUMNS.contains(info.getColumn()));
    }

    @Override
    public void removeByRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        remove(new LambdaQueryWrapper<EvalResultEntity>().eq(EvalResultEntity::getRunId, runId));
    }
}
