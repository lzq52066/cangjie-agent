package cn.cangjiecloud.eval.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.eval.dto.EvalCaseImportDTO;
import cn.cangjiecloud.eval.dto.EvalImportResultDTO;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;

import java.util.List;

public interface IEvalCaseService extends IService<EvalCaseEntity> {

    EvalCaseEntity create(EvalCaseEntity entity);

    EvalCaseEntity update(String id, EvalCaseEntity entity);

    void delete(String id);

    IPage<EvalCaseEntity> pageQuery(String datasetId, Integer pageNum, Integer pageSize);

    /**
     * 全量列表（评估运行时需遍历全部用例，供 EvalRunService 使用，勿用于分页接口）
     */
    List<EvalCaseEntity> listByDataset(String datasetId);

    /**
     * 从线上对话回流一条用例（人工反馈 / 标注 → 回归集）。
     * <p>
     * 同一条消息重复回流幂等：直接返回已存在的那条，不新增。
     *
     * @param datasetId 目标数据集
     * @param messageId 对话消息 ID（AI 回复或其对应的用户提问都可以）
     * @return 新建或已存在的用例
     */
    EvalCaseEntity importFromMessage(String datasetId, String messageId);

    /**
     * 按反馈 / 标注条件批量回流。
     *
     * @param datasetId 目标数据集
     * @param request   筛选条件
     * @return 分栏报数的执行结果
     */
    EvalImportResultDTO importFromConversations(String datasetId, EvalCaseImportDTO request);
}