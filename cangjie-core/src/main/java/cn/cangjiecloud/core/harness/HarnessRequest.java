package cn.cangjiecloud.core.harness;

import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.tool.ToolSpecification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次 Agent 执行的输入
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRequest {

    /** run ID（新建时可为空，由 recorder 生成后回填） */
    private String runId;

    /** 链路 ID */
    private String traceId;

    private String applicationId;

    private String applicationName;

    private String sessionId;

    private String userId;

    private String modelId;

    private String modelName;

    /** 执行形态：chat / workflow / subagent / debug */
    @Builder.Default
    private String harnessType = "chat";

    /** 父 run（子 Agent、工作流内嵌 Agent 场景） */
    private String parentRunId;

    /** 当前嵌套深度，父为 0 */
    @Builder.Default
    private int depth = 0;

    /** 上下文管线产出的初始消息列表 */
    private List<ChatMessage> contextMessages;

    /** 上下文管线各槽位占用（非空时留痕并回调 onContextReady） */
    private List<ContextFragment> contextFragments;

    /** 是否流式（true 时模型网关以流式调用并把增量转发给监听器） */
    @Builder.Default
    private boolean stream = false;

    /** 可用工具 */
    private List<ToolSpecification> tools;

    /** 采样参数 */
    @Builder.Default
    private ModelSettings modelSettings = ModelSettings.defaults();

    /** 循环策略 */
    @Builder.Default
    private LoopPolicy loopPolicy = LoopPolicy.builder().build();

    /** 应用级配置 */
    private HarnessConfig config;

    /** 知识库 ID（agentic 检索工具执行时需要，透传给 SpecialToolRoute） */
    private List<String> knowledgeBaseIds;

    /** 取消信号（SSE 断开、主动中断），同时供模型网关中断流式读取 */
    @Builder.Default
    private AtomicBoolean cancelFlag = new AtomicBoolean();

    /** 恢复执行时的检查点（为空表示全新执行） */
    private ResumeState resume;

    public boolean isCancelled() {
        return cancelFlag != null && cancelFlag.get();
    }
}
