package cn.cangjiecloud.workflow.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.workflow.RetryExecutor;
import cn.cangjiecloud.core.workflow.WorkflowNode;
import cn.cangjiecloud.core.workflow.WorkflowNodeRegistry;
import cn.cangjiecloud.workflow.api.dto.WorkflowNodeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 工作流并行执行引擎
 * <p>
 * 基于 Kahn 拓扑排序算法实现 DAG 并行执行：
 * <ol>
 *   <li>构建邻接表 + 逆邻接表，计算每个节点的入度</li>
 *   <li>每轮取出所有入度为 0 的就绪节点，通过 {@link CompletableFuture} 并发执行</li>
 *   <li>节点完成后递减后继节点入度，入度归零者加入下轮就绪队列</li>
 *   <li>条件节点仅激活匹配的分支后继</li>
 *   <li>支持节点级指数退避重试</li>
 * </ol>
 * <p>
 * 相比原来的栈式 DFS 执行，该引擎可自动识别 DAG 中的并行分支并并发执行，
 * 同时修复了 diamond 拓扑中汇合节点可能在前驱未完成时提前执行的问题。
 */
@Slf4j
@Component
public class WorkflowExecutionEngine {

    private final WorkflowNodeRegistry nodeRegistry;
    private final Executor businessExecutor;

    public WorkflowExecutionEngine(WorkflowNodeRegistry nodeRegistry,
                                   @Qualifier("businessExecutor") Executor businessExecutor) {
        this.nodeRegistry = nodeRegistry;
        this.businessExecutor = businessExecutor;
    }

    /**
     * 有向边定义
     */
    static class Edge {
        final String target;
        final String condition;

        Edge(String target, String condition) {
            this.target = target;
            this.condition = condition;
        }
    }

    /**
     * 节点执行事件监听器（用于节点级持久化、监控等）
     */
    public interface NodeEventListener {
        /**
         * 节点执行完成（成功或失败）回调
         *
         * @param nodeId       节点 ID
         * @param nodeType     节点类型
         * @param status       success / failed
         * @param outputs      节点输出（失败时为 null）
         * @param durationMs   耗时（毫秒）
         * @param errorMessage 错误信息（成功时为 null）
         */
        void onNodeCompleted(String nodeId, String nodeType, String status,
                             Map<String, Object> outputs, long durationMs, String errorMessage);
    }

    /**
     * 并行执行工作流 DAG
     *
     * @param nodes      节点列表
     * @param edgesArray 边列表（JSONArray）
     * @param inputs     工作流输入
     * @return 执行结果（所有节点输出合并后的变量 Map）
     */
    public Map<String, Object> execute(List<WorkflowNodeDTO> nodes, JSONArray edgesArray,
                                        Map<String, Object> inputs) {
        return execute(nodes, edgesArray, inputs, null);
    }

    /**
     * 并行执行工作流 DAG（带节点事件监听）
     */
    public Map<String, Object> execute(List<WorkflowNodeDTO> nodes, JSONArray edgesArray,
                                        Map<String, Object> inputs, NodeEventListener listener) {
        if (nodes == null || nodes.isEmpty()) {
            throw new ApiException("工作流节点为空");
        }

        // ---- 1. 构建数据结构 ----
        Map<String, WorkflowNodeDTO> nodeMap = nodes.stream()
                .collect(Collectors.toMap(WorkflowNodeDTO::getId, n -> n, (a, b) -> a));

        // 邻接表: source -> [Edge]
        Map<String, List<Edge>> adjacency = new HashMap<>();
        // 逆邻接表: target -> [source]
        Map<String, List<String>> reverseAdj = new HashMap<>();

        for (int i = 0; i < edgesArray.size(); i++) {
            JSONObject edge = edgesArray.getJSONObject(i);
            String source = edge.getString("source");
            String target = edge.getString("target");
            if (source == null || target == null) {
                continue;
            }
            String cond = edge.getString("condition");
            adjacency.computeIfAbsent(source, k -> new ArrayList<>())
                    .add(new Edge(target, cond));
            reverseAdj.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
        }

        // 确保所有节点在 reverseAdj 中至少有一个空列表（入度为 0 的节点）
        for (WorkflowNodeDTO node : nodes) {
            reverseAdj.putIfAbsent(node.getId(), new ArrayList<>());
        }

        // 入度计数（线程安全）
        Map<String, AtomicInteger> inDegree = new ConcurrentHashMap<>();
        for (var entry : reverseAdj.entrySet()) {
            inDegree.put(entry.getKey(), new AtomicInteger(entry.getValue().size()));
        }

        // ---- 2. 找到 start 节点，初始化就绪队列 ----
        WorkflowNodeDTO startNode = nodes.stream()
                .filter(n -> "start".equals(n.getType()))
                .findFirst()
                .orElseThrow(() -> new ApiException("工作流缺少 start 节点"));

        ConcurrentLinkedQueue<String> readyQueue = new ConcurrentLinkedQueue<>();
        readyQueue.add(startNode.getId());

        // 上下文变量（线程安全）
        Map<String, Object> variables = new ConcurrentHashMap<>(inputs != null ? inputs : Map.of());
        Map<String, Map<String, Object>> nodeResults = new ConcurrentHashMap<>();

        // 失败标记
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        List<Exception> failures = new ArrayList<>();

        // ---- 3. 主循环：按轮次并行执行 ----
        while (!readyQueue.isEmpty() && !hasFailure.get()) {
            // 收集本轮所有就绪节点
            List<String> batch = new ArrayList<>();
            String nodeId;
            while ((nodeId = readyQueue.poll()) != null) {
                batch.add(nodeId);
            }

            if (batch.isEmpty()) {
                break;
            }

            log.debug("本轮并行执行 {} 个节点: {}", batch.size(), batch);

            // 若本轮任一节点配置 parallel=false（串行执行），则整轮降级为串行
            boolean serialBatch = batch.stream()
                    .map(nodeMap::get)
                    .anyMatch(this::isSerialNode);

            if (serialBatch) {
                for (String nid : batch) {
                    try {
                        executeNode(nid, nodeMap, adjacency, inDegree,
                                variables, nodeResults, readyQueue, listener);
                    } catch (Exception e) {
                        hasFailure.set(true);
                        synchronized (failures) {
                            failures.add(e);
                        }
                    }
                }
            } else {
                // 并发执行本轮节点
                CompletableFuture<?>[] futures = batch.stream()
                        .map(nid -> CompletableFuture.runAsync(() -> {
                            try {
                                executeNode(nid, nodeMap, adjacency, inDegree,
                                        variables, nodeResults, readyQueue, listener);
                            } catch (Exception e) {
                                hasFailure.set(true);
                                synchronized (failures) {
                                    failures.add(e);
                                }
                            }
                        }, businessExecutor))
                        .toArray(CompletableFuture[]::new);

                // 等待本轮所有节点完成
                CompletableFuture.allOf(futures).join();
            }
        }

        // ---- 4. 检查是否失败 ----
        if (hasFailure.get() && !failures.isEmpty()) {
            Exception first = failures.get(0);
            if (first instanceof ApiException) {
                throw (ApiException) first;
            }
            throw new ApiException("工作流执行失败: " + first.getMessage(), failures.get(0));
        }

        // 统计因条件分支未激活的节点
        long unexecuted = nodes.stream()
                .filter(n -> !nodeResults.containsKey(n.getId()))
                .filter(n -> !"start".equals(n.getType()))
                .filter(n -> !"end".equals(n.getType()))
                .count();
        if (unexecuted > 0) {
            log.info("工作流执行完成，{} 个节点因条件分支未激活", unexecuted);
        }

        return new HashMap<>(variables);
    }

    /**
     * 执行单个节点，完成后传播到后继节点
     */
    private void executeNode(String nodeId,
                              Map<String, WorkflowNodeDTO> nodeMap,
                              Map<String, List<Edge>> adjacency,
                              Map<String, AtomicInteger> inDegree,
                              Map<String, Object> variables,
                              Map<String, Map<String, Object>> nodeResults,
                              ConcurrentLinkedQueue<String> readyQueue,
                              NodeEventListener listener) {

        WorkflowNodeDTO nodeDTO = nodeMap.get(nodeId);
        if (nodeDTO == null) {
            log.warn("工作流节点不存在: {}", nodeId);
            return;
        }

        // start 节点：直接传播后继，不执行业务逻辑
        if ("start".equals(nodeDTO.getType())) {
            nodeResults.put(nodeId, Map.of());
            propagateSuccessors(nodeId, nodeDTO.getType(), null,
                    adjacency, inDegree, readyQueue);
            log.info("工作流起始: {}", nodeDTO.getName());
            return;
        }

        // end 节点：记录结果，不再传播
        if ("end".equals(nodeDTO.getType())) {
            nodeResults.put(nodeId, new HashMap<>(variables));
            log.info("工作流结束: {}", nodeDTO.getName());
            return;
        }

        // 获取节点执行器
        WorkflowNode node = nodeRegistry.get(nodeDTO.getType());
        if (node == null) {
            throw new ApiException("未找到节点类型实现: " + nodeDTO.getType());
        }

        log.info("执行工作流节点: {} ({}) 类型: {}", nodeDTO.getName(), nodeId, nodeDTO.getType());

        // 读取节点重试配置
        int maxRetries = getNodeRetryCount(nodeDTO);
        long delayMs = getNodeRetryDelay(nodeDTO);

        // 执行节点（带重试）
        long nodeStart = System.currentTimeMillis();
        Map<String, Object> result;
        try {
            if (maxRetries > 0) {
                result = RetryExecutor.execute(
                        () -> node.execute(new HashMap<>(variables), nodeDTO.getConfig()),
                        maxRetries, delayMs,
                        nodeDTO.getName()
                );
            } else {
                result = node.execute(new HashMap<>(variables), nodeDTO.getConfig());
            }
        } catch (Exception e) {
            log.error("节点执行失败: {} ({})", nodeDTO.getName(), nodeId, e);
            emitNodeEvent(listener, nodeId, nodeDTO.getType(), "failed", null,
                    System.currentTimeMillis() - nodeStart, e.getMessage());
            throw new ApiException("节点 [" + nodeDTO.getName() + "] 执行失败: " + e.getMessage(), e);
        }

        emitNodeEvent(listener, nodeId, nodeDTO.getType(), "success", result,
                System.currentTimeMillis() - nodeStart, null);

        // 存储结果并合并到上下文变量
        nodeResults.put(nodeId, result);
        if (result != null) {
            variables.putAll(result);
        }

        // 传播到后继节点
        propagateSuccessors(nodeId, nodeDTO.getType(), result,
                adjacency, inDegree, readyQueue);
    }

    /**
     * 回调节点事件（监听器异常不影响主流程）
     */
    private void emitNodeEvent(NodeEventListener listener, String nodeId, String nodeType,
                               String status, Map<String, Object> outputs,
                               long durationMs, String errorMessage) {
        if (listener == null) {
            return;
        }
        try {
            listener.onNodeCompleted(nodeId, nodeType, status, outputs, durationMs, errorMessage);
        } catch (Exception e) {
            log.warn("节点事件回调失败: node={}, {}", nodeId, e.getMessage());
        }
    }

    /**
     * 节点执行完成后，递减所有后继节点的入度，入度归零者加入就绪队列。
     * <p>
     * 条件节点特殊处理：仅激活匹配 condition_result 的分支。
     */
    private void propagateSuccessors(String nodeId, String nodeType, Map<String, Object> nodeResult,
                                      Map<String, List<Edge>> adjacency,
                                      Map<String, AtomicInteger> inDegree,
                                      ConcurrentLinkedQueue<String> readyQueue) {

        List<Edge> outEdges = adjacency.getOrDefault(nodeId, List.of());
        if (outEdges.isEmpty()) {
            return;
        }

        // 条件节点：获取 condition_result
        String conditionResult = null;
        if ("condition".equals(nodeType) && nodeResult != null) {
            Object condObj = nodeResult.get("condition_result");
            conditionResult = condObj != null ? condObj.toString() : "false";
        }

        for (Edge edge : outEdges) {
            if (edge.target == null) {
                continue;
            }

            // 条件边路由：
            // - 普通节点只走无 condition 的边（与原栈式 DFS 语义一致，避免条件边被错误激活）
            // - condition 节点仅激活匹配 condition_result 的分支
            if (edge.condition != null && !edge.condition.isEmpty()) {
                if (!"condition".equals(nodeType)) {
                    log.debug("普通节点跳过条件边: {} -> {} (condition={})",
                            nodeId, edge.target, edge.condition);
                    continue;
                }
                if (!edge.condition.equalsIgnoreCase(conditionResult)) {
                    log.debug("条件分支不匹配: {} -> {} (condition={}, result={})",
                            nodeId, edge.target, edge.condition, conditionResult);
                    continue; // 不激活该分支，也不递减入度
                }
            }

            // 递减后继节点入度
            AtomicInteger degree = inDegree.get(edge.target);
            if (degree == null) {
                log.warn("后继节点不存在于 DAG 中: {}", edge.target);
                continue;
            }

            int remaining = degree.decrementAndGet();
            if (remaining == 0) {
                readyQueue.add(edge.target);
            }
        }
    }

    /**
     * 判断节点是否配置为串行执行（config.parallel = false）
     */
    private boolean isSerialNode(WorkflowNodeDTO nodeDTO) {
        if (nodeDTO == null) return false;
        Map<String, Object> config = nodeDTO.getConfig();
        return config != null && Boolean.FALSE.equals(config.get("parallel"));
    }

    /**
     * 从节点配置中读取重试次数
     */
    @SuppressWarnings("unchecked")
    private int getNodeRetryCount(WorkflowNodeDTO nodeDTO) {
        Map<String, Object> config = nodeDTO.getConfig();
        if (config == null) {
            return 0;
        }
        Object retryObj = config.get("retry");
        if (retryObj instanceof Map) {
            Map<String, Object> retryConfig = (Map<String, Object>) retryObj;
            Object enabled = retryConfig.get("enabled");
            if (enabled instanceof Boolean && !((Boolean) enabled)) {
                return 0;
            }
            Object maxRetries = retryConfig.get("maxRetries");
            if (maxRetries instanceof Number) {
                return ((Number) maxRetries).intValue();
            }
        }
        return 0;
    }

    /**
     * 从节点配置中读取重试延迟（毫秒）
     */
    @SuppressWarnings("unchecked")
    private long getNodeRetryDelay(WorkflowNodeDTO nodeDTO) {
        Map<String, Object> config = nodeDTO.getConfig();
        if (config == null) {
            return 1000L;
        }
        Object retryObj = config.get("retry");
        if (retryObj instanceof Map) {
            Map<String, Object> retryConfig = (Map<String, Object>) retryObj;
            Object delayMs = retryConfig.get("delayMs");
            if (delayMs instanceof Number) {
                return ((Number) delayMs).longValue();
            }
        }
        return 1000L;
    }
}