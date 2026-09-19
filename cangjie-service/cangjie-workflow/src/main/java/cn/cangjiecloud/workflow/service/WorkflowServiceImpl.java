package cn.cangjiecloud.workflow.service;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.core.workflow.WorkflowContext;
import cn.cangjiecloud.workflow.api.dto.WorkflowNodeDTO;
import cn.cangjiecloud.workflow.entity.WorkflowEntity;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEventEntity;
import cn.cangjiecloud.workflow.mapper.WorkflowMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, WorkflowEntity>
        implements IWorkflowService {

    /** 节点事件输出内容最大存储长度（字符） */
    private static final int EVENT_OUTPUT_MAX_LENGTH = 4000;

    private final WorkflowExecutionEngine executionEngine;
    private final IWorkflowExecutionService workflowExecutionService;
    private final IWorkflowExecutionEventService workflowExecutionEventService;

    @Autowired
    @Qualifier("businessExecutor")
    private Executor businessExecutor;

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
    public IPage<WorkflowEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<WorkflowEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(WorkflowEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(WorkflowEntity::getName, keyword);
        }
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
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
        WorkflowEntity workflow = requirePublished(workflowId);
        Map<String, Object> safeInputs = inputs == null ? new HashMap<>() : inputs;
        WorkflowExecutionEntity execution = createExecution(workflow, safeInputs);
        runExecution(workflow, execution, safeInputs);
        return execution;
    }

    @Override
    public WorkflowExecutionEntity executeAsync(String workflowId, Map<String, Object> inputs) {
        WorkflowEntity workflow = requirePublished(workflowId);
        Map<String, Object> safeInputs = inputs == null ? new HashMap<>() : inputs;
        WorkflowExecutionEntity execution = createExecution(workflow, safeInputs);
        businessExecutor.execute(() -> runExecution(workflow, execution, safeInputs));
        log.info("工作流异步执行已提交: {} ({}), executionId={}",
                workflow.getName(), workflowId, execution.getId());
        return execution;
    }

    private WorkflowEntity requirePublished(String workflowId) {
        WorkflowEntity workflow = getById(workflowId);
        if (workflow == null) {
            throw new ApiException("工作流不存在");
        }
        if (!"published".equals(workflow.getStatus())) {
            throw new ApiException("工作流未发布，无法执行");
        }
        return workflow;
    }

    private WorkflowExecutionEntity createExecution(WorkflowEntity workflow, Map<String, Object> safeInputs) {
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflowId(workflow.getId());
        execution.setApplicationId(workflow.getApplicationId());
        execution.setInputs(JsonUtils.toJSONString(safeInputs));
        execution.setStatus("running");
        execution.setStartTime(LocalDateTime.now());
        workflowExecutionService.save(execution);
        return execution;
    }

    /**
     * 执行工作流并维护执行记录与节点事件（同步/异步共用）
     */
    private void runExecution(WorkflowEntity workflow, WorkflowExecutionEntity execution,
                              Map<String, Object> safeInputs) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        long start = System.currentTimeMillis();
        try {
            // 解析节点和边
            List<WorkflowNodeDTO> nodes = JsonUtils.parseList(workflow.getNodes(), WorkflowNodeDTO.class);
            ArrayNode edgesArray = StringUtils.hasText(workflow.getEdges())
                    ? JsonUtils.parseArray(workflow.getEdges())
                    : JsonUtils.newArray();

            if (nodes == null || nodes.isEmpty()) {
                throw new ApiException("工作流节点为空");
            }

            // 构造上下文
            WorkflowContext context = new WorkflowContext();
            context.setExecutionId(execution.getId());
            context.setInputs(safeInputs);

            // 使用并行执行引擎执行 DAG（节点级事件落库）
            Map<String, Object> result = executionEngine.execute(nodes, edgesArray, safeInputs,
                    buildEventListener(execution.getId()));
            context.setVariables(result);
            context.setOutputs(result);

            execution.setCurrentNode("end");
            execution.setOutputs(JsonUtils.toJSONString(context.getOutputs()));
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
                    workflow.getName(), workflow.getId(), execution.getDuration());
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

            log.error("工作流执行失败: {} ({})", workflow.getName(), workflow.getId(), e);
        }
    }

    /**
     * 构建节点事件监听器：每个节点执行完成后落一条事件记录
     */
    private WorkflowExecutionEngine.NodeEventListener buildEventListener(String executionId) {
        return (nodeId, nodeType, status, outputs, durationMs, errorMessage) -> {
            WorkflowExecutionEventEntity event = new WorkflowExecutionEventEntity();
            event.setExecutionId(executionId);
            event.setNodeId(nodeId);
            event.setNodeType(nodeType);
            event.setStatus(status);
            event.setOutputs(truncate(outputs != null ? JsonUtils.toJSONString(outputs) : null));
            event.setErrorMessage(errorMessage);
            event.setDuration(durationMs);
            event.setEndTime(LocalDateTime.now());
            try {
                workflowExecutionEventService.save(event);
            } catch (Exception e) {
                log.warn("节点事件落库失败: execution={}, node={}, {}", executionId, nodeId, e.getMessage());
            }
        };
    }

    private String truncate(String content) {
        if (content == null || content.length() <= EVENT_OUTPUT_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, EVENT_OUTPUT_MAX_LENGTH);
    }
}
