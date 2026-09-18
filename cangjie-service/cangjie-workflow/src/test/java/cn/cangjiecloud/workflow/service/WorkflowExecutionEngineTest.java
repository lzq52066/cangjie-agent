package cn.cangjiecloud.workflow.service;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.workflow.WorkflowNode;
import cn.cangjiecloud.core.workflow.WorkflowNodeRegistry;
import cn.cangjiecloud.workflow.api.dto.WorkflowNodeDTO;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link WorkflowExecutionEngine} 单元测试：DAG 调度、条件路由、失败与重试、事件回调、异常分支。
 * 全程使用桩节点 + 同步执行器，不加载 Spring 上下文。
 */
class WorkflowExecutionEngineTest {

    // ---- 测试脚手架 ----

    /** 可编程桩节点：记录调用次数与入参快照 */
    private static class StubNode implements WorkflowNode {
        private final String type;
        private final Function<Map<String, Object>, Map<String, Object>> behavior;
        final AtomicInteger calls = new AtomicInteger();
        final List<Map<String, Object>> capturedInputs = new CopyOnWriteArrayList<>();

        StubNode(String type, Function<Map<String, Object>, Map<String, Object>> behavior) {
            this.type = type;
            this.behavior = behavior;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getName() {
            return "桩-" + type;
        }

        @Override
        public String getDescription() {
            return "测试桩";
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
            calls.incrementAndGet();
            capturedInputs.add(new HashMap<>(inputs));
            return behavior.apply(inputs);
        }
    }

    private static WorkflowNodeDTO dto(String id, String name, String type, Map<String, Object> config) {
        WorkflowNodeDTO d = new WorkflowNodeDTO();
        d.setId(id);
        d.setName(name);
        d.setType(type);
        d.setConfig(config);
        return d;
    }

    private static WorkflowNodeDTO dto(String id, String type) {
        return dto(id, id, type, null);
    }

    private static WorkflowNodeDTO dto(String id, String type, Map<String, Object> config) {
        return dto(id, id, type, config);
    }

    private static JSONArray edges(Object... triples) {
        JSONArray array = new JSONArray();
        for (Object t : triples) {
            if (t instanceof String[] parts) {
                JSONObject e = new JSONObject();
                e.put("source", parts[0]);
                e.put("target", parts[1]);
                if (parts.length > 2 && parts[2] != null) {
                    e.put("condition", parts[2]);
                }
                array.add(e);
            } else {
                array.add(t);
            }
        }
        return array;
    }

    private static WorkflowExecutionEngine engine(WorkflowNode... stubs) {
        return new WorkflowNodeRegistryTestSupport().build(List.of(stubs));
    }

    /** 独立小类：避免构造器泛型推断问题 */
    private static class WorkflowNodeRegistryTestSupport {
        WorkflowExecutionEngine build(List<WorkflowNode> nodes) {
            return new WorkflowExecutionEngine(new WorkflowNodeRegistry(new ArrayList<>(nodes)), Runnable::run);
        }
    }

    // ---- 参数校验分支 ----

    @Test
    // 覆盖场景：nodes 为 null / 空列表 —— 抛"工作流节点为空"
    void executeShouldThrowWhenNodesEmpty() {
        WorkflowExecutionEngine engine = engine();
        assertThatThrownBy(() -> engine.execute(null, new JSONArray(), Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("工作流节点为空");
        assertThatThrownBy(() -> engine.execute(List.of(), new JSONArray(), Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("工作流节点为空");
    }

    @Test
    // 覆盖场景：缺少 start 节点 —— 抛"工作流缺少 start 节点"
    void executeShouldThrowWhenStartNodeMissing() {
        WorkflowExecutionEngine engine = engine();
        assertThatThrownBy(() -> engine.execute(
                List.of(dto("n1", "llm")), new JSONArray(), Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("工作流缺少 start 节点");
    }

    // ---- 正常流 ----

    @Test
    // 覆盖场景：start→llm→end 线性流 —— start 不执行、end 不合并输出、llm 输出并入上下文
    void executeShouldRunLinearFlowAndMergeOutputs() {
        StubNode llm = new StubNode("llm", in -> Map.of("llm_output", "你好 " + in.get("user")));

        Map<String, Object> result = engine(llm).execute(
                List.of(dto("s", "start"), dto("l", "大模型", "llm", null), dto("e", "end")),
                edges(new String[]{"s", "l"}, new String[]{"l", "e"}),
                Map.of("user", "张三"));

        assertThat(llm.calls).hasValue(1);
        assertThat(llm.capturedInputs.get(0)).containsEntry("user", "张三");
        assertThat(result)
                .containsEntry("user", "张三")
                .containsEntry("llm_output", "你好 张三")
                .doesNotContainKey("__end__");
    }

    @Test
    // 覆盖场景：inputs 为 null —— 空上下文也能正常执行
    void executeShouldWorkWithNullInputs() {
        StubNode llm = new StubNode("llm", in -> Map.of("k", "v"));

        Map<String, Object> result = engine(llm).execute(
                List.of(dto("s", "start"), dto("l", "llm"), dto("e", "end")),
                edges(new String[]{"s", "l"}, new String[]{"l", "e"}),
                null);

        assertThat(result).containsEntry("k", "v");
    }

    @Test
    // 覆盖场景：diamond 拓扑 —— 汇合节点等两个前驱都完成后仅执行一次，且能看到全部上游输出
    void executeShouldRunDiamondJoinExactlyOnce() {
        StubNode a = new StubNode("sinkA", in -> Map.of("a_out", 1));
        StubNode b = new StubNode("sinkB", in -> Map.of("b_out", 2));
        StubNode c = new StubNode("sinkC", in -> Map.of("c_out",
                in.get("a_out").toString() + in.get("b_out")));

        Map<String, Object> result = engine(a, b, c).execute(
                List.of(dto("s", "start"), dto("a", "sinkA"), dto("b", "sinkB"),
                        dto("c", "sinkC"), dto("e", "end")),
                edges(new String[]{"s", "a"}, new String[]{"s", "b"},
                        new String[]{"a", "c"}, new String[]{"b", "c"}, new String[]{"c", "e"}),
                Map.of());

        assertThat(c.calls).hasValue(1);
        assertThat(result).containsEntry("c_out", "12");
    }

    // ---- 异常与未知类型 ----

    @Test
    // 覆盖场景：注册表无对应实现 —— 抛"未找到节点类型实现: magic"
    void executeShouldThrowWhenNodeTypeUnregistered() {
        assertThatThrownBy(() -> engine().execute(
                List.of(dto("s", "start"), dto("m", "magic")),
                edges(new String[]{"s", "m"}), Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("未找到节点类型实现: magic");
    }

    @Test
    // 覆盖场景：节点执行抛异常 —— 包装为"节点 [名称] 执行失败"
    void executeShouldWrapNodeFailureIntoApiException() {
        StubNode boom = new StubNode("llm", in -> {
            throw new RuntimeException("boom");
        });

        assertThatThrownBy(() -> engine(boom).execute(
                List.of(dto("s", "start"), dto("l", "炸裂节点", "llm", null)),
                edges(new String[]{"s", "l"}), Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("节点 [炸裂节点] 执行失败: boom");
    }

    // ---- 条件路由 ----

    @Test
    // 覆盖场景：condition_result=true —— 仅激活 condition="true" 分支，false 分支节点不执行
    void executeShouldActivateTrueBranchOnly() {
        StubNode cond = new StubNode("condition", in -> Map.of("condition_result", true));
        StubNode yes = new StubNode("sinkYes", in -> Map.of("via", "yes"));
        StubNode no = new StubNode("sinkNo", in -> Map.of("via", "no"));

        Map<String, Object> result = engine(cond, yes, no).execute(
                List.of(dto("s", "start"), dto("c", "condition"),
                        dto("y", "sinkYes"), dto("n", "sinkNo"), dto("e", "end")),
                edges(new String[]{"s", "c"},
                        new String[]{"c", "y", "true"}, new String[]{"c", "n", "false"},
                        new String[]{"y", "e"}, new String[]{"n", "e"}),
                Map.of());

        assertThat(yes.calls).hasValue(1);
        assertThat(no.calls).hasValue(0);
        assertThat(result).containsEntry("via", "yes");
    }

    @Test
    // 覆盖场景：condition_result=false —— 激活 false 分支
    void executeShouldActivateFalseBranchOnly() {
        StubNode cond = new StubNode("condition", in -> Map.of("condition_result", false));
        StubNode yes = new StubNode("sinkYes", in -> Map.of("via", "yes"));
        StubNode no = new StubNode("sinkNo", in -> Map.of("via", "no"));

        Map<String, Object> result = engine(cond, yes, no).execute(
                List.of(dto("s", "start"), dto("c", "condition"),
                        dto("y", "sinkYes"), dto("n", "sinkNo"), dto("e", "end")),
                edges(new String[]{"s", "c"},
                        new String[]{"c", "y", "true"}, new String[]{"c", "n", "false"}),
                Map.of());

        assertThat(no.calls).hasValue(1);
        assertThat(yes.calls).hasValue(0);
        assertThat(result).containsEntry("via", "no");
    }

    @Test
    // 覆盖场景：condition 节点输出缺失 condition_result —— 按 false 处理
    void executeShouldTreatMissingConditionResultAsFalse() {
        StubNode cond = new StubNode("condition", in -> Map.of());
        StubNode yes = new StubNode("sinkYes", in -> Map.of());
        StubNode no = new StubNode("sinkNo", in -> Map.of("via", "no"));

        engine(cond, yes, no).execute(
                List.of(dto("s", "start"), dto("c", "condition"),
                        dto("y", "sinkYes"), dto("n", "sinkNo")),
                edges(new String[]{"s", "c"},
                        new String[]{"c", "y", "true"}, new String[]{"c", "n", "false"}),
                Map.of());

        assertThat(no.calls).hasValue(1);
        assertThat(yes.calls).hasValue(0);
    }

    @Test
    // 覆盖场景：普通节点（非 condition）挂条件边 —— 直接跳过不激活
    void executeShouldSkipConditionalEdgeFromNormalNode() {
        StubNode llm = new StubNode("llm", in -> Map.of("llm_output", "x"));
        StubNode gated = new StubNode("sinkGated", in -> Map.of("gated", true));

        Map<String, Object> result = engine(llm, gated).execute(
                List.of(dto("s", "start"), dto("l", "llm"), dto("g", "sinkGated")),
                edges(new String[]{"s", "l"}, new String[]{"l", "g", "true"}),
                Map.of());

        assertThat(gated.calls).hasValue(0);
        assertThat(result).doesNotContainKey("gated");
    }

    // ---- 串行批次 ----

    @Test
    // 覆盖场景：任一就绪节点配置 parallel=false —— 整轮降级为串行执行且全部完成
    void executeShouldFallBackToSerialBatchWhenParallelDisabled() {
        StubNode x = new StubNode("sinkX", in -> Map.of("x", 1));
        StubNode y = new StubNode("sinkY", in -> Map.of("y", 2));

        Map<String, Object> result = engine(x, y).execute(
                List.of(dto("s", "start"),
                        dto("x", "sinkX", Map.of("parallel", false)),
                        dto("y", "sinkY")),
                edges(new String[]{"s", "x"}, new String[]{"s", "y"}),
                Map.of());

        assertThat(x.calls).hasValue(1);
        assertThat(y.calls).hasValue(1);
        assertThat(result).containsEntry("x", 1).containsEntry("y", 2);
    }

    // ---- 重试 ----

    @Test
    // 覆盖场景：节点配置 retry.maxRetries —— 前两次失败第三次成功（共执行 3 次）
    void executeShouldRetryFailingNodeUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        StubNode flaky = new StubNode("llm", in -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("临时故障");
            }
            return Map.of("llm_output", "final");
        });

        Map<String, Object> result = engine(flaky).execute(
                List.of(dto("s", "start"),
                        dto("l", "llm", Map.of("retry", Map.of("maxRetries", 3, "delayMs", 1)))),
                edges(new String[]{"s", "l"}), Map.of());

        assertThat(attempts).hasValue(3);
        assertThat(result).containsEntry("llm_output", "final");
    }

    @Test
    // 覆盖场景：retry.enabled=false —— 不重试，仅执行一次即失败
    void executeShouldNotRetryWhenRetryDisabled() {
        StubNode alwaysFail = new StubNode("llm", in -> {
            throw new RuntimeException("hard fail");
        });

        assertThatThrownBy(() -> engine(alwaysFail).execute(
                List.of(dto("s", "start"),
                        dto("l", "llm", Map.of("retry",
                                Map.of("enabled", false, "maxRetries", 5, "delayMs", 1)))),
                edges(new String[]{"s", "l"}), Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hard fail");

        assertThat(alwaysFail.calls).hasValue(1);
    }

    @Test
    // 覆盖场景：重试次数耗尽 —— 异常最终向外抛出
    void executeShouldFailAfterRetriesExhausted() {
        StubNode alwaysFail = new StubNode("llm", in -> {
            throw new RuntimeException("依旧失败");
        });

        assertThatThrownBy(() -> engine(alwaysFail).execute(
                List.of(dto("s", "start"),
                        dto("l", "llm", Map.of("retry", Map.of("maxRetries", 2, "delayMs", 1)))),
                edges(new String[]{"s", "l"}), Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("节点 [l] 执行失败");

        // 首次 + 2 次重试 = 3 次
        assertThat(alwaysFail.calls).hasValue(3);
    }

    // ---- 事件监听 ----

    @Test
    // 覆盖场景：成功/失败节点均回调事件；监听器内部抛异常被吞掉不影响主流程
    void executeShouldEmitNodeEventsAndSwallowListenerErrors() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        StubNode llm = new StubNode("llm", in -> Map.of("llm_output", "ok"));
        StubNode boom = new StubNode("sinkBoom", in -> {
            throw new RuntimeException("listener-check");
        });

        WorkflowExecutionEngine.NodeEventListener listener =
                (nodeId, nodeType, status, outputs, durationMs, errorMessage) -> {
                    events.add(nodeId + ":" + status);
                    // 故意在回调中抛异常，引擎应吞掉
                    if ("llm".equals(nodeType)) {
                        throw new IllegalStateException("回调故障");
                    }
                };

        // 成功节点：回调抛异常被吞掉；失败节点：回调 status=failed 后引擎抛 ApiException
        assertThatThrownBy(() -> engine(llm, boom).execute(
                List.of(dto("s", "start"), dto("l", "llm"), dto("b", "sinkBoom")),
                edges(new String[]{"s", "l"}, new String[]{"l", "b"}),
                Map.of(), listener))
                .isInstanceOf(ApiException.class);

        assertThat(events).containsExactly("l:success", "b:failed");
        assertThat(llm.calls).hasValue(1);
    }

    // ---- 容错：脏边数据 ----

    @Test
    // 覆盖场景：脏边数据 —— source/target 缺失的边被忽略、指向不存在节点的边仅告警不崩溃
    void executeShouldIgnoreMalformedEdges() {
        StubNode llm = new StubNode("llm", in -> Map.of("llm_output", "v"));

        JSONObject missingTarget = new JSONObject();
        missingTarget.put("source", "l"); // 无 target
        JSONObject missingSource = new JSONObject();
        missingSource.put("target", "l"); // 无 source

        Map<String, Object> result = engine(llm).execute(
                List.of(dto("s", "start"), dto("l", "llm"), dto("e", "end")),
                edges(new String[]{"s", "l"}, new String[]{"l", "ghost-node"},
                        new String[]{"l", "e"}),
                Map.of());
        // 追加两条脏边（放入同一 JSONArray 再次执行验证）
        JSONArray withDirty = edges(new String[]{"s", "l"}, new String[]{"l", "e"});
        withDirty.add(missingTarget);
        withDirty.add(missingSource);

        Map<String, Object> result2 = engine(llm).execute(
                List.of(dto("s", "start"), dto("l", "llm"), dto("e", "end")),
                withDirty, Map.of());

        assertThat(result).containsEntry("llm_output", "v");
        assertThat(result2).containsEntry("llm_output", "v");
    }
}
