package cn.cangjiecloud.agent;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.workflow.WorkflowNode;
import cn.cangjiecloud.core.workflow.WorkflowNodeRegistry;
import cn.cangjiecloud.workflow.api.dto.WorkflowNodeDTO;
import cn.cangjiecloud.workflow.service.WorkflowExecutionEngine;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作流并行执行引擎单元测试：
 * 覆盖并行分支、变量传递、条件路由、失败传播与节点事件回调
 */
class WorkflowExecutionEngineTest {

    /** 记录每个节点执行时看到的输入，用于断言执行顺序与变量可见性 */
    private final Map<String, Map<String, Object>> executedSnapshots = new ConcurrentHashMap<>();

    private WorkflowNode stub(String type, boolean fail) {
        return new WorkflowNode() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public String getName() {
                return type;
            }

            @Override
            public String getDescription() {
                return "stub " + type;
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
                executedSnapshots.put(type, new HashMap<>(inputs));
                if (fail) {
                    throw new IllegalStateException(type + " 模拟失败");
                }
                return Map.of(type + "_output", type + "-done");
            }
        };
    }

    private WorkflowNode conditionStub() {
        return new WorkflowNode() {
            @Override
            public String getType() {
                return "condition";
            }

            @Override
            public String getName() {
                return "condition";
            }

            @Override
            public String getDescription() {
                return "stub condition";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
                executedSnapshots.put("condition", new HashMap<>(inputs));
                return Map.of("condition_result", "true");
            }
        };
    }

    private WorkflowExecutionEngine engine(List<WorkflowNode> nodes) {
        WorkflowNodeRegistry registry = new WorkflowNodeRegistry(nodes);
        return new WorkflowExecutionEngine(registry, Runnable::run);
    }

    private WorkflowNodeDTO node(String id, String type) {
        WorkflowNodeDTO dto = new WorkflowNodeDTO();
        dto.setId(id);
        dto.setType(type);
        dto.setName(type + "-" + id);
        return dto;
    }

    private ArrayNode edges(String... pairs) {
        ArrayNode array = JsonUtils.newArray();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            ObjectNode e = JsonUtils.newObject();
            e.put("source", pairs[i]);
            e.put("target", pairs[i + 1]);
            array.add(e);
        }
        return array;
    }

    private ArrayNode edgesWithCondition(String source, String target, String condition) {
        ArrayNode array = JsonUtils.newArray();
        ObjectNode e = JsonUtils.newObject();
        e.put("source", source);
        e.put("target", target);
        e.put("condition", condition);
        array.add(e);
        return array;
    }

    @Test
    void parallelBranchesBothExecuteAndMergeVariables() {
        WorkflowExecutionEngine engine = engine(List.of(stub("taskA", false), stub("taskB", false)));
        List<WorkflowNodeDTO> nodes = List.of(
                node("s", "start"), node("a", "taskA"), node("b", "taskB"), node("e", "end"));
        ArrayNode edges = JsonUtils.newArray();
        edges.addAll(edges("s", "a", "s", "b"));
        edges.addAll(edges("a", "e", "b", "e"));

        Map<String, Object> result = engine.execute(nodes, edges, Map.of("q", "hello"));

        assertEquals("hello", result.get("q"), "输入变量应保留");
        assertEquals("taskA-done", result.get("taskA_output"), "分支 A 输出应合并");
        assertEquals("taskB-done", result.get("taskB_output"), "分支 B 输出应合并");
    }

    @Test
    void serialChainPropagatesVariables() {
        WorkflowExecutionEngine engine = engine(List.of(stub("taskA", false), stub("taskB", false)));
        List<WorkflowNodeDTO> nodes = List.of(
                node("s", "start"), node("a", "taskA"), node("b", "taskB"), node("e", "end"));

        Map<String, Object> result = engine.execute(nodes, edges("s", "a", "a", "b", "b", "e"), Map.of());

        assertEquals("taskA-done", result.get("taskA_output"));
        assertEquals("taskA-done", executedSnapshots.get("taskB").get("taskA_output"),
                "后继节点应能看到前驱输出");
    }

    @Test
    void conditionEdgesOnlyActivateMatchingBranch() {
        WorkflowExecutionEngine engine = engine(List.of(
                conditionStub(), stub("taskTrue", false), stub("taskFalse", false)));
        List<WorkflowNodeDTO> nodes = List.of(
                node("s", "start"), node("c", "condition"),
                node("t", "taskTrue"), node("f", "taskFalse"), node("e", "end"));

        ArrayNode edges = JsonUtils.newArray();
        edges.addAll(edges("s", "c"));
        edges.addAll(edgesWithCondition("c", "t", "true"));
        edges.addAll(edgesWithCondition("c", "f", "false"));
        edges.addAll(edges("t", "e", "f", "e"));

        Map<String, Object> result = engine.execute(nodes, edges, Map.of());

        assertEquals("taskTrue-done", result.get("taskTrue_output"), "匹配分支应执行");
        assertFalse(result.containsKey("taskFalse_output"), "不匹配分支不应执行");
        assertFalse(executedSnapshots.containsKey("taskFalse"), "不匹配分支节点不应被调用");
    }

    @Test
    void nodeFailurePropagatesAsApiException() {
        WorkflowExecutionEngine engine = engine(List.of(stub("taskA", true)));
        List<WorkflowNodeDTO> nodes = List.of(
                node("s", "start"), node("a", "taskA"), node("e", "end"));

        ApiException ex = assertThrows(ApiException.class,
                () -> engine.execute(nodes, edges("s", "a", "a", "e"), Map.of()));
        assertTrue(ex.getMessage().contains("taskA"), "异常信息应包含失败节点");
    }

    @Test
    void listenerReceivesNodeEvents() {
        WorkflowExecutionEngine engine = engine(List.of(stub("taskA", false)));
        List<WorkflowNodeDTO> nodes = List.of(
                node("s", "start"), node("a", "taskA"), node("e", "end"));

        List<String> events = new ArrayList<>();
        engine.execute(nodes, edges("s", "a", "a", "e"), Map.of(),
                (nodeId, nodeType, status, outputs, durationMs, errorMessage) ->
                        events.add(nodeId + ":" + nodeType + ":" + status));

        assertTrue(events.contains("a:taskA:success"), "应收到业务节点成功事件: " + events);
        assertFalse(events.stream().anyMatch(e -> e.startsWith("s:")), "start 节点不产生事件");
        assertFalse(events.stream().anyMatch(e -> e.startsWith("e:")), "end 节点不产生事件");
    }

    @Test
    void listenerReceivesFailureEvent() {
        WorkflowExecutionEngine engine = engine(List.of(stub("taskA", true)));
        List<WorkflowNodeDTO> nodes = List.of(
                node("s", "start"), node("a", "taskA"), node("e", "end"));

        List<String> events = new ArrayList<>();
        assertThrows(ApiException.class, () -> engine.execute(nodes, edges("s", "a", "a", "e"), Map.of(),
                (nodeId, nodeType, status, outputs, durationMs, errorMessage) ->
                        events.add(nodeId + ":" + status)));

        assertTrue(events.contains("a:failed"), "应收到节点失败事件: " + events);
    }

    @Test
    void missingStartNodeThrows() {
        WorkflowExecutionEngine engine = engine(List.of(stub("taskA", false)));
        List<WorkflowNodeDTO> nodes = List.of(node("a", "taskA"), node("e", "end"));

        ApiException ex = assertThrows(ApiException.class,
                () -> engine.execute(nodes, edges("a", "e"), Map.of()));
        assertNotNull(ex.getMessage());
    }
}
