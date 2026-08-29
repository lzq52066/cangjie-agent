package cn.cangjiecloud.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEventEntity;
import cn.cangjiecloud.workflow.mapper.WorkflowExecutionEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class WorkflowExecutionEventServiceImpl
        extends ServiceImpl<WorkflowExecutionEventMapper, WorkflowExecutionEventEntity>
        implements IWorkflowExecutionEventService {

    @Override
    public List<WorkflowExecutionEventEntity> listByExecution(String executionId) {
        return list(new LambdaQueryWrapper<WorkflowExecutionEventEntity>()
                .eq(WorkflowExecutionEventEntity::getExecutionId, executionId)
                .orderByAsc(WorkflowExecutionEventEntity::getCreateTime));
    }
}
