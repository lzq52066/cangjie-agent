package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.workflow.WorkflowNode;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 节点 — 调用大模型进行对话
 * <p>
 * config 参数：
 * - modelId: 模型配置 ID（必填，不填则使用默认模型）
 * - systemPrompt: 系统提示词（可选）
 * - userPrompt: 用户提示词（可选，支持 {variable} 占位符）
 * - temperature: 温度（可选，覆盖模型默认值）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNode implements WorkflowNode {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{(\\w+)}");

    private final IModelService modelService;

    /** 单次 LLM 请求超时（秒），与模型服务共用同一配置 */
    @Value("${cangjie.model.timeout-seconds:120}")
    private long timeoutSeconds;

    @Autowired
    @Qualifier("llmStreamExecutor")
    private AsyncTaskExecutor streamExecutor;

    @Autowired
    private cn.cangjiecloud.model.circuitbreaker.ModelCircuitBreaker circuitBreaker;

    @Override
    public String getType() {
        return "llm";
    }

    @Override
    public String getName() {
        return "大模型";
    }

    @Override
    public String getDescription() {
        return "调用大语言模型进行对话";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
        Map<String, Object> safeConfig = config != null ? config : Map.of();
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();

        // 获取模型
        String modelId = (String) safeConfig.get("modelId");
        ModelEntity entity;
        if (modelId != null && !modelId.isEmpty()) {
            entity = modelService.getById(modelId);
            if (entity == null) {
                throw new ApiException("LLM节点: 模型不存在: " + modelId);
            }
        } else {
            entity = modelService.getDefaultModel();
            if (entity == null) {
                throw new ApiException("LLM节点: 未配置默认模型");
            }
        }

        // 构建提示词（支持变量替换）
        String systemPrompt = resolveVariables((String) safeConfig.get("systemPrompt"), safeInputs);
        String userPrompt = resolveVariables((String) safeConfig.get("userPrompt"), safeInputs);

        // 如果没有配置 prompt，尝试使用 question 或 input 变量
        if (userPrompt == null || userPrompt.isEmpty()) {
            Object question = safeInputs.get("question");
            if (question == null) question = safeInputs.get("input");
            if (question != null) {
                userPrompt = question.toString();
            }
        }

        if (userPrompt == null || userPrompt.isEmpty()) {
            userPrompt = "请回答";
        }

        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        messages.add(ChatMessage.user(userPrompt));

        // 构建请求
        double temperature = safeConfig.containsKey("temperature")
                ? ((Number) safeConfig.get("temperature")).doubleValue()
                : (entity.getTemperature() != null ? entity.getTemperature() : 0.7);
        int maxTokens = safeConfig.containsKey("maxTokens")
                ? ((Number) safeConfig.get("maxTokens")).intValue()
                : (entity.getMaxTokens() != null ? entity.getMaxTokens() : 4096);

        ChatRequest request = ChatRequest.builder()
                .model(entity.getModelName())
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        // 调用模型
        OpenAICompatibleClient client = new OpenAICompatibleClient(
                entity, Duration.ofSeconds(timeoutSeconds), streamExecutor, circuitBreaker);
        ChatResponse response = client.chat(request);

        log.info("LLM节点执行完成, 模型: {}, tokens: {}", entity.getModelName(), response.getTotalTokens());

        Map<String, Object> result = new HashMap<>();
        result.put("llm_output", response.getContent());
        result.put("llm_model", entity.getModelName());
        result.put("llm_tokens", response.getTotalTokens());
        return result;
    }

    /**
     * 将 {variable} 占位符替换为上下文变量值
     */
    private String resolveVariables(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
