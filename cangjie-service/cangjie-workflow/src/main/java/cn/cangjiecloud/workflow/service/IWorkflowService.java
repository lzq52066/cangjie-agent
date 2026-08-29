package cn.cangjiecloud.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.workflow.entity.WorkflowEntity;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;

import java.util.List;
import java.util.Map;

public interface IWorkflowService extends IService<WorkflowEntity> {

    WorkflowEntity create(WorkflowEntity entity);

    WorkflowEntity update(String id, WorkflowEntity entity);

    void delete(String id);

    List<WorkflowEntity> list(String keyword);

    /**
     * 发布工作流
     */
    WorkflowEntity publish(String id);

    /**
     * 根据应用 ID 查询关联的已发布工作流
     */
    WorkflowEntity getByApplicationId(String applicationId);

    /**
     * 执行工作流
     *
     * @param workflowId 工作流 ID
     * @param inputs     输入参数
     * @return 执行记录
     */
    WorkflowExecutionEntity execute(String workflowId, Map<String, Object> inputs);

    /**
     * 异步执行工作流：立即返回执行记录（status=running），
     * 后台线程执行，通过执行记录 ID 查询进度与节点事件
     *
     * @param workflowId 工作流 ID
     * @param inputs     输入参数
     * @return 执行记录（初始状态）
     */
    WorkflowExecutionEntity executeAsync(String workflowId, Map<String, Object> inputs);
}
