package cn.cangjiecloud.workflow.service;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.workflow.entity.WorkflowEntity;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEventEntity;
import cn.cangjiecloud.workflow.mapper.WorkflowExecutionEventMapper;
import cn.cangjiecloud.workflow.mapper.WorkflowExecutionMapper;
import cn.cangjiecloud.workflow.mapper.WorkflowMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowServiceImpl} 单元测试。
 * <p>
 * 全程不连数据库：spy 拦截 MyBatis-Plus 的 save/getById/updateById/removeById/page/getOne，
 * 执行引擎、执行记录服务、事件服务、TraceCollector 全部使用 mock，
 * businessExecutor 通过 ReflectionTestUtils 注入同步/录制执行器。
 */
class WorkflowServiceImplTest {

    private static final String NODES_JSON =
            "[{\"id\":\"s\",\"type\":\"start\",\"name\":\"开始\"},{\"id\":\"l\",\"type\":\"llm\",\"name\":\"大模型\"}]";
    private static final String EDGES_JSON = "[{\"source\":\"s\",\"target\":\"l\"}]";

    private WorkflowExecutionEngine executionEngine;
    private IWorkflowExecutionService workflowExecutionService;
    private IWorkflowExecutionEventService workflowExecutionEventService;
    private TraceCollector traceCollector;
    private List<Runnable> deferredTasks;
    private WorkflowServiceImpl service;

    @BeforeAll
    // 预注册三张表结构：让 LambdaQueryWrapper 的 SFunction -> 列名 解析在离线环境可用
    static void installTableInfo() {
        initTableInfo(WorkflowMapper.class, WorkflowEntity.class);
        initTableInfo(WorkflowExecutionMapper.class, WorkflowExecutionEntity.class);
        initTableInfo(WorkflowExecutionEventMapper.class, WorkflowExecutionEventEntity.class);
    }

    /** namespace 只能设置一次，故每个实体各用一个 MapperBuilderAssistant */
    private static void initTableInfo(Class<?> mapper, Class<?> entity) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(mapper.getName());
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        executionEngine = mock(WorkflowExecutionEngine.class);
        workflowExecutionService = mock(IWorkflowExecutionService.class);
        workflowExecutionEventService = mock(IWorkflowExecutionEventService.class);
        traceCollector = mock(TraceCollector.class);
        deferredTasks = new ArrayList<>();

        service = spy(new WorkflowServiceImpl(
                executionEngine, workflowExecutionService, workflowExecutionEventService));
        // 注入"录制任务"的异步执行器：不立即运行，便于断言异步语义；单测按需手动触发
        ReflectionTestUtils.setField(service, "businessExecutor", (Executor) deferredTasks::add);
        ReflectionTestUtils.setField(service, "traceCollector", traceCollector);
    }

    private static WorkflowEntity workflow(String id, String status, String nodes, String edges) {
        WorkflowEntity w = new WorkflowEntity();
        w.setId(id);
        w.setName("测试工作流");
        w.setStatus(status);
        w.setNodes(nodes);
        w.setEdges(edges);
        w.setApplicationId("app-1");
        w.setVersion(1);
        return w;
    }

    private WorkflowEntity stubPublishedWorkflow() {
        WorkflowEntity w = workflow("w1", "published", NODES_JSON, EDGES_JSON);
        doReturn(w).when(service).getById("w1");
        return w;
    }

    /** 打桩引擎正常返回，并返回捕获到的节点事件监听器 */
    private WorkflowExecutionEngine.NodeEventListener stubEngineSuccess(Map<String, Object> outputs) {
        when(executionEngine.execute(anyList(), any(ArrayNode.class), anyMap(),
                any(WorkflowExecutionEngine.NodeEventListener.class))).thenReturn(outputs);
        ArgumentCaptor<WorkflowExecutionEngine.NodeEventListener> captor =
                ArgumentCaptor.forClass(WorkflowExecutionEngine.NodeEventListener.class);
        return captor.getValue();
    }

    // ---------- create ----------

    @Test
    // 覆盖场景：创建时 status/version 缺省 —— 默认 draft 与版本 1
    void createShouldApplyDefaultsWhenStatusAndVersionMissing() {
        doReturn(true).when(service).save(any(WorkflowEntity.class));
        WorkflowEntity entity = new WorkflowEntity();
        entity.setName("新工作流");

        WorkflowEntity result = service.create(entity);

        assertThat(result.getStatus()).isEqualTo("draft");
        assertThat(result.getVersion()).isEqualTo(1);
        verify(service).save(entity);
    }

    @Test
    // 覆盖场景：创建时已带 status/version —— 原样保留
    void createShouldKeepProvidedStatusAndVersion() {
        doReturn(true).when(service).save(any(WorkflowEntity.class));
        WorkflowEntity entity = new WorkflowEntity();
        entity.setStatus("published");
        entity.setVersion(7);

        service.create(entity);

        assertThat(entity.getStatus()).isEqualTo("published");
        assertThat(entity.getVersion()).isEqualTo(7);
    }

    // ---------- update / delete ----------

    @Test
    // 覆盖场景：更新不存在的工作流 —— 抛"工作流不存在"
    void updateShouldThrowWhenWorkflowMissing() {
        doReturn(null).when(service).getById("nope");

        assertThatThrownBy(() -> service.update("nope", new WorkflowEntity()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("工作流不存在");
    }

    @Test
    // 覆盖场景：更新成功 —— 强制回填路径 id，并返回重新加载后的记录
    void updateShouldForceIdAndReturnReloaded() {
        WorkflowEntity existing = workflow("w1", "draft", null, null);
        WorkflowEntity reloaded = workflow("w1", "draft", "[]", null);
        doReturn(existing).doReturn(reloaded).when(service).getById("w1");
        doReturn(true).when(service).updateById(any(WorkflowEntity.class));
        WorkflowEntity payload = new WorkflowEntity();
        payload.setName("改名");

        WorkflowEntity result = service.update("w1", payload);

        assertThat(payload.getId()).isEqualTo("w1");
        assertThat(result).isSameAs(reloaded);
        verify(service).updateById(payload);
    }

    @Test
    // 覆盖场景：删除不存在的工作流 —— 静默返回且不调用 removeById
    void deleteShouldIgnoreWhenWorkflowMissing() {
        doReturn(null).when(service).getById("ghost");

        service.delete("ghost");

        verify(service, never()).removeById(any(Serializable.class));
    }

    @Test
    // 覆盖场景：删除存在的工作流 —— 按 id 删除
    void deleteShouldRemoveWhenWorkflowExists() {
        doReturn(workflow("w1", "draft", null, null)).when(service).getById("w1");
        doReturn(true).when(service).removeById(any(Serializable.class));

        service.delete("w1");

        verify(service).removeById("w1");
    }

    // ---------- pageQuery ----------

    @Test
    @SuppressWarnings("unchecked")
    // 覆盖场景：分页查询带关键字 —— name LIKE + create_time 倒序 + 显式分页参数
    void pageQueryShouldApplyKeywordAndPaging() {
        Page<WorkflowEntity> fake = new Page<>(2, 20);
        doReturn(fake).when(service).page(any(Page.class), any(Wrapper.class));

        IPage<WorkflowEntity> result = service.pageQuery("客服", 2, 20);

        assertThat(result).isSameAs(fake);
        ArgumentCaptor<Wrapper<WorkflowEntity>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(any(Page.class), cap.capture());
        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) cap.getValue();
        assertThat(wrapper.getTargetSql()).contains("name LIKE");
        assertThat(wrapper.getSqlSegment()).contains("ORDER BY create_time DESC");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly("客服");
    }

    @Test
    @SuppressWarnings("unchecked")
    // 覆盖场景：关键字为空 + 页码为 null —— 不加 LIKE 条件，默认 1/10 分页
    void pageQueryShouldDefaultPagingAndSkipKeyword() {
        Page<WorkflowEntity> fake = new Page<>();
        doReturn(fake).when(service).page(any(Page.class), any(Wrapper.class));

        service.pageQuery("  ", null, null);

        ArgumentCaptor<IPage<WorkflowEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<WorkflowEntity>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(pageCap.capture(), cap.capture());
        assertThat(pageCap.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCap.getValue().getSize()).isEqualTo(10);
        assertThat(((LambdaQueryWrapper<?>) cap.getValue()).getSqlSegment()).doesNotContain("LIKE");
    }

    // ---------- publish ----------

    @Test
    // 覆盖场景：发布不存在的工作流 —— 抛"工作流不存在"
    void publishShouldThrowWhenWorkflowMissing() {
        doReturn(null).when(service).getById("nope");

        assertThatThrownBy(() -> service.publish("nope"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("工作流不存在");
    }

    @Test
    // 覆盖场景：节点为空的工作流 —— 拒绝发布
    void publishShouldRejectWhenNodesBlank() {
        WorkflowEntity w = workflow("w1", "draft", "  ", null);
        doReturn(w).when(service).getById("w1");

        assertThatThrownBy(() -> service.publish("w1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("工作流节点为空，无法发布");
    }

    @Test
    // 覆盖场景：发布成功 —— 状态 published、版本自增
    void publishShouldIncrementVersionAndMarkPublished() {
        WorkflowEntity w = workflow("w1", "draft", NODES_JSON, EDGES_JSON);
        w.setVersion(3);
        WorkflowEntity reloaded = workflow("w1", "published", NODES_JSON, EDGES_JSON);
        reloaded.setVersion(4);
        doReturn(w).doReturn(reloaded).when(service).getById("w1");
        doReturn(true).when(service).updateById(any(WorkflowEntity.class));

        WorkflowEntity result = service.publish("w1");

        assertThat(w.getStatus()).isEqualTo("published");
        assertThat(w.getVersion()).isEqualTo(4);
        assertThat(result.getVersion()).isEqualTo(4);
        verify(service).updateById(w);
    }

    @Test
    // 覆盖场景：发布版本为 null —— 版本按 1 处理
    void publishShouldTreatNullVersionAsOne() {
        WorkflowEntity w = workflow("w1", "draft", NODES_JSON, null);
        w.setVersion(null);
        doReturn(w).when(service).getById("w1");
        doReturn(true).when(service).updateById(any(WorkflowEntity.class));

        service.publish("w1");

        assertThat(w.getVersion()).isEqualTo(1);
    }

    // ---------- getByApplicationId ----------

    @Test
    // 覆盖场景：applicationId 为空 —— 直接返回 null 且不查库
    void getByApplicationIdShouldReturnNullWhenBlank() {
        assertThat(service.getByApplicationId(null)).isNull();
        assertThat(service.getByApplicationId("")).isNull();
        verify(service, never()).getOne(any(Wrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    // 覆盖场景：查询已发布的最新版工作流 —— published + version 倒序 + LIMIT 1
    void getByApplicationIdShouldQueryLatestPublished() {
        WorkflowEntity w = workflow("w9", "published", null, null);
        doReturn(w).when(service).getOne(any(Wrapper.class));

        WorkflowEntity result = service.getByApplicationId("app-9");

        assertThat(result).isSameAs(w);
        ArgumentCaptor<Wrapper<WorkflowEntity>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).getOne(cap.capture());
        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) cap.getValue();
        assertThat(wrapper.getTargetSql())
                .contains("application_id")
                .contains("status");
        assertThat(wrapper.getSqlSegment()).contains("ORDER BY version DESC").contains("LIMIT 1");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly("app-9", "published");
    }

    // ---------- execute 守卫 ----------

    @Test
    // 覆盖场景：执行不存在的工作流 —— 抛"工作流不存在"
    void executeShouldThrowWhenWorkflowMissing() {
        doReturn(null).when(service).getById("nope");

        assertThatThrownBy(() -> service.execute("nope", Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("工作流不存在");
    }

    @Test
    // 覆盖场景：执行未发布的工作流 —— 抛"工作流未发布，无法执行"（异步入口同样受守卫保护）
    void executeShouldRejectUnpublishedWorkflow() {
        doReturn(workflow("w1", "draft", NODES_JSON, null)).when(service).getById("w1");

        assertThatThrownBy(() -> service.execute("w1", Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("工作流未发布，无法执行");
        assertThatThrownBy(() -> service.executeAsync("w1", Map.of()))
                .isInstanceOf(ApiException.class);
        assertThat(deferredTasks).isEmpty();
    }

    // ---------- execute 主流程 ----------

    @Test
    // 覆盖场景：同步执行成功 —— 执行记录 running→completed、输出落 JSON、trace 记 success
    void executeShouldCompleteAndPersistOutputsOnSuccess() {
        stubPublishedWorkflow();
        when(executionEngine.execute(anyList(), any(ArrayNode.class), anyMap(),
                any(WorkflowExecutionEngine.NodeEventListener.class)))
                .thenReturn(new HashMap<>(Map.of("llm_output", "你好")));

        WorkflowExecutionEntity execution = service.execute("w1", new HashMap<>(Map.of("question", "问题A")));

        assertThat(execution.getWorkflowId()).isEqualTo("w1");
        assertThat(execution.getApplicationId()).isEqualTo("app-1");
        assertThat(execution.getStatus()).isEqualTo("completed");
        assertThat(execution.getCurrentNode()).isEqualTo("end");
        assertThat(execution.getInputs()).contains("问题A");
        assertThat(execution.getOutputs()).contains("llm_output");
        assertThat(execution.getStartTime()).isNotNull();
        assertThat(execution.getEndTime()).isNotNull();
        assertThat(execution.getDuration()).isNotNull();

        ArgumentCaptor<WorkflowExecutionEntity> saveCap =
                ArgumentCaptor.forClass(WorkflowExecutionEntity.class);
        verify(workflowExecutionService).save(saveCap.capture());
        verify(workflowExecutionService).updateById(execution);
        verify(traceCollector).record(eq("workflow"), eq("execute"), any(), anyLong(),
                eq("success"), contains("测试工作流"));
    }

    @Test
    // 覆盖场景：引擎执行抛异常 —— 状态置 failed、记录 errorMessage、trace 记 fail、对外不抛
    void executeShouldMarkFailedWhenEngineThrows() {
        stubPublishedWorkflow();
        when(executionEngine.execute(anyList(), any(ArrayNode.class), anyMap(),
                any(WorkflowExecutionEngine.NodeEventListener.class)))
                .thenThrow(new ApiException("节点 [大模型] 执行失败: boom"));

        WorkflowExecutionEntity execution = service.execute("w1", new HashMap<>());

        assertThat(execution.getStatus()).isEqualTo("failed");
        assertThat(execution.getErrorMessage()).isEqualTo("节点 [大模型] 执行失败: boom");
        verify(workflowExecutionService).updateById(execution);
        verify(traceCollector).record(eq("workflow"), eq("execute"), any(), anyLong(),
                eq("fail"), contains("boom"));
    }

    @Test
    // 覆盖场景：节点定义为空数组 —— 执行前校验抛"工作流节点为空"并落 failed 状态
    void executeShouldFailFastWhenNodesEmpty() {
        stubPublishedWorkflow();
        WorkflowEntity emptyNodes = workflow("w1", "published", "[]", null);
        doReturn(emptyNodes).when(service).getById("w1");

        WorkflowExecutionEntity execution = service.execute("w1", null);

        assertThat(execution.getStatus()).isEqualTo("failed");
        assertThat(execution.getErrorMessage()).isEqualTo("工作流节点为空");
        verify(executionEngine, never()).execute(anyList(), any(ArrayNode.class), anyMap(),
                any(WorkflowExecutionEngine.NodeEventListener.class));
    }

    @Test
    // 覆盖场景：traceCollector 未装配（null）—— 成功路径不应抛 NPE
    void executeShouldTolerateNullTraceCollector() {
        ReflectionTestUtils.setField(service, "traceCollector", null);
        stubPublishedWorkflow();
        when(executionEngine.execute(anyList(), any(ArrayNode.class), anyMap(),
                any(WorkflowExecutionEngine.NodeEventListener.class)))
                .thenReturn(new HashMap<>());

        assertThat(service.execute("w1", null).getStatus()).isEqualTo("completed");
    }

    // ---------- executeAsync ----------

    @Test
    // 覆盖场景：异步执行 —— 提交后立即返回 running 记录，任务在 businessExecutor 中延后运行
    void executeAsyncShouldDeferExecutionToBusinessExecutor() {
        stubPublishedWorkflow();
        when(executionEngine.execute(anyList(), any(ArrayNode.class), anyMap(),
                any(WorkflowExecutionEngine.NodeEventListener.class)))
                .thenReturn(new HashMap<>(Map.of("k", "v")));

        WorkflowExecutionEntity execution = service.executeAsync("w1", new HashMap<>());

        assertThat(execution.getStatus()).isEqualTo("running");
        assertThat(deferredTasks).hasSize(1);
        verify(executionEngine, never()).execute(anyList(), any(ArrayNode.class), anyMap(),
                any(WorkflowExecutionEngine.NodeEventListener.class));

        // 手动驱动异步任务后完成
        deferredTasks.get(0).run();
        assertThat(execution.getStatus()).isEqualTo("completed");
        verify(executionEngine).execute(anyList(), any(ArrayNode.class), anyMap(),
                any(WorkflowExecutionEngine.NodeEventListener.class));
    }

    // ---------- 节点事件监听器 ----------

    @Test
    // 覆盖场景：节点事件落库 —— 超长输出截断到 4000 字符、失败事件带 errorMessage
    void eventListenerShouldPersistTruncatedOutputsAndErrors() {
        stubPublishedWorkflow();
        when(executionEngine.execute(anyList(), any(ArrayNode.class), anyMap(),
                any(WorkflowExecutionEngine.NodeEventListener.class)))
                .thenReturn(new HashMap<>());
        service.execute("w1", new HashMap<>());

        ArgumentCaptor<WorkflowExecutionEngine.NodeEventListener> captor =
                ArgumentCaptor.forClass(WorkflowExecutionEngine.NodeEventListener.class);
        verify(executionEngine).execute(anyList(), any(ArrayNode.class), anyMap(), captor.capture());
        WorkflowExecutionEngine.NodeEventListener listener = captor.getValue();

        // 成功事件：输出 JSON 远超 4000 字符，应被截断
        listener.onNodeCompleted("n1", "llm", "success",
                Map.of("payload", "x".repeat(5000)), 12L, null);
        // 失败事件：outputs 为 null，仅记录错误
        listener.onNodeCompleted("n2", "tool", "failed", null, 3L, "工具超时");

        ArgumentCaptor<WorkflowExecutionEventEntity> eventCap =
                ArgumentCaptor.forClass(WorkflowExecutionEventEntity.class);
        verify(workflowExecutionEventService, org.mockito.Mockito.times(2)).save(eventCap.capture());
        List<WorkflowExecutionEventEntity> events = eventCap.getAllValues();

        assertThat(events.get(0).getNodeId()).isEqualTo("n1");
        assertThat(events.get(0).getStatus()).isEqualTo("success");
        assertThat(events.get(0).getOutputs()).hasSize(4000);
        assertThat(events.get(0).getDuration()).isEqualTo(12L);
        assertThat(events.get(1).getOutputs()).isNull();
        assertThat(events.get(1).getErrorMessage()).isEqualTo("工具超时");
        assertThat(events.get(1).getNodeType()).isEqualTo("tool");
    }

    @Test
    // 覆盖场景：事件落库抛异常 —— 监听器内部吞掉，不影响主流程
    void eventListenerShouldSwallowPersistenceFailure() {
        stubPublishedWorkflow();
        when(executionEngine.execute(anyList(), any(ArrayNode.class), anyMap(),
                any(WorkflowExecutionEngine.NodeEventListener.class)))
                .thenReturn(new HashMap<>());
        service.execute("w1", new HashMap<>());

        ArgumentCaptor<WorkflowExecutionEngine.NodeEventListener> captor =
                ArgumentCaptor.forClass(WorkflowExecutionEngine.NodeEventListener.class);
        verify(executionEngine).execute(anyList(), any(ArrayNode.class), anyMap(), captor.capture());

        doThrow(new RuntimeException("db down")).when(workflowExecutionEventService)
                .save(any(WorkflowExecutionEventEntity.class));

        assertThatCode(() -> captor.getValue().onNodeCompleted(
                "n1", "llm", "success", Map.of("a", 1), 5L, null))
                .doesNotThrowAnyException();
    }
}
