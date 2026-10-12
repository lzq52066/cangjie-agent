package cn.cangjiecloud.eval.controller;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.eval.dto.EvalCaseImportDTO;
import cn.cangjiecloud.eval.dto.EvalImportResultDTO;
import cn.cangjiecloud.eval.dto.EvalRunCompareDTO;
import cn.cangjiecloud.eval.dto.OnlineEvalRequestDTO;
import cn.cangjiecloud.eval.dto.OnlineEvalSummaryDTO;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.entity.EvalResultEntity;
import cn.cangjiecloud.eval.entity.EvalRunEntity;
import cn.cangjiecloud.eval.service.IEvalCaseService;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
import cn.cangjiecloud.eval.service.IEvalResultService;
import cn.cangjiecloud.eval.service.IEvalRunService;
import cn.cangjiecloud.eval.service.IOnlineEvalService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评估体系控制器
 * <p>
 * 路径前缀 /api/admin/observability/eval（与前端 proxy 及全局 ADMIN_API 规范保持一致），
 * 提供数据集 CRUD + 用例管理 + 运行评估
 */
@RestController
@RequestMapping(AppConst.ADMIN_API + "/observability/eval")
@RequiredArgsConstructor
@SaCheckLogin
public class EvalController {

    private final IEvalDatasetService datasetService;
    private final IEvalCaseService caseService;
    private final IEvalRunService runService;
    private final IEvalResultService resultService;
    private final IOnlineEvalService onlineEvalService;

    // ========== 数据集 ==========

    @GetMapping("/datasets")
    public PageResult<EvalDatasetEntity> listDatasets(@RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return PageResult.of(datasetService.pageQuery(keyword, pageNum, pageSize));
    }

    @PostMapping("/datasets")
    public EvalDatasetEntity createDataset(@RequestBody EvalDatasetEntity entity) {
        return datasetService.create(entity);
    }

    @PutMapping("/datasets/{id}")
    public EvalDatasetEntity updateDataset(@PathVariable String id, @RequestBody EvalDatasetEntity entity) {
        return datasetService.update(id, entity);
    }

    @DeleteMapping("/datasets/{id}")
    public void deleteDataset(@PathVariable String id) {
        datasetService.delete(id);
    }

    // ========== 用例 ==========

    @GetMapping("/datasets/{datasetId}/cases")
    public PageResult<EvalCaseEntity> listCases(@PathVariable String datasetId,
                                                @RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        return PageResult.of(caseService.pageQuery(datasetId, pageNum, pageSize));
    }

    @PostMapping("/datasets/{datasetId}/cases")
    public EvalCaseEntity createCase(@PathVariable String datasetId, @RequestBody EvalCaseEntity entity) {
        entity.setDatasetId(datasetId);
        return caseService.create(entity);
    }

    @PutMapping("/cases/{id}")
    public EvalCaseEntity updateCase(@PathVariable String id, @RequestBody EvalCaseEntity entity) {
        return caseService.update(id, entity);
    }

    @DeleteMapping("/cases/{id}")
    public void deleteCase(@PathVariable String id) {
        caseService.delete(id);
    }

    // ========== 对话回流（坏 case 从线上回到评测集） ==========

    /**
     * 回流一条：把带反馈 / 人工标注的对话回复加进数据集。
     * <p>
     * 同一条消息重复提交幂等返回已有用例，不新增行。
     */
    @PostMapping("/datasets/{datasetId}/cases/from-message")
    public EvalCaseEntity importCaseFromMessage(@PathVariable String datasetId,
                                                @RequestBody EvalCaseImportDTO request) {
        return caseService.importFromMessage(datasetId, request == null ? null : request.getMessageId());
    }

    /**
     * 批量回流：按应用 / 反馈 / 是否标注扫一批候选回复入集
     */
    @PostMapping("/datasets/{datasetId}/cases/import-conversations")
    public EvalImportResultDTO importCasesFromConversations(@PathVariable String datasetId,
                                                           @RequestBody(required = false) EvalCaseImportDTO request) {
        return caseService.importFromConversations(datasetId, request);
    }

    // ========== 评估运行 ==========

    @PostMapping("/datasets/{datasetId}/run")
    public EvalRunEntity runEval(@PathVariable String datasetId, @RequestBody Map<String, Object> config) {
        return runService.run(datasetId, config);
    }

    /**
     * 运行历史分页：可按数据集 / 状态筛选
     */
    @GetMapping("/runs")
    public PageResult<EvalRunEntity> listRuns(@RequestParam(required = false) String datasetId,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRunEntity> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRunEntity>()
                        .eq(datasetId != null && !datasetId.isBlank(), EvalRunEntity::getDatasetId, datasetId)
                        .eq(status != null && !status.isBlank(), EvalRunEntity::getStatus, status)
                        .orderByDesc(EvalRunEntity::getStartTime);
        return PageResult.of(runService.page(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize), wrapper));
    }

    @GetMapping("/runs/{runId}")
    public EvalRunEntity getRunReport(@PathVariable String runId) {
        return runService.getReport(runId);
    }

    /**
     * 对比两次运行。
     * <p>
     * 必须同时回答「整体好了多少」与「哪几条变坏了」：只看均值会把「十条关键用例跌了、
     * 总体反而涨了」这类典型回归看漏。
     *
     * @param run      当前运行（「改动后」）
     * @param baseline 基准运行（「改动前」）；不传时取同数据集里紧邻它之前的那一次已结束运行
     */
    @GetMapping("/runs/compare")
    public EvalRunCompareDTO compareRuns(@RequestParam String run,
                                         @RequestParam(required = false) String baseline) {
        // 「这次改动相对上一版」才是调参时真正想问的，所以只选一个运行时由它往前找基准；
        // 方向不能反：基准在前、当前在后，差值才是「改动带来的变化」而不是它的相反数
        if (baseline == null || baseline.isBlank()) {
            baseline = runService.previousRun(run).getId();
        }
        return runService.compare(baseline, run);
    }

    /**
     * 逐 case 明细。与报告概览分两个接口：一次运行可能有上百个用例，
     * 没道理跟着轮询一遍遍重传。
     * <p>
     * 本接口只返精简列（问题 / 各指标 / 耗时 / 状态 / traceId），回答与检索片段这些正文
     * 走 {@link #runResultDetail} 展开时单条拉 —— 与链路侧 {@code /traces/{spanId}/payload} 同一套语义。
     */
    @GetMapping("/runs/{runId}/results")
    public List<EvalResultEntity> listRunResults(@PathVariable String runId) {
        return resultService.listByRun(runId);
    }

    /**
     * 单条 case 的完整正文（回答 / 检索片段 / 判定中间量）
     */
    @GetMapping("/runs/{runId}/results/{resultId}")
    public EvalResultEntity runResultDetail(@PathVariable String runId, @PathVariable String resultId) {
        EvalResultEntity result = resultService.detail(resultId);
        if (result == null || !runId.equals(result.getRunId())) {
            throw new ApiException("评估明细不存在: " + resultId);
        }
        return result;
    }

    /**
     * 取消运行：执行中的评估在下一个用例边界停下
     */
    @PostMapping("/runs/{runId}/cancel")
    public EvalRunEntity cancelRun(@PathVariable String runId) {
        return runService.cancelRun(runId);
    }

    /**
     * 删除运行记录（运行中不允许删除）
     */
    @DeleteMapping("/runs/{runId}")
    public void deleteRun(@PathVariable String runId) {
        runService.deleteRun(runId);
    }

    // ========== 在线评测（对线上真实调用抽样打分） ==========

    /**
     * 手动跑一轮在线评测：捞一批未打标的线上调用，用与离线同一套指标打分。
     * <p>
     * 同步执行且条数上限受控：一轮要烧判定 token，开成异步反而会让没人盯的后台任务越跑越大。
     */
    @PostMapping("/online/score")
    public OnlineEvalSummaryDTO scoreOnline(@RequestBody(required = false) OnlineEvalRequestDTO request) {
        return onlineEvalService.score(request);
    }

    /**
     * 待评积压条数：一直涨就是在线评测没在跑（没启用或被判定模型卡住）。
     */
    @GetMapping("/online/pending")
    public long onlinePendingCount() {
        return onlineEvalService.pendingCount();
    }
}