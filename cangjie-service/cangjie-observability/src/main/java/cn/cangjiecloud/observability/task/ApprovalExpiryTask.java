package cn.cangjiecloud.observability.task;

import cn.cangjiecloud.observability.service.IAgentApprovalService;
import cn.cangjiecloud.observability.service.IAgentRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 审批过期扫描任务。
 * <p>
 * run 挂起等待人工审批时不占线程，审批单超时后如果没人管，run 会永远停在
 * waiting_approval：检查点占库、前端会话被"存在待审批"卡死。本任务周期性兜底：
 * <ol>
 *   <li>把逾期仍 pending 的审批单 CAS 置为 expired（多实例并发安全）</li>
 *   <li>把受影响的 run 置为 failed 并清除检查点，run 不再可恢复</li>
 * </ol>
 * 默认决策只写入审批单留痕，不会替用户放行工具——超时的 run 一律终止而非自动续跑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalExpiryTask {

    private static final String FINISH_REASON = "approval_expired";
    private static final String ERROR_MESSAGE = "审批超时未决策，运行已终止";

    private final IAgentApprovalService approvalService;
    private final IAgentRunService runService;

    @Value("${cangjie.harness.approval-expiry-scan-enabled:true}")
    private boolean enabled;

    /** 超时默认决策留痕（reject/approve），仅影响审批单备注文案 */
    @Value("${cangjie.harness.approval-timeout-default-decision:reject}")
    private String defaultDecision;

    @Scheduled(fixedDelayString = "${cangjie.harness.approval-expiry-scan-interval-ms:60000}",
            initialDelayString = "${cangjie.harness.approval-expiry-scan-initial-delay-ms:10000}")
    public void expireOverdueApprovals() {
        if (!enabled) {
            return;
        }
        try {
            List<String> runIds = approvalService.expireOverdue(defaultDecision);
            if (runIds.isEmpty()) {
                return;
            }
            int terminated = 0;
            for (String runId : runIds.stream().filter(StringUtils::hasText).distinct().toList()) {
                try {
                    // CAS：若 run 恰好在置 expired 前一瞬被决策恢复，这里自然迁移失败、跳过
                    if (runService.abandonWaitingRun(runId, FINISH_REASON, ERROR_MESSAGE)) {
                        terminated++;
                    }
                } catch (Exception ex) {
                    log.error("审批超时终止 run 失败: runId={}", runId, ex);
                }
            }
            log.info("审批过期扫描完成: 审批单置 expired {} 张, 终止挂起 run {} 个", runIds.size(), terminated);
        } catch (Exception ex) {
            log.error("审批过期扫描任务异常", ex);
        }
    }
}
