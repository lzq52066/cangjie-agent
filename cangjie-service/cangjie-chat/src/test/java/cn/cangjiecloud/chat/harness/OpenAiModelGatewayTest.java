package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.core.harness.AssistantTurn;
import cn.cangjiecloud.core.harness.DeltaSink;
import cn.cangjiecloud.core.harness.HarnessException;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.ModelSettings;
import cn.cangjiecloud.core.model.ChatChunk;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpenAiModelGateway} 单元测试：覆盖同步/流式两条调用路径，
 * 包括客户端选择、空响应报错、取消中断、分片错误、异常包装与部分正文保留。
 */
@ExtendWith(MockitoExtension.class)
class OpenAiModelGatewayTest {

    /** 模型服务桩 */
    @Mock
    private IModelService modelService;

    /** OpenAI 兼容客户端桩 */
    @Mock
    private OpenAICompatibleClient client;

    /** 被测试模型网关 */
    private OpenAiModelGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new OpenAiModelGateway(modelService);
    }

    /**
     * 构造最小化执行请求
     */
    private HarnessRequest request(String modelId, String modelName, boolean cancelled) {
        return HarnessRequest.builder()
                .runId("run-1").traceId("trace-1")
                .modelId(modelId).modelName(modelName)
                .modelSettings(ModelSettings.builder().temperature(0.9).build())
                .cancelFlag(new AtomicBoolean(cancelled))
                .tools(List.of())
                .build();
    }

    private List<ChatMessage> messages() {
        return List.of(ChatMessage.user("hi"));
    }

    @Nested
    class ExchangeSync {

        /** 有模型 ID 时取对应客户端，无 ID 时取默认客户端 */
        @Test
        void shouldResolveClientByModelId() {
            when(modelService.getClient("m1")).thenReturn(client);
            when(client.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().build());

            gateway.exchange(request("m1", "名称", false), messages(), null);

            verify(modelService).getClient("m1");
        }

        /** 模型 ID 空白时使用默认客户端 */
        @Test
        void shouldUseDefaultClientWhenModelIdBlank() {
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().build());

            gateway.exchange(request(null, null, false), messages(), null);

            verify(modelService).getDefaultClient();
        }

        /** 同步响应被翻译为 AssistantTurn，透传正文与统计 */
        @Test
        void shouldMapSyncResponseToTurn() {
            ChatResponse response = ChatResponse.builder()
                    .content("答案").finishReason("stop")
                    .promptTokens(10).completionTokens(5).totalTokens(15)
                    .build();
            when(modelService.getClient("m1")).thenReturn(client);
            when(client.chat(any(ChatRequest.class))).thenReturn(response);

            AssistantTurn turn = gateway.exchange(request("m1", "名称", false), messages(), null);

            assertThat(turn.getContent()).isEqualTo("答案");
            assertThat(turn.getFinishReason()).isEqualTo("stop");
            assertThat(turn.getInputTokens()).isEqualTo(10L);
            assertThat(turn.getOutputTokens()).isEqualTo(5L);
            assertThat(turn.getTotalTokens()).isEqualTo(15L);
            assertThat(turn.getModel()).isEqualTo("名称");
        }

        /** 同步响应为 null 时抛出 HarnessException */
        @Test
        void shouldThrowWhenSyncResponseNull() {
            when(modelService.getClient("m1")).thenReturn(client);
            when(client.chat(any(ChatRequest.class))).thenReturn(null);

            assertThatThrownBy(() -> gateway.exchange(request("m1", "名称", false), messages(), null))
                    .isInstanceOf(HarnessException.class)
                    .hasMessageContaining("模型未返回有效响应");
        }

        /** 无工具时组装出的请求 toolChoice 为 none */
        @Test
        void shouldSetToolChoiceNoneWhenNoTools() {
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().build());

            gateway.exchange(request(null, null, false), messages(), null);

            verify(client).chat(org.mockito.ArgumentMatchers.argThat(
                    req -> "none".equals(req.getToolChoice()) && req.getTools().isEmpty()));
        }
    }

    @Nested
    class ExchangeStream {

        /** 正常流式：增量转发给 sink，done 分片统计被汇总 */
        @Test
        void shouldAggregateStreamAndForwardDeltas() {
            ChatChunk delta1 = ChatChunk.builder().delta("你").done(false).build();
            ChatChunk delta2 = ChatChunk.builder().delta("好").done(false).build();
            ChatChunk done = ChatChunk.builder().delta("").done(true)
                    .finishReason("stop").inputTokens(3L).outputTokens(2L).totalTokens(5L)
                    .build();
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.streamChat(any(ChatRequest.class), any()))
                    .thenReturn(java.util.stream.Stream.of(delta1, delta2, done));
            DeltaSink sink = org.mockito.Mockito.mock(DeltaSink.class);

            AssistantTurn turn = gateway.exchange(
                    request(null, "名称", false), messages(), sink);

            assertThat(turn.getContent()).isEqualTo("你好");
            assertThat(turn.getFinishReason()).isEqualTo("stop");
            assertThat(turn.getInputTokens()).isEqualTo(3L);
            assertThat(turn.getTotalTokens()).isEqualTo(5L);
            verify(sink).onDelta("你");
            verify(sink).onDelta("好");
        }

        /** 请求已取消时抛出携带取消消息的 HarnessException */
        @Test
        void shouldThrowOnCancellation() {
            ChatChunk delta = ChatChunk.builder().delta("x").done(false).build();
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.streamChat(any(ChatRequest.class), any()))
                    .thenReturn(java.util.stream.Stream.of(delta));

            assertThatThrownBy(() -> gateway.exchange(
                    request(null, null, true), messages(), DeltaSink.NOOP))
                    .isInstanceOf(HarnessException.class)
                    .hasMessageContaining("SSE 连接已关闭");
        }

        /** 分片携带错误时抛出 HarnessException，并保留此前的部分正文 */
        @Test
        void shouldThrowWhenChunkCarriesError() {
            ChatChunk delta = ChatChunk.builder().delta("部分").done(false).build();
            ChatChunk errorChunk = ChatChunk.builder().delta("").done(false).error("余额不足").build();
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.streamChat(any(ChatRequest.class), any()))
                    .thenReturn(java.util.stream.Stream.of(delta, errorChunk));

            assertThatThrownBy(() -> gateway.exchange(
                    request(null, null, false), messages(), DeltaSink.NOOP))
                    .isInstanceOf(HarnessException.class)
                    .hasMessage("余额不足")
                    .satisfies(ex -> assertThat(((HarnessException) ex).getPartialContent())
                            .isEqualTo("部分"));
        }

        /** 流式过程中抛出 HarnessException 时原样上抛 */
        @Test
        void shouldRethrowHarnessExceptionFromStream() {
            HarnessException failure = new HarnessException("内部中断");
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.streamChat(any(ChatRequest.class), any()))
                    .thenThrow(failure);

            assertThatThrownBy(() -> gateway.exchange(
                    request(null, null, false), messages(), DeltaSink.NOOP))
                    .isSameAs(failure);
        }

        /** 流式过程中抛出其他异常时包装为 HarnessException */
        @Test
        void shouldWrapOtherExceptions() {
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.streamChat(any(ChatRequest.class), any()))
                    .thenThrow(new RuntimeException("连接重置"));

            assertThatThrownBy(() -> gateway.exchange(
                    request(null, null, false), messages(), DeltaSink.NOOP))
                    .isInstanceOf(HarnessException.class)
                    .hasMessage("连接重置");
        }

        /** 无增量的非 done 分片被跳过，不转发给 sink */
        @Test
        void shouldSkipChunkWithoutDelta() {
            ChatChunk empty = ChatChunk.builder().delta(null).done(false).build();
            ChatChunk done = ChatChunk.builder().delta("").done(true).build();
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.streamChat(any(ChatRequest.class), any()))
                    .thenReturn(java.util.stream.Stream.of(empty, done));
            DeltaSink sink = org.mockito.Mockito.mock(DeltaSink.class);

            gateway.exchange(request(null, null, false), messages(), sink);

            verify(sink, never()).onDelta(any());
        }
    }

    /** 模型名缺失且模型 ID 存在时，查询失败也能回落返回模型 ID */
    @Test
    void shouldFallbackModelNameToIdWhenLookupFails() {
        when(modelService.getClient("m9")).thenReturn(client);
        when(modelService.getById("m9")).thenThrow(new RuntimeException("x"));
        when(client.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().build());

        AssistantTurn turn = gateway.exchange(request("m9", null, false), messages(), null);

        assertThat(turn.getModel()).isEqualTo("m9");
    }

    /** 模型名与模型 ID 均缺失时模型名回落为“默认模型” */
    @Test
    void shouldFallbackModelNameToDefault() {
        when(modelService.getDefaultClient()).thenReturn(client);
        when(client.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().build());

        AssistantTurn turn = gateway.exchange(request(null, null, false), messages(), null);

        assertThat(turn.getModel()).isEqualTo("默认模型");
    }
}
