package cn.cangjiecloud.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.eval.entity.EvalResultEntity;

import java.util.List;

/**
 * 评估结果明细服务
 */
public interface IEvalResultService extends IService<EvalResultEntity> {

    /**
     * 记录一条 case 结果（只 insert，不 update）
     *
     * @return 是否真正落库成功。调用方要靠它区分「没跑 case」与「明细全写失败」，
     *         不能让写失败静默消失 —— 那样汇总指标齐全而明细一行没有，比直接报错更难发现
     */
    boolean record(EvalResultEntity result);

    /**
     * 某次运行的全部 case 结果，按用例创建顺序返回。
     * <p>
     * 只返精简列：回答 / 检索片段 / 判定中间量这些正文由 {@link #detail(String)} 展开行时单条拉取。
     */
    List<EvalResultEntity> listByRun(String runId);

    /**
     * 单条 case 的完整明细（含回答、检索片段与判定中间量）
     */
    EvalResultEntity detail(String id);

    /**
     * 删除某次运行的全部 case 结果（删 run 时级联调用）
     */
    void removeByRun(String runId);
}
