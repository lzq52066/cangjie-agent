package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.core.harness.AssistantTurn;
import cn.cangjiecloud.core.harness.DeltaSink;
import cn.cangjiecloud.core.harness.HarnessException;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.ModelGateway;
import cn.cangjiecloud.core.harness.ModelSettings;
import cn.cangjiecloud.core.model.ChatChunk;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议模型网关。
 * <p>
 * 把"同步 {@code client.chat}"与"流式 {@code client.streamChat}"两种调用归一为一轮
 * {@link AssistantTurn}，因此 Agent 循环只有一份实现。
 * 请求参数组装约定：不下发 model（由客户端自身的模型配置决定）、
 * 无工具时 toolChoice 为 none、温度回落 0.7。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiModelGateway implements ModelGateway {

    private static final String CANCELLED_MESSAGE = "SSE 连接已关闭，停止流式对话";

    private final IModelService modelService;

    @Override
    public AssistantTurn exchange(HarnessRequest request, List<ChatMessage> messages, DeltaSink sink) {
        OpenAICompatibleClient client = resolveClient(request.getModelId());
        ChatRequest chatRequest = toChatRequest(request, messages);
        if (sink == null) {
            return exchangeSync(client, chatRequest, request);
        }
        return exchangeStream(client, chatRequest, request, sink);
    }

    // ==================== 同步 ====================

    private AssistantTurn exchangeSync(OpenAICompatibleClient client, ChatRequest chatRequest, HarnessRequest request) {
        ChatResponse response = client.chat(chatRequest);
        if (response == null) {
            throw new HarnessException("模型未返回有效响应");
        }
        return AssistantTurn.builder()
                .content(response.getContent())
                .model(modelName(request))
                .finishReason(response.getFinishReason())
                .inputTokens(response.getPromptTokens())
                .outputTokens(response.getCompletionTokens())
                .totalTokens(response.getTotalTokens())
                .toolCalls(response.getToolCalls())
                .build();
    }

    // ==================== 流式 ====================

    private AssistantTurn exchangeStream(OpenAICompatibleClient client, ChatRequest chatRequest,
                                         HarnessRequest request, DeltaSink sink) {
        StringBuilder content = new StringBuilder();
        List<ChatResponse.ToolCall> toolCalls = new ArrayList<>();
        AtomicReference<String> finishReason = new AtomicReference<>();
        AtomicReference<Long> inputTokens = new AtomicReference<>();
        AtomicReference<Long> outputTokens = new AtomicReference<>();
        AtomicReference<Long> totalTokens = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        try (Stream<ChatChunk> stream = client.streamChat(chatRequest, request.getCancelFlag())) {
            stream.forEach(chunk -> {
                if (request.isCancelled()) {
                    throw new HarnessException(CANCELLED_MESSAGE, partialOf(content), null);
                }
                if (chunk.getError() != null) {
                    error.set(chunk.getError());
                    return;
                }
                if (chunk.getDelta() == null) {
                    return;
                }
                content.append(chunk.getDelta());
                if (chunk.isDone()) {
                    // done 分片只携带统计信息，完成标记由业务侧统一发送
                    if (chunk.getFinishReason() != null) {
                        finishReason.set(chunk.getFinishReason());
                    }
                    if (chunk.getInputTokens() != null) {
                        inputTokens.set(chunk.getInputTokens());
                    }
                    if (chunk.getOutputTokens() != null) {
                        outputTokens.set(chunk.getOutputTokens());
                    }
                    if (chunk.getTotalTokens() != null) {
                        totalTokens.set(chunk.getTotalTokens());
                    }
                    if (chunk.getToolCalls() != null) {
                        toolCalls.addAll(chunk.getToolCalls());
                    }
                    return;
                }
                sink.onDelta(chunk.getDelta());
            });
        } catch (HarnessException e) {
            // 取消等内部中断不携带部分内容（取消分支由 harness 自行保留现场）
            throw e;
        } catch (Exception e) {
            log.error("流式对话异常: {}", e.getMessage(), e);
            String msg = e.getMessage() == null ? "模型流式调用失败" : e.getMessage();
            throw new HarnessException(msg, partialOf(content), e);
        }

        if (error.get() != null) {
            // 服务端在 chunk 中返回错误（如余额不足、限流），此前可能已吐出部分正文，一并保留
            throw new HarnessException(error.get(), partialOf(content), null);
        }

        return AssistantTurn.builder()
                .content(content.toString())
                .model(modelName(request))
                .finishReason(finishReason.get())
                .inputTokens(valueOrZero(inputTokens.get()))
                .outputTokens(valueOrZero(outputTokens.get()))
                .totalTokens(valueOrZero(totalTokens.get()))
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .build();
    }

    // ==================== 参数组装 ====================

    private ChatRequest toChatRequest(HarnessRequest request, List<ChatMessage> messages) {
        ModelSettings settings = request.getModelSettings() == null
                ? ModelSettings.defaults() : request.getModelSettings();
        List<Map<String, Object>> tools = ToolSpecAssembler.toDefinitions(request.getTools());
        return ChatRequest.builder()
                .messages(new ArrayList<>(messages))
                .temperature(settings.getTemperature())
                .maxTokens(settings.getMaxTokens())
                .topP(settings.getTopP())
                .tools(tools)
                .toolChoice(tools.isEmpty() ? "none" : "auto")
                .build();
    }

    private OpenAICompatibleClient resolveClient(String modelId) {
        return StringUtils.hasText(modelId) ? modelService.getClient(modelId) : modelService.getDefaultClient();
    }

    private String modelName(HarnessRequest request) {
        if (StringUtils.hasText(request.getModelName())) {
            return request.getModelName();
        }
        if (!StringUtils.hasText(request.getModelId())) {
            return "默认模型";
        }
        try {
            ModelEntity model = modelService.getById(request.getModelId());
            if (model != null && StringUtils.hasText(model.getName())) {
                return model.getName();
            }
        } catch (Exception e) {
            log.warn("获取模型名称失败: modelId={}, {}", request.getModelId(), e.getMessage());
        }
        return request.getModelId();
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    /** 已累积正文非空白时返回，供失败场景保留部分回答 */
    private static String partialOf(StringBuilder content) {
        if (content == null || content.length() == 0) {
            return null;
        }
        String text = content.toString().trim();
        return text.isEmpty() ? null : content.toString();
    }
}
