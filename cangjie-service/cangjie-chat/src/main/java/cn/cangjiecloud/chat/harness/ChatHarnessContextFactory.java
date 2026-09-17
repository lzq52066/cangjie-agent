package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.core.harness.AgentRunRecorder;
import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.ResumeResolver;
import cn.cangjiecloud.core.harness.ResumeState;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.service.IModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对话侧的 {@link HarnessRequest} 装配器，同时充当断点续跑的 {@link ResumeResolver}。
 * <p>
 * 检查点只持久化会话状态（消息、轮次、待执行工具），模型、工具集与采样参数属于配置态，
 * 恢复时必须按 run 上的应用重新装配——因此这两件事放在同一处，保证新建执行与恢复执行
 * 拿到的参数完全同源。装配顺序沿用改造前：应用工具 → 技能 → agentic 检索工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHarnessContextFactory implements ResumeResolver {

    private final IApplicationService applicationService;
    private final IModelService modelService;
    private final ToolSpecAssembler toolSpecAssembler;
    private final HarnessConfigResolver configResolver;

    /**
     * 为一次新的对话执行装配输入
     *
     * @param application  应用（决定工具集、模型与采样参数）
     * @param messages     上下文管线产出的初始消息列表
     * @param stream       是否流式
     * @param cancelFlag   取消信号（流式必填，同步可传 null）
     * @param sessionId    会话 ID
     * @param userId       用户 ID
     * @param traceId      链路 ID
     * @param maxRounds    兜底最大轮次（现网 {@code cangjie.chat.agent.max-rounds}）
     * @param timeoutSecs  兜底总超时秒数（现网 {@code cangjie.chat.agent.timeout-seconds}）
     */
    public HarnessRequest newRequest(ApplicationEntity application, List<ChatMessage> messages, boolean stream,
                                     AtomicBoolean cancelFlag, String sessionId, String userId, String traceId,
                                     int maxRounds, long timeoutSecs) {
        HarnessConfig config = configResolver.parse(application);
        return HarnessRequest.builder()
                .traceId(traceId)
                .applicationId(application == null ? null : application.getId())
                .applicationName(application == null ? null : application.getName())
                .sessionId(sessionId)
                .userId(userId)
                .modelId(application == null ? null : application.getModelId())
                .modelName(resolveModelName(application == null ? null : application.getModelId()))
                .harnessType("chat")
                .contextMessages(messages)
                .stream(stream)
                .tools(toolSpecAssembler.assemble(application))
                .knowledgeBaseIds(toolSpecAssembler.knowledgeBaseIds(application))
                .modelSettings(configResolver.modelSettings(application))
                .loopPolicy(configResolver.loopPolicy(config, maxRounds, timeoutSecs))
                .config(config)
                .cancelFlag(cancelFlag == null ? new AtomicBoolean() : cancelFlag)
                .build();
    }

    /**
     * 审批决策后按检查点重建执行输入（工具集与模型按应用当前配置重新装配）
     */
    @Override
    public HarnessRequest resolve(AgentRunRecorder.PausedRun pausedRun, boolean approved, String remark) {
        ResumeState state = pausedRun.getState();
        ApplicationEntity application = loadApplication(pausedRun.getApplicationId());
        HarnessConfig config = configResolver.parse(application);
        // 恢复执行不再向前端推增量，产出由 resume 接口的返回值给出
        return HarnessRequest.builder()
                .runId(pausedRun.getRunId())
                .traceId(pausedRun.getTraceId())
                .applicationId(pausedRun.getApplicationId())
                .applicationName(StringUtils.hasText(pausedRun.getApplicationName())
                        ? pausedRun.getApplicationName()
                        : (application == null ? null : application.getName()))
                .sessionId(pausedRun.getSessionId())
                .userId(pausedRun.getUserId())
                .modelId(pausedRun.getModelId())
                .modelName(pausedRun.getModelName())
                .harnessType(StringUtils.hasText(pausedRun.getHarnessType())
                        ? pausedRun.getHarnessType() : "chat")
                .parentRunId(pausedRun.getParentRunId())
                .depth(pausedRun.getDepth())
                .stream(false)
                .tools(toolSpecAssembler.assemble(application))
                .knowledgeBaseIds(toolSpecAssembler.knowledgeBaseIds(application))
                .modelSettings(configResolver.modelSettings(application))
                .loopPolicy(configResolver.loopPolicy(config))
                .config(config)
                .resume(state)
                .build();
    }

    private ApplicationEntity loadApplication(String applicationId) {
        if (!StringUtils.hasText(applicationId)) {
            return null;
        }
        try {
            return applicationService.getById(applicationId);
        } catch (Exception e) {
            log.warn("恢复运行时加载应用失败，按无工具继续: appId={}, {}", applicationId, e.getMessage());
            return null;
        }
    }

    private String resolveModelName(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return "默认模型";
        }
        try {
            ModelEntity model = modelService.getById(modelId);
            if (model != null && StringUtils.hasText(model.getName())) {
                return model.getName();
            }
        } catch (Exception e) {
            log.warn("获取模型名称失败: modelId={}, {}", modelId, e.getMessage());
        }
        return modelId;
    }
}
