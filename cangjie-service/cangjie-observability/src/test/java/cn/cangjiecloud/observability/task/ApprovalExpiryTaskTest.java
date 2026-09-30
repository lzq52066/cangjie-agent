package cn.cangjiecloud.observability.task;

import cn.cangjiecloud.observability.service.IAgentApprovalService;
import cn.cangjiecloud.observability.service.IAgentRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalExpiryTask} 单元测试。
 * <p>
 * 覆盖扫描开关、默认决策透传、受影响 run 的去重过滤与逐个 CAS 终止，
 * 以及单个 run 终止失败或审批扫描整体异常时的隔离兜底（任务不允许向调度器抛异常）。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalExpiryTaskTest {

    private static final String FINISH_REASON = "approval_expired";
    private static final String ERROR_MESSAGE = "审批超时未决策，运行已终止";

    @Mock
    private IAgentApprovalService approvalService;

    @Mock
    private IAgentRunService runService;

    private ApprovalExpiryTask task;

    @BeforeEach
    void setUp() {
        task = new ApprovalExpiryTask(approvalService, runService);
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "defaultDecision", "reject");
    }

    @Nested
    class ScanSwitch {

        @Test
        void shouldSkipWholeScanWhenDisabled() {
            ReflectionTestUtils.setField(task, "enabled", false);

            task.expireOverdueApprovals();

            verifyNoInteractions(approvalService, runService);
        }

        @Test
        void shouldPassConfiguredDefaultDecisionToScan() {
            ReflectionTestUtils.setField(task, "defaultDecision", "approve");
            when(approvalService.expireOverdue("approve")).thenReturn(Collections.emptyList());

            task.expireOverdueApprovals();

            verify(approvalService).expireOverdue("approve");
        }
    }

    @Nested
    class TerminateAffectedRuns {

        @Test
        void shouldNotTouchRunServiceWhenNothingExpired() {
            when(approvalService.expireOverdue("reject")).thenReturn(Collections.emptyList());

            task.expireOverdueApprovals();

            verifyNoInteractions(runService);
        }

        @Test
        void shouldAbandonEachAffectedRunWithExpiryReason() {
            when(approvalService.expireOverdue("reject")).thenReturn(List.of("run-1", "run-2"));
            when(runService.abandonWaitingRun(anyString(), anyString(), anyString())).thenReturn(true);

            task.expireOverdueApprovals();

            verify(runService).abandonWaitingRun("run-1", FINISH_REASON, ERROR_MESSAGE);
            verify(runService).abandonWaitingRun("run-2", FINISH_REASON, ERROR_MESSAGE);
            verify(runService, times(2)).abandonWaitingRun(anyString(), anyString(), anyString());
        }

        @Test
        void shouldDropBlankAndDuplicateRunIdsBeforeAbandoning() {
            when(approvalService.expireOverdue("reject"))
                    .thenReturn(Arrays.asList("run-1", "run-1", "  ", null, "run-2"));

            task.expireOverdueApprovals();

            verify(runService, times(1)).abandonWaitingRun("run-1", FINISH_REASON, ERROR_MESSAGE);
            verify(runService, times(1)).abandonWaitingRun("run-2", FINISH_REASON, ERROR_MESSAGE);
            verify(runService, times(2)).abandonWaitingRun(anyString(), anyString(), anyString());
        }

        @Test
        void shouldKeepGoingWhenCasOnOneRunFails() {
            when(approvalService.expireOverdue("reject")).thenReturn(List.of("run-bad", "run-good"));
            when(runService.abandonWaitingRun(eq("run-bad"), anyString(), anyString()))
                    .thenThrow(new RuntimeException("db down"));
            when(runService.abandonWaitingRun(eq("run-good"), anyString(), anyString())).thenReturn(true);

            assertThatCode(() -> task.expireOverdueApprovals()).doesNotThrowAnyException();

            verify(runService).abandonWaitingRun("run-bad", FINISH_REASON, ERROR_MESSAGE);
            verify(runService).abandonWaitingRun("run-good", FINISH_REASON, ERROR_MESSAGE);
        }

        @Test
        void shouldAlsoSwallowFalseCasResultWithoutException() {
            // run 恰在置 expired 前被决策恢复：CAS 返回 false，任务静默跳过、不影响其它 run
            when(approvalService.expireOverdue("reject")).thenReturn(List.of("run-raced"));
            when(runService.abandonWaitingRun(eq("run-raced"), anyString(), anyString())).thenReturn(false);

            assertThatCode(() -> task.expireOverdueApprovals()).doesNotThrowAnyException();

            verify(runService).abandonWaitingRun("run-raced", FINISH_REASON, ERROR_MESSAGE);
        }
    }

    @Nested
    class FailureIsolation {

        @Test
        void shouldSwallowApprovalScanFailure() {
            when(approvalService.expireOverdue("reject")).thenThrow(new RuntimeException("scan db down"));

            assertThatCode(() -> task.expireOverdueApprovals()).doesNotThrowAnyException();

            verifyNoInteractions(runService);
        }

        @Test
        void shouldHaveNoSideEffectOnRunsWhenScanFails() {
            when(approvalService.expireOverdue("reject")).thenThrow(new IllegalStateException("timeout"));

            assertThatCode(() -> task.expireOverdueApprovals()).doesNotThrowAnyException();

            verify(runService, never()).abandonWaitingRun(anyString(), anyString(), anyString());
        }
    }
}
