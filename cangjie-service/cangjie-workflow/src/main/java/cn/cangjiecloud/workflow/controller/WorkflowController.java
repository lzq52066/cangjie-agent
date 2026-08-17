package cn.cangjiecloud.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.core.workflow.WorkflowNode;
import cn.cangjiecloud.core.workflow.WorkflowNodeRegistry;
import cn.cangjiecloud.workflow.api.dto.WorkflowExecuteDTO;
import cn.cangjiecloud.workflow.entity.WorkflowEntity;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;
import cn.cangjiecloud.workflow.service.IWorkflowExecutionService;
import cn.cangjiecloud.workflow.service.IWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/workflow")
public class WorkflowController {

    private final IWorkflowService workflowService;
    private final IWorkflowExecutionService workflowExecutionService;
    private final WorkflowNodeRegistry workflowNodeRegistry;

    @GetMapping
    public R<List<WorkflowEntity>> list(@RequestParam(required = false) String keyword) {
        return R.data(workflowService.list(keyword));
    }

    @PostMapping
    public R<WorkflowEntity> create(@RequestBody WorkflowEntity entity) {
        return R.data(workflowService.create(entity));
    }

    @PutMapping("/{id}")
    public R<WorkflowEntity> update(@PathVariable String id, @RequestBody WorkflowEntity entity) {
        return R.data(workflowService.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        workflowService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<WorkflowEntity> get(@PathVariable String id) {
        return R.data(workflowService.getById(id));
    }

    @PostMapping("/{id}/publish")
    public R<WorkflowEntity> publish(@PathVariable String id) {
        return R.data(workflowService.publish(id));
    }

    @PostMapping("/{id}/execute")
    public R<WorkflowExecutionEntity> execute(@PathVariable String id,
                                              @RequestBody WorkflowExecuteDTO dto) {
        dto.setWorkflowId(id);
        return R.data(workflowService.execute(id, dto.getInputs()));
    }

    @GetMapping("/{id}/executions")
    public R<List<WorkflowExecutionEntity>> executions(@PathVariable String id) {
        return R.data(workflowExecutionService.listByWorkflow(id));
    }

    @GetMapping("/execution/{executionId}")
    public R<WorkflowExecutionEntity> execution(@PathVariable String executionId) {
        return R.data(workflowExecutionService.getById(executionId));
    }

    @GetMapping("/node-types")
    public R<List<String>> nodeTypes() {
        return R.data(workflowNodeRegistry.listTypes());
    }

    @GetMapping("/nodes")
    public R<List<WorkflowNode>> nodes() {
        return R.data(workflowNodeRegistry.listAll());
    }
}
