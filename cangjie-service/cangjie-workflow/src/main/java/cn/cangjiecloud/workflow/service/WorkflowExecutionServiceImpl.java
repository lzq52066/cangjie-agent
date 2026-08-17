package cn.cangjiecloud.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;
import cn.cangjiecloud.workflow.mapper.WorkflowExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl
        extends ServiceImpl<WorkflowExecutionMapper, WorkflowExecutionEntity>
        implements IWorkflowExecutionService {

    @Override
    public List<WorkflowExecutionEntity> listByWorkflow(String workflowId) {
        LambdaQueryWrapper<WorkflowExecutionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowExecutionEntity::getWorkflowId, workflowId)
                .orderByDesc(WorkflowExecutionEntity::getStartTime);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordStatus(String executionId, String status, String currentNode, String errorMessage) {
        WorkflowExecutionEntity entity = getById(executionId);
        if (entity == null) {
            log.warn("工作流执行记录不存在: {}", executionId);
            return;
        }
        entity.setStatus(status);
        if (currentNode != null) {
            entity.setCurrentNode(currentNode);
        }
        if (errorMessage != null) {
            entity.setErrorMessage(errorMessage);
        }
        updateById(entity);
    }
}
