package cn.cangjiecloud.workflow.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.core.workflow.WorkflowContext;
import cn.cangjiecloud.workflow.api.dto.WorkflowNodeDTO;
import cn.cangjiecloud.workflow.entity.WorkflowEntity;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;
import cn.cangjiecloud.workflow.mapper.WorkflowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, WorkflowEntity>
        implements IWorkflowService {

    private final WorkflowExecutionEngine executionEngine;
    private final IWorkflowExecutionService workflowExecutionService;

    @Autowired(required = false)
    private TraceCollector traceCollector;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowEntity create(WorkflowEntity entity) {
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("draft");
        }
        if (entity.getVersion() == null) {
            entity.setVersion(1);
        }
        save(entity);
        log.info("工作流已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowEntity update(String id, WorkflowEntity entity) {
        WorkflowEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("工作流不存在");
        }
        entity.setId(id);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        WorkflowEntity entity = getById(id);
        if (entity == null) {
            return;
        }
        removeById(id);
        log.info("工作流已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public List<WorkflowEntity> list(String keyword) {
        LambdaQueryWrapper<WorkflowEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(WorkflowEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(WorkflowEntity::getName, keyword);
        }
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowEntity publish(String id) {
        WorkflowEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("工作流不存在");
        }
        if (!StringUtils.hasText(entity.getNodes())) {
            throw new ApiException("工作流节点为空，无法发布");
        }
        entity.setStatus("published");
        entity.setVersion(entity.getVersion() == null ? 1 : entity.getVersion() + 1);
        updateById(entity);
        log.info("工作流已发布: {} ({}) 版本: {}", entity.getName(), id, entity.getVersion());
        return getById(id);
    }

    @Override
    public WorkflowEntity getByApplicationId(String applicationId) {
        if (!StringUtils.hasText(applicationId)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<WorkflowEntity>()
                .eq(WorkflowEntity::getApplicationId, applicationId)
                .eq(WorkflowEntity::getStatus, "published")
                .orderByDesc(WorkflowEntity::getVersion)
                .last("LIMIT 1"));
    }

    @Override
    public WorkflowExecutionEntity execute(String workflowId, Map<String, Object> inputs) {
        WorkflowEntity workflow = getById(workflowId);
        if (workflow == null) {
            throw new ApiException("工作流不存在");
        }
        if (!"published".equals(workflow.getStatus())) {
            throw new ApiException("工作流未发布，无法执行");
        }

        String traceId = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> safeInputs = inputs == null ? new HashMap<>() : inputs;

        // 创建执行记录
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflowId(workflowId);
        execution.setApplicationId(workflow.getApplicationId());
        execution.setInputs(JSON.toJSONString(safeInputs));
        execution.setStatus("running");
        execution.setStartTime(LocalDateTime.now());
        workflowExecutionService.save(execution);

        long start = System.currentTimeMillis();
        try {
            // 解析节点和边
            List<WorkflowNodeDTO> nodes = JSON.parseArray(workflow.getNodes(), WorkflowNodeDTO.class);
            JSONArray edgesArray = StringUtils.hasText(workflow.getEdges())
                    ? JSON.parseArray(workflow.getEdges())
                    : new JSONArray();

            if (nodes == null || nodes.isEmpty()) {
                throw new ApiException("工作流节点为空");
            }

            // 构造上下文
            WorkflowContext context = new WorkflowContext();
            context.setExecutionId(execution.getId());
            context.setInputs(safeInputs);

            // 使用并行执行引擎执行 DAG
            Map<String, Object> result = executionEngine.execute(nodes, edgesArray, safeInputs);
            context.setVariables(result);
            context.setOutputs(result);

            execution.setCurrentNode("end");
            workflowExecutionService.updateById(execution);

            execution.setOutputs(JSON.toJSONString(context.getOutputs()));
            execution.setStatus("completed");
            execution.setEndTime(LocalDateTime.now());
            execution.setDuration(System.currentTimeMillis() - start);
            workflowExecutionService.updateById(execution);

            // 记录工作流执行成功追踪
            if (traceCollector != null) {
                traceCollector.record("workflow", "execute", traceId, execution.getDuration(), "success",
                        "工作流: " + workflow.getName() + ", 耗时: " + execution.getDuration() + "ms");
            }

            log.info("工作流执行成功: {} ({}), 耗时 {}ms",
                    workflow.getName(), workflowId, execution.getDuration());
            return execution;
        } catch (Exception e) {
            execution.setStatus("failed");
            execution.setErrorMessage(e.getMessage());
            execution.setEndTime(LocalDateTime.now());
            execution.setDuration(System.currentTimeMillis() - start);
            workflowExecutionService.updateById(execution);

            // 记录工作流执行失败追踪
            if (traceCollector != null) {
                traceCollector.record("workflow", "execute", traceId, execution.getDuration(), "fail",
                        "工作流执行失败: " + e.getMessage());
            }

            log.error("工作流执行失败: {} ({})", workflow.getName(), workflowId, e);
            return execution;
        }
    }
}
