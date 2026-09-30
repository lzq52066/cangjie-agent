package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.core.harness.AgentRunRecorder;
import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.ResumeState;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.observability.context.TraceContext;
import cn.cangjiecloud.observability.dto.AgentRunDetailVO;
import cn.cangjiecloud.observability.dto.AgentRunQueryDTO;
import cn.cangjiecloud.observability.entity.AgentApprovalEntity;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.mapper.AgentApprovalMapper;
import cn.cangjiecloud.observability.mapper.AgentRunMapper;
import cn.cangjiecloud.observability.mapper.AgentRunStepMapper;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link AgentRunServiceImpl} 单元测试。
 * <p>
 * 通过 spy 拦截 {@code ServiceImpl} 继承的 save/update/page/list/getById 等数据库方法，
 * 业务异步执行器替换为直接执行（{@code Runnable::run}）以便同步断言；
 * LambdaQueryWrapper 的列名解析依赖 {@link TableInfoHelper} 预初始化，不连接真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class AgentRunServiceImplTest {

    @Mock
    private AgentRunStepMapper stepMapper;

    @Mock
    private AgentApprovalMapper approvalMapper;

    @Mock
    private ILlmTraceService llmTraceService;

    @Mock
    private AgentRunMapper runMapper;

    private AgentRunServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        initTableInfo(AgentRunMapper.class, AgentRunEntity.class);
        initTableInfo(AgentRunStepMapper.class, AgentRunStepEntity.class);
        initTableInfo(AgentApprovalMapper.class, AgentApprovalEntity.class);
    }

    private static void initTableInfo(Class<?> mapper, Class<?> entity) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(mapper.getName());
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        service = spy(new AgentRunServiceImpl(stepMapper, approvalMapper, llmTraceService,
                (Executor) Runnable::run));
        ReflectionTestUtils.setField(service, "baseMapper", runMapper);
        ReflectionTestUtils.setField(service, "maxStepContentLength", 4000);
        ReflectionTestUtils.setField(service, "recordSteps", true);
        ReflectionTestUtils.setField(service, "retentionDays", 30);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    // ==================== startRun ====================

    @Nested
    class StartRun {

        @Test
        void shouldFillEntityAndReturnGeneratedId() {
            doAnswer(invocation -> {
                AgentRunEntity entity = invocation.getArgument(0);
                entity.setId("run-100");
                return true;
            }).when(service).save(any(AgentRunEntity.class));

            HarnessConfig config = new HarnessConfig();
            config.setRunTokenBudget(500L);
            HarnessRequest request = HarnessRequest.builder()
                    .traceId("trace-1")
                    .sessionId("session-1")
                    .userId("user-1")
                    .applicationId("app-1")
                    .applicationName("demo-app")
                    .modelId("model-1")
                    .modelName("gpt-4o")
                    .parentRunId("parent-1")
                    .depth(2)
                    .config(config)
                    .build();

            String runId = service.startRun(request);

            assertThat(runId).isEqualTo("run-100");
            assertThat(request.getRunId()).isEqualTo("run-100");

            ArgumentCaptor<AgentRunEntity> captor = ArgumentCaptor.forClass(AgentRunEntity.class);
            verify(service).save(captor.capture());
            AgentRunEntity saved = captor.getValue();
            assertThat(saved.getTraceId()).isEqualTo("trace-1");
            assertThat(saved.getSessionId()).isEqualTo("session-1");
            assertThat(saved.getUserId()).isEqualTo("user-1");
            assertThat(saved.getAppId()).isEqualTo("app-1");
            assertThat(saved.getAppName()).isEqualTo("demo-app");
            assertThat(saved.getModelId()).isEqualTo("model-1");
            assertThat(saved.getModelName()).isEqualTo("gpt-4o");
            assertThat(saved.getParentRunId()).isEqualTo("parent-1");
            assertThat(saved.getDepth()).isEqualTo(2);
            assertThat(saved.getStatus()).isEqualTo(RunStatus.RUNNING.value());
            assertThat(saved.getRounds()).isZero();
            assertThat(saved.getToolCallCount()).isZero();
            assertThat(saved.getInputTokens()).isZero();
            assertThat(saved.getTokenBudget()).isEqualTo(500L);
            assertThat(saved.getStartTime()).isNotNull();
        }

        @Test
        void shouldDefaultHarnessTypeToChatAndFallbackToTraceContext() {
            doAnswer(invocation -> {
                AgentRunEntity entity = invocation.getArgument(0);
                entity.setId("run-101");
                return true;
            }).when(service).save(any(AgentRunEntity.class));
            TraceContext.setTraceId("ctx-trace");
            TraceContext.setSessionId("ctx-session");
            TraceContext.setUserId("ctx-user");

            HarnessRequest request = HarnessRequest.builder().harnessType(" ").build();

            service.startRun(request);

            ArgumentCaptor<AgentRunEntity> captor = ArgumentCaptor.forClass(AgentRunEntity.class);
            verify(service).save(captor.capture());
            AgentRunEntity saved = captor.getValue();
            assertThat(saved.getHarnessType()).isEqualTo("chat");
            assertThat(saved.getTraceId()).isEqualTo("ctx-trace");
            assertThat(saved.getSessionId()).isEqualTo("ctx-session");
            assertThat(saved.getUserId()).isEqualTo("ctx-user");
            assertThat(saved.getTokenBudget()).isNull();
        }

        @Test
        void shouldDegradeToRequestRunIdWhenSaveFails() {
            doThrow(new RuntimeException("db down")).when(service).save(any(AgentRunEntity.class));

            HarnessRequest request = HarnessRequest.builder().runId("run-existed").build();

            assertThat(service.startRun(request)).isEqualTo("run-existed");
        }

        @Test
        void shouldGenerateFallbackIdWhenSaveFailsWithoutRequestRunId() {
            doThrow(new RuntimeException("db down")).when(service).save(any(AgentRunEntity.class));

            String runId = service.startRun(HarnessRequest.builder().build());

            assertThat(runId).isNotBlank().hasSize(32);
        }
    }

    // ==================== recordContext ====================

    @Nested
    class RecordContext {

        @Test
        void shouldSkipBlankRunIdOrEmptyFragments() {
            service.recordContext(null, List.of(buildFragment()));
            service.recordContext(" ", List.of(buildFragment()));
            service.recordContext("run-1", List.of());
            service.recordContext("run-1", null);

            verify(service, never()).update(any(Wrapper.class));
        }

        @Test
        void shouldWriteContextUsageJson() {
            doReturn(true).when(service).update(any(Wrapper.class));

            ContextFragment fragment = ContextFragment.builder()
                    .slot(ContextSlot.HISTORY)
                    .source("promptTemplate")
                    .messages(List.of(ChatMessage.user("hi"), ChatMessage.assistant("hello")))
                    .estTokens(12)
                    .budgetTokens(100)
                    .droppedTokens(3)
                    .build();

            service.recordContext("run-1", List.of(fragment));

            LambdaUpdateWrapper<AgentRunEntity> wrapper = captureUpdateWrapper();
            assertThat(wrapper.getSqlSet()).contains("context_usage");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .anyMatch(v -> v instanceof String json && json.contains("\"slot\":\"history\"")
                            && json.contains("\"source\":\"promptTemplate\"")
                            && json.contains("\"messages\":2")
                            && json.contains("\"estTokens\":12"));
        }

        @Test
        void shouldUseUnknownSlotAndEmptySourceWhenFragmentMetadataMissing() {
            doReturn(true).when(service).update(any(Wrapper.class));

            ContextFragment fragment = ContextFragment.builder().build();

            service.recordContext("run-1", List.of(fragment));

            LambdaUpdateWrapper<AgentRunEntity> wrapper = captureUpdateWrapper();
            assertThat(wrapper.getParamNameValuePairs().values())
                    .anyMatch(v -> v instanceof String json && json.contains("\"slot\":\"unknown\"")
                            && json.contains("\"source\":\"\""));
        }

        @Test
        void shouldSwallowUpdateFailure() {
            doThrow(new RuntimeException("db down")).when(service).update(any(Wrapper.class));

            assertThatCode(() -> service.recordContext("run-1", List.of(buildFragment())))
                    .doesNotThrowAnyException();
        }

        @SuppressWarnings("unchecked")
        private LambdaUpdateWrapper<AgentRunEntity> captureUpdateWrapper() {
            ArgumentCaptor<Wrapper<AgentRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).update(captor.capture());
            return (LambdaUpdateWrapper<AgentRunEntity>) captor.getValue();
        }

        private ContextFragment buildFragment() {
            return ContextFragment.builder().slot(ContextSlot.SYSTEM).source("s").build();
        }
    }

    // ==================== recordStep ====================

    @Nested
    class RecordStep {

        @Test
        void shouldSkipWhenDisabledOrRecordInvalid() {
            ReflectionTestUtils.setField(service, "recordSteps", false);
            service.recordStep(AgentRunRecorder.StepRecord.builder().runId("run-1").build());
            verify(stepMapper, never()).insert(any(AgentRunStepEntity.class));

            ReflectionTestUtils.setField(service, "recordSteps", true);
            service.recordStep(null);
            service.recordStep(AgentRunRecorder.StepRecord.builder().runId(" ").build());
            verify(stepMapper, never()).insert(any(AgentRunStepEntity.class));
        }

        @Test
        void shouldInsertStepEntityWithTruncationAndTimeMapping() {
            long startMs = Instant.parse("2026-01-02T03:04:05Z").toEpochMilli();
            long endMs = startMs + 1500;
            String longInput = "i".repeat(4200);
            String longOutput = "o".repeat(4100);

            service.recordStep(AgentRunRecorder.StepRecord.builder()
                    .runId("run-1")
                    .stepNo(1)
                    .round(2)
                    .type("llm")
                    .name("gpt-4o")
                    .status("success")
                    .input(longInput)
                    .output(longOutput)
                    .inputTokens(10L)
                    .outputTokens(20L)
                    .durationMs(1500L)
                    .startMs(startMs)
                    .endMs(endMs)
                    .build());

            ArgumentCaptor<AgentRunStepEntity> captor = ArgumentCaptor.forClass(AgentRunStepEntity.class);
            verify(stepMapper).insert(captor.capture());
            AgentRunStepEntity entity = captor.getValue();
            assertThat(entity.getRunId()).isEqualTo("run-1");
            assertThat(entity.getStepNo()).isEqualTo(1);
            assertThat(entity.getRound()).isEqualTo(2);
            assertThat(entity.getType()).isEqualTo("llm");
            assertThat(entity.getName()).isEqualTo("gpt-4o");
            assertThat(entity.getInput()).hasSize(4000 + "...[truncated]".length()).endsWith("...[truncated]");
            assertThat(entity.getOutput()).hasSize(4000 + "...[truncated]".length()).endsWith("...[truncated]");
            assertThat(entity.getTruncated()).isEqualTo(1);
            assertThat(entity.getInputTokens()).isEqualTo(10L);
            assertThat(entity.getOutputTokens()).isEqualTo(20L);
            assertThat(entity.getDuration()).isEqualTo(1500L);
            assertThat(entity.getStartTime()).isEqualTo(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), ZoneId.systemDefault()));
            assertThat(entity.getEndTime()).isEqualTo(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(endMs), ZoneId.systemDefault()));
        }

        @Test
        void shouldLeaveTimesNullForNonPositiveEpochAndNotMarkShortContent() {
            service.recordStep(AgentRunRecorder.StepRecord.builder()
                    .runId("run-1")
                    .startMs(0)
                    .endMs(-1)
                    .input("short")
                    .output("short")
                    .build());

            ArgumentCaptor<AgentRunStepEntity> captor = ArgumentCaptor.forClass(AgentRunStepEntity.class);
            verify(stepMapper).insert(captor.capture());
            AgentRunStepEntity entity = captor.getValue();
            assertThat(entity.getStartTime()).isNull();
            assertThat(entity.getEndTime()).isNull();
            assertThat(entity.getTruncated()).isZero();
            assertThat(entity.getInput()).isEqualTo("short");
        }

        @Test
        void shouldSwallowInsertFailure() {
            doThrow(new RuntimeException("db down")).when(stepMapper).insert(any(AgentRunStepEntity.class));

            assertThatCode(() -> service.recordStep(AgentRunRecorder.StepRecord.builder()
                    .runId("run-1").build())).doesNotThrowAnyException();
        }
    }

    // ==================== checkpoint ====================

    @Nested
    class Checkpoint {

        @Test
        void shouldReturnOneTimeTokenAndPersistSnapshot() {
            doReturn(true).when(service).update(any(Wrapper.class));
            HarnessContext context = new HarnessContext(HarnessRequest.builder().build());
            context.setRunId("run-1");
            context.nextRound();
            context.nextRound();

            String token = service.checkpoint(context, RunStatus.WAITING_APPROVAL);

            assertThat(token).isNotNull().hasSize(32);
            ArgumentCaptor<Wrapper<AgentRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).update(captor.capture());
            LambdaUpdateWrapper<AgentRunEntity> wrapper = (LambdaUpdateWrapper<AgentRunEntity>) captor.getValue();
            assertThat(wrapper.getSqlSet()).contains("status", "context_snapshot", "resume_token", "rounds");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains(RunStatus.WAITING_APPROVAL.value(), token);
        }

        @Test
        void shouldReturnNullWhenUpdateFails() {
            doThrow(new RuntimeException("db down")).when(service).update(any(Wrapper.class));
            HarnessContext context = new HarnessContext(HarnessRequest.builder().build());
            context.setRunId("run-1");

            assertThat(service.checkpoint(context, RunStatus.WAITING_LOCAL)).isNull();
        }
    }

    // ==================== finishRun ====================

    @Nested
    class FinishRun {

        @Test
        void shouldSkipBlankRunIdOrNullOutcome() {
            service.finishRun(null, HarnessOutcome.builder().status(RunStatus.COMPLETED).build());
            service.finishRun(" ", HarnessOutcome.builder().status(RunStatus.COMPLETED).build());
            service.finishRun("run-1", null);

            verify(service, never()).update(any(Wrapper.class));
        }

        @Test
        void shouldWriteTerminalStateAndClearCheckpoint() {
            doReturn(true).when(service).update(any(Wrapper.class));

            service.finishRun("run-1", HarnessOutcome.builder()
                    .status(RunStatus.COMPLETED)
                    .rounds(3)
                    .toolCallCount(2)
                    .inputTokens(100L)
                    .outputTokens(50L)
                    .totalTokens(150L)
                    .finishReason("stop")
                    .finalText("x".repeat(4500))
                    .durationMs(1234L)
                    .build());

            LambdaUpdateWrapper<AgentRunEntity> wrapper = captureWrapper();
            assertThat(wrapper.getSqlSet()).contains("status", "rounds", "tool_call_count", "input_tokens",
                    "output_tokens", "total_tokens", "finish_reason", "final_text", "duration", "end_time",
                    "context_snapshot", "resume_token");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains(RunStatus.COMPLETED.value(), "stop", 1234L);
            assertThat(wrapper.getParamNameValuePairs().values()).anyMatch(v ->
                    v instanceof String text && text.endsWith("...[truncated]")
                            && text.length() == 4000 + "...[truncated]".length());
        }

        @Test
        void shouldKeepCheckpointForSuspendedStatus() {
            doReturn(true).when(service).update(any(Wrapper.class));

            service.finishRun("run-1", HarnessOutcome.builder()
                    .status(RunStatus.WAITING_APPROVAL)
                    .build());

            LambdaUpdateWrapper<AgentRunEntity> wrapper = captureWrapper();
            assertThat(wrapper.getSqlSet()).contains("status").doesNotContain("context_snapshot", "resume_token");
        }

        @Test
        void shouldSwallowUpdateFailure() {
            doThrow(new RuntimeException("db down")).when(service).update(any(Wrapper.class));

            assertThatCode(() -> service.finishRun("run-1",
                    HarnessOutcome.builder().status(RunStatus.FAILED).build())).doesNotThrowAnyException();
        }

        @SuppressWarnings("unchecked")
        private LambdaUpdateWrapper<AgentRunEntity> captureWrapper() {
            ArgumentCaptor<Wrapper<AgentRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).update(captor.capture());
            return (LambdaUpdateWrapper<AgentRunEntity>) captor.getValue();
        }
    }

    // ==================== abandonWaitingRun ====================

    @Nested
    class AbandonWaitingRun {

        @Test
        void shouldReturnFalseWhenRunIdBlank() {
            assertThat(service.abandonWaitingRun(" ", "reason", "error")).isFalse();
            verify(service, never()).update(any(Wrapper.class));
        }

        @Test
        void shouldMarkFailedAndFillDurationFromStartTime() {
            doReturn(true).when(service).update(any(Wrapper.class));
            AgentRunEntity run = new AgentRunEntity();
            run.setStartTime(LocalDateTime.now().minusSeconds(60));
            doReturn(run).when(service).getById("run-1");

            boolean abandoned = service.abandonWaitingRun("run-1", "approval_expired", "审批超时未决策，运行已终止");

            assertThat(abandoned).isTrue();
            LambdaUpdateWrapper<AgentRunEntity> wrapper = captureWrapper();
            assertThat(wrapper.getSqlSet()).contains("status", "finish_reason", "error_message",
                    "end_time", "context_snapshot", "resume_token", "duration");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains(RunStatus.FAILED.value(), "approval_expired", "审批超时未决策，运行已终止");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .anyMatch(v -> v instanceof Long duration && duration >= 59_000L);
        }

        @Test
        void shouldSkipDurationWhenRunMissing() {
            doReturn(true).when(service).update(any(Wrapper.class));
            doReturn(null).when(service).getById("run-1");

            assertThat(service.abandonWaitingRun("run-1", "reason", "error")).isTrue();

            LambdaUpdateWrapper<AgentRunEntity> wrapper = captureWrapper();
            assertThat(wrapper.getSqlSet()).doesNotContain("duration");
        }

        @SuppressWarnings("unchecked")
        private LambdaUpdateWrapper<AgentRunEntity> captureWrapper() {
            ArgumentCaptor<Wrapper<AgentRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).update(captor.capture());
            return (LambdaUpdateWrapper<AgentRunEntity>) captor.getValue();
        }
    }

    // ==================== loadPaused ====================

    @Nested
    class LoadPaused {

        @Test
        void shouldReturnNullWhenNotSuspendedOrSnapshotMissingOrTokenMismatch() {
            assertThat(service.loadPaused("run-1", "tok")).isNull();

            AgentRunEntity noSnapshot = new AgentRunEntity();
            noSnapshot.setStatus(RunStatus.WAITING_APPROVAL.value());
            noSnapshot.setResumeToken("tok");
            doReturn(noSnapshot).when(service).getById("run-1");
            assertThat(service.loadPaused("run-1", "tok")).isNull();

            AgentRunEntity running = new AgentRunEntity();
            running.setStatus(RunStatus.RUNNING.value());
            running.setContextSnapshot("{}");
            running.setResumeToken("tok");
            doReturn(running).when(service).getById("run-2");
            assertThat(service.loadPaused("run-2", "tok")).isNull();

            AgentRunEntity wrongToken = new AgentRunEntity();
            wrongToken.setStatus(RunStatus.WAITING_LOCAL.value());
            wrongToken.setContextSnapshot("{}");
            wrongToken.setResumeToken("tok-1");
            doReturn(wrongToken).when(service).getById("run-3");
            assertThat(service.loadPaused("run-3", "tok-2")).isNull();

            verify(service, never()).update(any(Wrapper.class));
        }

        @Test
        void shouldConsumeTokenAndReturnPausedRun() {
            doReturn(true).when(service).update(any(Wrapper.class));
            ResumeState state = ResumeState.builder()
                    .runId("run-1")
                    .round(2)
                    .stepNo(3)
                    .inputTokens(11L)
                    .outputTokens(22L)
                    .totalTokens(33L)
                    .toolCallCount(4)
                    .spentTokens(33L)
                    .elapsedMs(1500L)
                    .build();
            AgentRunEntity run = new AgentRunEntity();
            run.setStatus(RunStatus.WAITING_APPROVAL.value());
            run.setContextSnapshot(cn.cangjiecloud.common.util.JsonUtils.toJSONString(state));
            run.setResumeToken("tok-1");
            run.setTraceId("trace-1");
            run.setAppId("app-1");
            run.setAppName("demo-app");
            run.setSessionId("session-1");
            run.setUserId("user-1");
            run.setModelId("model-1");
            run.setModelName("gpt-4o");
            run.setHarnessType("chat");
            run.setParentRunId("parent-1");
            doReturn(run).when(service).getById("run-1");
            AgentApprovalEntity approval = new AgentApprovalEntity();
            approval.setId("approval-1");
            doReturn(approval).when(approvalMapper).selectOne(any(Wrapper.class));

            AgentRunRecorder.PausedRun paused = service.loadPaused("run-1", "tok-1");

            assertThat(paused).isNotNull();
            assertThat(paused.getRunId()).isEqualTo("run-1");
            assertThat(paused.getStatus()).isEqualTo(RunStatus.WAITING_APPROVAL.value());
            assertThat(paused.getState().getRound()).isEqualTo(2);
            assertThat(paused.getState().getStepNo()).isEqualTo(3);
            assertThat(paused.getState().getTotalTokens()).isEqualTo(33L);
            assertThat(paused.getApprovalId()).isEqualTo("approval-1");
            assertThat(paused.getTraceId()).isEqualTo("trace-1");
            assertThat(paused.getApplicationId()).isEqualTo("app-1");
            assertThat(paused.getSessionId()).isEqualTo("session-1");
            assertThat(paused.getUserId()).isEqualTo("user-1");
            assertThat(paused.getModelName()).isEqualTo("gpt-4o");
            assertThat(paused.getParentRunId()).isEqualTo("parent-1");
            assertThat(paused.getDepth()).isZero();

            ArgumentCaptor<Wrapper<AgentRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).update(captor.capture());
            LambdaUpdateWrapper<AgentRunEntity> wrapper = (LambdaUpdateWrapper<AgentRunEntity>) captor.getValue();
            assertThat(wrapper.getSqlSet()).contains("resume_token", "context_snapshot", "status");
            assertThat(wrapper.getParamNameValuePairs().values()).contains(RunStatus.RUNNING.value());
        }

        @Test
        void shouldReturnNullWhenParsedStateIsNullAndNotConsumeToken() {
            AgentRunEntity run = new AgentRunEntity();
            run.setStatus(RunStatus.WAITING_LOCAL.value());
            run.setContextSnapshot("null");
            run.setResumeToken("tok-1");
            doReturn(run).when(service).getById("run-1");

            assertThat(service.loadPaused("run-1", "tok-1")).isNull();

            verify(service, never()).update(any(Wrapper.class));
        }

        @Test
        void shouldReturnPausedRunWithDefaultStateForEmptyJsonSnapshot() {
            doReturn(true).when(service).update(any(Wrapper.class));
            AgentRunEntity run = new AgentRunEntity();
            run.setStatus(RunStatus.WAITING_LOCAL.value());
            run.setContextSnapshot("{}");
            run.setResumeToken("tok-1");
            doReturn(run).when(service).getById("run-1");

            AgentRunRecorder.PausedRun paused = service.loadPaused("run-1", "tok-1");

            assertThat(paused).isNotNull();
            assertThat(paused.getState()).isNotNull();
            assertThat(paused.getState().getRound()).isZero();
            assertThat(paused.getApprovalId()).isNull();
        }
    }

    // ==================== 查询 ====================

    @Nested
    class PageQuery {

        @Test
        void shouldApplyAllFiltersAndPaging() {
            doReturn(new Page<>(2, 20)).when(service).page(any(IPage.class), any(Wrapper.class));
            AgentRunQueryDTO query = new AgentRunQueryDTO();
            query.setTraceId("trace-1");
            query.setSessionId("session-1");
            query.setAppId("app-1");
            query.setUserId("user-1");
            query.setStatus("running");
            query.setHarnessType("chat");
            query.setParentRunId("parent-1");
            query.setModelName("gpt-4o");
            query.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
            query.setEndTime(LocalDateTime.of(2026, 2, 1, 0, 0));
            query.setPageNum(2);
            query.setPageSize(20);

            service.pageQuery(query);

            ArgumentCaptor<IPage<AgentRunEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<AgentRunEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());
            assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
            assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
            LambdaQueryWrapper<AgentRunEntity> wrapper = (LambdaQueryWrapper<AgentRunEntity>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).contains("trace_id", "session_id", "app_id", "user_id",
                    "status", "harness_type", "parent_run_id", "model_name", "start_time");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
        }

        @Test
        void shouldUsePagingDefaultsAndSkipBlankFilters() {
            doReturn(new Page<>(1, 10)).when(service).page(any(IPage.class), any(Wrapper.class));
            AgentRunQueryDTO query = new AgentRunQueryDTO();
            query.setPageNum(null);
            query.setPageSize(null);

            service.pageQuery(query);

            ArgumentCaptor<IPage<AgentRunEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<AgentRunEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());
            assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
            assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
            LambdaQueryWrapper<AgentRunEntity> wrapper = (LambdaQueryWrapper<AgentRunEntity>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).doesNotContain("trace_id", "session_id");
        }
    }

    @Nested
    class Detail {

        @Test
        void shouldReturnNullWhenRunMissing() {
            assertThat(service.detail("run-none")).isNull();
            verify(stepMapper, never()).selectList(any());
        }

        @Test
        void shouldAssembleRunWithStepsTracesApprovalsAndChildren() {
            AgentRunEntity run = new AgentRunEntity();
            run.setId("run-1");
            doReturn(run).when(service).getById("run-1");
            AgentRunStepEntity step = new AgentRunStepEntity();
            step.setId("step-1");
            doReturn(List.of(step)).when(stepMapper).selectList(any());
            LlmTraceEntity trace = new LlmTraceEntity();
            trace.setId("llm-1");
            doReturn(List.of(trace)).when(llmTraceService).listByRun("run-1");
            AgentApprovalEntity approval = new AgentApprovalEntity();
            approval.setId("approval-1");
            doReturn(List.of(approval)).when(approvalMapper).selectList(any());
            AgentRunEntity child = new AgentRunEntity();
            child.setId("child-1");
            doReturn(List.of(child)).when(service).list(any(Wrapper.class));

            AgentRunDetailVO detail = service.detail("run-1");

            assertThat(detail).isNotNull();
            assertThat(detail.getRun()).isSameAs(run);
            assertThat(detail.getSteps()).containsExactly(step);
            assertThat(detail.getLlmTraces()).containsExactly(trace);
            assertThat(detail.getApprovals()).containsExactly(approval);
            assertThat(detail.getChildren()).containsExactly(child);
        }
    }

    @Nested
    class ListQueries {

        @Test
        void shouldQueryStepsByRunIdOrderedByStepNo() {
            AgentRunStepEntity step = new AgentRunStepEntity();
            step.setId("step-1");
            doReturn(List.of(step)).when(stepMapper).selectList(any());

            assertThat(service.listSteps("run-1")).containsExactly(step);

            ArgumentCaptor<Wrapper<AgentRunStepEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(stepMapper).selectList(captor.capture());
            LambdaQueryWrapper<AgentRunStepEntity> wrapper = (LambdaQueryWrapper<AgentRunStepEntity>) captor.getValue();
            assertThat(wrapper.getTargetSql()).contains("run_id");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").contains("step_no");
        }

        @Test
        void shouldQueryRunsBySessionOrderedByStartTime() {
            AgentRunEntity run = new AgentRunEntity();
            run.setId("run-1");
            doReturn(List.of(run)).when(service).list(any(Wrapper.class));

            assertThat(service.listBySession("session-1")).containsExactly(run);

            ArgumentCaptor<Wrapper<AgentRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).list(captor.capture());
            LambdaQueryWrapper<AgentRunEntity> wrapper = (LambdaQueryWrapper<AgentRunEntity>) captor.getValue();
            assertThat(wrapper.getTargetSql()).contains("session_id");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").contains("start_time");
        }
    }

    // ==================== scheduledPurge ====================

    @Nested
    class ScheduledPurge {

        @Test
        void shouldSkipWhenRetentionDaysNotPositive() {
            ReflectionTestUtils.setField(service, "retentionDays", 0);

            service.scheduledPurge();

            verify(stepMapper, never()).purgeBatch(any(), anyInt());
            verify(runMapper, never()).purgeBatch(any(), anyInt());
        }

        @Test
        void shouldPurgeInBatchesUntilNotFull() {
            doReturn(2000, 100).when(stepMapper).purgeBatch(any(LocalDateTime.class), eq(2000));
            doReturn(2000, 5).when(runMapper).purgeBatch(any(LocalDateTime.class), eq(2000));

            service.scheduledPurge();

            verify(stepMapper, times(2)).purgeBatch(any(LocalDateTime.class), eq(2000));
            verify(runMapper, times(2)).purgeBatch(any(LocalDateTime.class), eq(2000));
            ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(stepMapper, times(2)).purgeBatch(cutoffCaptor.capture(), eq(2000));
            assertThat(cutoffCaptor.getValue())
                    .isBetween(LocalDateTime.now().minusDays(31), LocalDateTime.now().minusDays(29));
        }

        @Test
        void shouldStopImmediatelyWhenNoRowsPurged() {
            doReturn(0).when(stepMapper).purgeBatch(any(LocalDateTime.class), eq(2000));
            doReturn(0).when(runMapper).purgeBatch(any(LocalDateTime.class), eq(2000));

            service.scheduledPurge();

            verify(stepMapper, times(1)).purgeBatch(any(LocalDateTime.class), eq(2000));
            verify(runMapper, times(1)).purgeBatch(any(LocalDateTime.class), eq(2000));
        }
    }

}
