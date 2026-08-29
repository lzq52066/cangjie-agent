package cn.cangjiecloud.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEventEntity;

import java.util.List;

public interface IWorkflowExecutionEventService extends IService<WorkflowExecutionEventEntity> {

    /**
     * 按执行记录查询节点事件（按创建时间正序）
     */
    List<WorkflowExecutionEventEntity> listByExecution(String executionId);
}
