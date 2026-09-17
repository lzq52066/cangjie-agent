package cn.cangjiecloud.eval.controller;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.entity.EvalRunEntity;
import cn.cangjiecloud.eval.service.IEvalCaseService;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
import cn.cangjiecloud.eval.service.IEvalRunService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    // ========== 评估运行 ==========

    @PostMapping("/datasets/{datasetId}/run")
    public EvalRunEntity runEval(@PathVariable String datasetId, @RequestBody Map<String, Object> config) {
        return runService.run(datasetId, config);
    }

    @GetMapping("/runs/{runId}")
    public EvalRunEntity getRunReport(@PathVariable String runId) {
        return runService.getReport(runId);
    }
}