package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.LoopPolicy;
import cn.cangjiecloud.core.harness.ModelSettings;
import cn.cangjiecloud.core.harness.context.ContextBudget;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Harness 执行参数解析器：全局默认（{@code cangjie.harness.*}）与应用级覆盖
 * （{@code application.config} 的 {@code harness} 节点）合并。
 * <p>
 * 未配置时产出的参数与改造前完全等价：轮次取 {@code cangjie.harness.max-rounds}
 * （默认 5，与旧 {@code cangjie.chat.agent.max-rounds} 同值），总超时默认 300 秒，
 * 温度取应用值否则 0.7。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HarnessConfigResolver {

    private static final String CONFIG_NODE = "harness";

    private final HarnessProperties properties;

    /**
     * 灰度总开关
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isSseToolEvents() {
        return properties.isSseToolEvents();
    }

    /**
     * 解析应用级覆盖配置（无配置或格式非法时返回 null，由调用方回落全局默认）
     */
    public HarnessConfig parse(ApplicationEntity application) {
        if (application == null || !StringUtils.hasText(application.getConfig())) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(application.getConfig());
            if (root == null || root.getJSONObject(CONFIG_NODE) == null) {
                return null;
            }
            return root.getJSONObject(CONFIG_NODE).toJavaObject(HarnessConfig.class);
        } catch (Exception e) {
            log.warn("解析应用 harness 配置失败，回落全局默认: appId={}, {}", application.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 循环策略
     */
    public LoopPolicy loopPolicy(HarnessConfig config) {
        return loopPolicy(config, properties.getMaxRounds(), properties.getTimeoutSeconds());
    }

    /**
     * 循环策略（未显式配置时回落到调用方给出的现网参数，保证灰度前后完全等价）
     *
     * @param fallbackMaxRounds    兜底最大轮次（对应 {@code cangjie.chat.agent.max-rounds}）
     * @param fallbackTimeoutSecs  兜底总超时秒数（对应 {@code cangjie.chat.agent.timeout-seconds}）
     */
    public LoopPolicy loopPolicy(HarnessConfig config, int fallbackMaxRounds, long fallbackTimeoutSecs) {
        HarnessConfig.Loop loop = config == null ? null : config.getLoop();
        int maxRounds = pick(loop == null ? null : loop.getMaxRounds(), fallbackMaxRounds);
        long timeout = pick(loop == null ? null : loop.getTimeoutSeconds(), fallbackTimeoutSecs);
        long toolTimeout = pick(loop == null ? null : loop.getToolTimeoutSeconds(),
                properties.getToolTimeoutSeconds());
        boolean parallel = loop != null && Boolean.TRUE.equals(loop.getParallelTools());
        return LoopPolicy.builder()
                .maxRounds(maxRounds)
                .timeoutSeconds(timeout)
                .toolTimeoutSeconds(toolTimeout)
                .parallelTools(parallel)
                .build();
    }

    /**
     * 采样参数（温度回落链：应用值 → 0.7，与改造前一致）
     */
    public ModelSettings modelSettings(ApplicationEntity application) {
        return ModelSettings.builder()
                .temperature(application == null || application.getTemperature() == null
                        ? 0.7 : application.getTemperature())
                .build();
    }

    /**
     * 上下文预算（默认关闭，关闭时行为与改造前一致）
     */
    public ContextBudget budget(HarnessConfig config) {
        HarnessConfig.Context ctx = config == null ? null : config.getContext();
        if (ctx == null || ctx.getBudgetEnabled() == null) {
            return properties.getContext().toBudget();
        }
        if (!Boolean.TRUE.equals(ctx.getBudgetEnabled()) || ctx.getBudgetTokens() == null
                || ctx.getBudgetTokens() <= 0) {
            return ContextBudget.disabled();
        }
        return ContextBudget.of(ctx.getBudgetTokens(),
                ctx.getSlotRatios() == null ? properties.getContext().getSlotRatios() : ctx.getSlotRatios());
    }

    /**
     * 单次 run 的 token 预算
     */
    public long runTokenBudget(HarnessConfig config) {
        return config == null || config.getRunTokenBudget() == null ? 0L : config.getRunTokenBudget();
    }

    /**
     * 是否启用审批拦截
     */
    public boolean approvalEnabled(HarnessConfig config) {
        HarnessConfig.Approval approval = config == null ? null : config.getApproval();
        if (approval == null || approval.getEnabled() == null) {
            return properties.isApprovalEnabled();
        }
        return Boolean.TRUE.equals(approval.getEnabled());
    }

    /**
     * 审批单有效期（秒）
     */
    public int approvalTimeoutSeconds(HarnessConfig config) {
        HarnessConfig.Approval approval = config == null ? null : config.getApproval();
        if (approval == null || approval.getTimeoutSeconds() == null) {
            return properties.getApprovalTimeoutSeconds();
        }
        return approval.getTimeoutSeconds();
    }

    /**
     * 工具输出回填模型的最大字符数
     */
    public int maxToolOutputChars() {
        return properties.getMaxToolOutputChars();
    }

    /**
     * 是否记录 step 明细
     */
    public boolean recordSteps(HarnessConfig config) {
        return config == null || config.getRecordSteps() == null || Boolean.TRUE.equals(config.getRecordSteps());
    }

    private int pick(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private long pick(Long value, long fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
