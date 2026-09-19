package cn.cangjiecloud.model.provider;

import cn.cangjiecloud.core.model.ChatChunk;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.TokenEstimator;
import cn.cangjiecloud.model.entity.ModelEntity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流式响应 token usage 回填的实机验证。
 * <p>
 * langchain4j 的 {@code OpenAiStreamingChatModel} 固定下发 {@code stream_options.include_usage=true}，
 * 并在收尾 chunk 里回传 usage。本测试用 JDK 内置 HttpServer 模拟 OpenAI 兼容端点，
 * 验证真实 usage 能一路回填到 done 分片；厂商不支持该参数时回落为 jtokkit 估算而非记 0。
 */
class OpenAICompatibleClientStreamingUsageTest {

    private HttpServer server;
    private String baseUrl;
    /** 记录服务端收到的请求体，用于断言 include_usage 确实被下发 */
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void doneChunkShouldCarryRealUsageFromProvider() {
        // 收尾 chunk 携带真实 usage（OpenAI 规范：choices 为空、usage 有值）
        String sse = """
                data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"你好"},"finish_reason":null}]}

                data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4o","choices":[{"index":0,"delta":{"content":"世界"},"finish_reason":null}]}

                data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-4o","choices":[],"usage":{"prompt_tokens":11,"completion_tokens":22,"total_tokens":33}}

                data: [DONE]

                """;

        List<ChatChunk> chunks = collect(streamChat(sse));

        assertThat(receivedBody.get())
                .contains("\"stream_options\"")
                .contains("\"include_usage\"")
                .contains("true");
        assertThat(text(chunks)).isEqualTo("你好世界");
        ChatChunk done = last(chunks);
        assertThat(done.isDone()).isTrue();
        assertThat(done.getInputTokens()).isEqualTo(11L);
        assertThat(done.getOutputTokens()).isEqualTo(22L);
        assertThat(done.getTotalTokens()).isEqualTo(33L);
    }

    @Test
    void doneChunkShouldFallBackToEstimationWhenProviderOmitsUsage() {
        // 部分 OpenAI 兼容厂商不支持 stream_options.include_usage，收尾 chunk 没有 usage
        String sse = """
                data: {"id":"chatcmpl-2","object":"chat.completion.chunk","created":1,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"你好世界"},"finish_reason":null}]}

                data: {"id":"chatcmpl-2","object":"chat.completion.chunk","created":1,"model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;

        List<ChatChunk> chunks = collect(streamChat(sse));

        ChatChunk done = last(chunks);
        assertThat(done.isDone()).isTrue();
        // usage 缺失时按 jtokkit 估算，不能记成 0（否则会话统计与 trace 全部失真）
        assertThat(done.getInputTokens()).isPositive();
        assertThat(done.getOutputTokens())
                .isEqualTo((long) TokenEstimator.count("你好世界"));
        assertThat(done.getTotalTokens()).isEqualTo(done.getInputTokens() + done.getOutputTokens());
    }

    // ==================== 测试脚手架 ====================

    private Stream<ChatChunk> streamChat(String sse) {
        server.createContext("/v1/chat/completions", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = sse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });

        ModelEntity entity = new ModelEntity();
        entity.setId("model-1");
        entity.setName("测试模型");
        entity.setModelName("gpt-4o");
        entity.setMaxTokens(1024);

        OpenAICompatibleClient client = new OpenAICompatibleClient(entity, "test-key", baseUrl, null,
                new SimpleAsyncTaskExecutor("stream-usage-test-"), null, List.of(), 0, 0);

        ChatRequest request = ChatRequest.builder()
                .model("gpt-4o")
                .messages(List.of(ChatMessage.user("你好")))
                .maxTokens(64)
                .build();
        return client.streamChat(request);
    }

    private static List<ChatChunk> collect(Stream<ChatChunk> stream) {
        List<ChatChunk> chunks = new ArrayList<>();
        try (stream) {
            stream.forEach(chunks::add);
        }
        return chunks;
    }

    private static String text(List<ChatChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        chunks.forEach(chunk -> sb.append(chunk.getDelta() == null ? "" : chunk.getDelta()));
        return sb.toString();
    }

    private static ChatChunk last(List<ChatChunk> chunks) {
        assertThat(chunks).isNotEmpty();
        return chunks.get(chunks.size() - 1);
    }
}
