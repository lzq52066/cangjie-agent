package cn.cangjiecloud.workflow.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.core.workflow.WorkflowContext;
import cn.cangjiecloud.core.workflow.WorkflowNode;
import cn.cangjiecloud.core.workflow.WorkflowNodeRegistry;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, WorkflowEntity>
        implements IWorkflowService {

    private final WorkflowNodeRegistry workflowNodeRegistry;
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

            // 节点 Map
            Map<String, WorkflowNodeDTO> nodeMap = nodes.stream()
                    .collect(Collectors.toMap(WorkflowNodeDTO::getId, n -> n));

            // 带条件的邻接表: source -> [(target, condition)]
            // condition 为 null/空表示无条件边（始终走）
            record EdgeRef(String target, String condition) {}
            Map<String, List<EdgeRef>> adjacency = new HashMap<>();
            for (int i = 0; i < edgesArray.size(); i++) {
                JSONObject edge = edgesArray.getJSONObject(i);
                String source = edge.getString("source");
                String target = edge.getString("target");
                if (source == null || target == null) {
                    continue;
                }
                String cond = edge.getString("condition");
                adjacency.computeIfAbsent(source, k -> new ArrayList<>())
                        .add(new EdgeRef(target, cond));
            }

            // 找到起始节点
            WorkflowNodeDTO startNode = nodes.stream()
                    .filter(n -> "start".equals(n.getType()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException("工作流缺少 start 节点"));

            // 构造上下文
            WorkflowContext context = new WorkflowContext();
            context.setExecutionId(execution.getId());
            context.setInputs(safeInputs);
            Map<String, Object> variables = new HashMap<>(safeInputs);
            context.setVariables(variables);

            // 拓扑遍历：使用栈按序执行
            Deque<String> stack = new ArrayDeque<>();
            stack.push(startNode.getId());
            Set<String> visited = new HashSet<>();

            while (!stack.isEmpty()) {
                String nodeId = stack.pop();
                if (!visited.add(nodeId)) {
                    continue;
                }
                WorkflowNodeDTO nodeDTO = nodeMap.get(nodeId);
                if (nodeDTO == null) {
                    log.warn("工作流节点不存在: {}", nodeId);
                    continue;
                }

                execution.setCurrentNode(nodeId);
                workflowExecutionService.updateById(execution);

                WorkflowNode node = workflowNodeRegistry.get(nodeDTO.getType());
                if (node == null) {
                    throw new ApiException("未找到节点类型实现: " + nodeDTO.getType());
                }

                log.info("执行工作流节点: {} ({}) 类型: {}",
                        nodeDTO.getName(), nodeId, nodeDTO.getType());
                Map<String, Object> result = node.execute(context.getVariables(), nodeDTO.getConfig());
                if (result != null) {
                    context.getVariables().putAll(result);
                }

                // 到达结束节点
                if ("end".equals(nodeDTO.getType())) {
                    break;
                }

                // 条件分支路由
                List<EdgeRef> outEdges = adjacency.getOrDefault(nodeId, List.of());
                if ("condition".equals(nodeDTO.getType())) {
                    // 条件节点：根据 condition_result 选择匹配的出边
                    Object condResult = context.getVariables().get("condition_result");
                    String condValue = condResult != null ? condResult.toString() : "false";
                    List<EdgeRef> matched = new ArrayList<>();
                    for (EdgeRef e : outEdges) {
                        if (e.condition == null || e.condition.isEmpty()
                                || e.condition.equalsIgnoreCase(condValue)) {
                            matched.add(e);
                        }
                    }
                    // 逆序压栈保证按序执行
                    for (int i = matched.size() - 1; i >= 0; i--) {
                        stack.push(matched.get(i).target);
                    }
                } else {
                    // 普通节点：走所有无条件出边
                    for (int i = outEdges.size() - 1; i >= 0; i--) {
                        EdgeRef e = outEdges.get(i);
                        if (e.condition == null || e.condition.isEmpty()) {
                            stack.push(e.target);
                        }
                    }
                }
            }

            context.setOutputs(context.getVariables());
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
