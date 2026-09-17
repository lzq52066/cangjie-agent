package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import cn.cangjiecloud.model.entity.ModelEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LlmNode} 单元测试：模型选择、异常分支、提示词回退、参数覆盖、输出结构。
 */
class LlmNodeTest {

    private IModelService modelService;
    private OpenAICompatibleClient client;
    private LlmNode node;

    @BeforeEach
    void setUp() {
        modelService = mock(IModelService.class);
        client = mock(OpenAICompatibleClient.class);
        node = new LlmNode(modelService);
    }

    private ModelEntity entity(String name, Double temperature, Integer maxTokens) {
        ModelEntity entity = new ModelEntity();
        entity.setModelName(name);
        entity.setTemperature(temperature);
        entity.setMaxTokens(maxTokens);
        return entity;
    }

    @Test
    // 覆盖场景：节点元信息
    void metadataShouldDescribeLlmNode() {
        assertThat(node.getType()).isEqualTo("llm");
        assertThat(node.getName()).isEqualTo("大模型");
        assertThat(node.getDescription()).contains("大语言模型");
    }

    @Test
    // 覆盖场景：配置 modelId —— 走 getById + getClient，输出 llm_output/llm_model/llm_tokens
    void executeShouldUseSpecifiedModelAndClient() {
        when(modelService.getById("m1")).thenReturn(entity("gpt-test", 0.3, 512));
        when(modelService.getClient("m1")).thenReturn(client);
        when(client.chat(any())).thenReturn(ChatResponse.builder()
                .content("你好").totalTokens(7).build());

        Map<String, Object> output = node.execute(
                Map.of("question", "你是谁"),
                Map.of("modelId", "m1", "userPrompt", "你好 {question}"));

        assertThat(output)
                .containsEntry("llm_output", "你好")
                .containsEntry("llm_model", "gpt-test")
                .containsEntry("llm_tokens", 7);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(client).chat(captor.capture());
        ChatRequest request = captor.getValue();
        assertThat(request.getModel()).isEqualTo("gpt-test");
        assertThat(request.getTemperature()).isEqualTo(0.3);
        assertThat(request.getMaxTokens()).isEqualTo(512);
        // 无 systemPrompt，仅一条 user 消息，且占位符已替换
        assertThat(request.getMessages()).hasSize(1);
        assertThat(request.getMessages().get(0).getRole()).isEqualTo("user");
        assertThat(request.getMessages().get(0).getContent()).isEqualTo("你好 你是谁");
    }

    @Test
    // 覆盖场景：未配置 modelId —— 走 getDefaultModel + getDefaultClient
    void executeShouldFallBackToDefaultModel() {
        when(modelService.getDefaultModel()).thenReturn(entity("default-model", null, null));
        when(modelService.getDefaultClient()).thenReturn(client);
        when(client.chat(any())).thenReturn(ChatResponse.builder().content("ok").build());

        Map<String, Object> output = node.execute(Map.of(), Map.of("userPrompt", "hi"));

        assertThat(output).containsEntry("llm_model", "default-model");
        // 实体 temperature/maxTokens 为 null 时取默认 0.7 / 4096
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(client).chat(captor.capture());
        assertThat(captor.getValue().getTemperature()).isEqualTo(0.7);
        assertThat(captor.getValue().getMaxTokens()).isEqualTo(4096);
    }

    @Test
    // 覆盖场景：modelId 指定但模型不存在 —— 抛 ApiException
    void executeShouldThrowWhenSpecifiedModelMissing() {
        when(modelService.getById("bad")).thenReturn(null);

        assertThatThrownBy(() -> node.execute(Map.of(), Map.of("modelId", "bad")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("LLM节点: 模型不存在: bad");
    }

    @Test
    // 覆盖场景：无 modelId 且未配置默认模型 —— 抛 ApiException
    void executeShouldThrowWhenDefaultModelMissing() {
        when(modelService.getDefaultModel()).thenReturn(null);

        assertThatThrownBy(() -> node.execute(Map.of(), Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("LLM节点: 未配置默认模型");
    }

    @Test
    // 覆盖场景：userPrompt 缺失 —— 依次回退 inputs.question、inputs.input
    void executeShouldFallBackToQuestionThenInput() {
        when(modelService.getDefaultModel()).thenReturn(entity("m", 0.5, 100));
        when(modelService.getDefaultClient()).thenReturn(client);
        when(client.chat(any())).thenReturn(ChatResponse.builder().content("r").build());

        node.execute(Map.of("question", "问题Q", "input", "输入I"), new HashMap<>());
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(client).chat(captor.capture());
        assertThat(captor.getValue().getMessages().get(0).getContent()).isEqualTo("问题Q");

        node.execute(Map.of("input", "输入I"), new HashMap<>());
        verify(client, org.mockito.Mockito.times(2)).chat(captor.capture());
        assertThat(captor.getValue().getMessages().get(0).getContent()).isEqualTo("输入I");
    }

    @Test
    // 覆盖场景：userPrompt 与 question/input 均为空 —— 兜底提示词"请回答"，且 systemPrompt 加入消息头
    void executeShouldUseDefaultPromptWhenNoUserContent() {
        when(modelService.getDefaultModel()).thenReturn(entity("m", 0.5, 100));
        when(modelService.getDefaultClient()).thenReturn(client);
        when(client.chat(any())).thenReturn(ChatResponse.builder().content("r").build());

        node.execute(Map.of(), Map.of("systemPrompt", "你是助手"));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(client).chat(captor.capture());
        assertThat(captor.getValue().getMessages()).hasSize(2);
        assertThat(captor.getValue().getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(captor.getValue().getMessages().get(1).getContent()).isEqualTo("请回答");
    }

    @Test
    // 覆盖场景：config 中 temperature/maxTokens 覆盖模型默认值
    void executeShouldOverrideGenerationParamsFromConfig() {
        when(modelService.getById("m1")).thenReturn(entity("gpt", 0.3, 512));
        when(modelService.getClient("m1")).thenReturn(client);
        when(client.chat(any())).thenReturn(ChatResponse.builder().content("r").build());

        node.execute(Map.of(), Map.of("modelId", "m1", "userPrompt", "hi",
                "temperature", 0.9, "maxTokens", 64));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(client).chat(captor.capture());
        assertThat(captor.getValue().getTemperature()).isEqualTo(0.9);
        assertThat(captor.getValue().getMaxTokens()).isEqualTo(64);
    }
}
