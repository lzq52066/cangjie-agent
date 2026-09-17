package cn.cangjiecloud.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;

public interface IWorkflowExecutionService extends IService<WorkflowExecutionEntity> {

    /**
     * 按工作流 ID 分页查询执行历史
     */
    IPage<WorkflowExecutionEntity> pageQuery(String workflowId, Integer pageNum, Integer pageSize);

    /**
     * 记录执行状态
     *
     * @param executionId 执行记录 ID
     * @param status      状态：pending / running / completed / failed
     * @param currentNode 当前节点
     * @param errorMessage 错误信息
     */
    void recordStatus(String executionId, String status, String currentNode, String errorMessage);
}
