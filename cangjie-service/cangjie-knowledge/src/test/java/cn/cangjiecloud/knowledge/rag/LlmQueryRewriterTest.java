package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link LlmQueryRewriter} 单元测试。
 * <p>
 * 覆盖：空 query / 空历史短路、成功改写与提示词拼装、角色前缀映射、模型空响应与调用异常的降级。
 */
@ExtendWith(MockitoExtension.class)
class LlmQueryRewriterTest {

    @Mock
    private IModelService modelService;

    @Mock
    private OpenAICompatibleClient client;

    private LlmQueryRewriter rewriter;

    @BeforeEach
    void setUp() {
        rewriter = new LlmQueryRewriter(modelService);
        ReflectionTestUtils.setField(rewriter, "temperature", 0.3);
        ReflectionTestUtils.setField(rewriter, "maxTokens", 200);
    }

    @Test
    void getType应为llm() {
        assertThat(rewriter.getType()).isEqualTo("llm");
    }

    @Nested
    class 短路与降级 {

        @Test
        void query为空白时应原样返回且不调用模型() {
            assertThat(rewriter.rewrite(null, List.of(ChatMessage.user("你好")))).isNull();
            assertThat(rewriter.rewrite("   ", List.of(ChatMessage.user("你好")))).isEqualTo("   ");
            verifyNoInteractions(modelService);
        }

        @Test
        void 历史为空时应原样返回且不调用模型() {
            assertThat(rewriter.rewrite("原始问题", null)).isEqualTo("原始问题");
            assertThat(rewriter.rewrite("原始问题", List.of())).isEqualTo("原始问题");
            verifyNoInteractions(modelService);
        }

        @Test
        void 模型返回空白时应降级返回原query() {
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("   ").build());

            assertThat(rewriter.rewrite("原始问题", List.of(ChatMessage.user("你好")))).isEqualTo("原始问题");
        }

        @Test
        void 模型返回null时应降级返回原query() {
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.chat(any(ChatRequest.class))).thenReturn(null);

            assertThat(rewriter.rewrite("原始问题", List.of(ChatMessage.user("你好")))).isEqualTo("原始问题");
        }

        @Test
        void 模型调用异常时应降级返回原query() {
            when(modelService.getDefaultClient()).thenThrow(new RuntimeException("模型服务不可用"));

            assertThat(rewriter.rewrite("原始问题", List.of(ChatMessage.user("你好")))).isEqualTo("原始问题");
        }
    }

    @Nested
    class 成功改写 {

        @Test
        void 成功改写应返回模型输出并携带历史与参数() {
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content(" 他昨天说的内容 ").build());
            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);

            String result = rewriter.rewrite("他昨天说了什么",
                    List.of(ChatMessage.user("你好"), ChatMessage.assistant("收到")));

            assertThat(result).isEqualTo("他昨天说的内容");
            verify(client).chat(captor.capture());
            ChatRequest request = captor.getValue();
            assertThat(request.getTemperature()).isEqualTo(0.3);
            assertThat(request.getMaxTokens()).isEqualTo(200);
            assertThat(request.getMessages()).hasSize(2);
            assertThat(request.getMessages().get(0).getRole()).isEqualTo("system");
            assertThat(request.getMessages().get(0).getContent()).contains("检索查询改写助手");
            String prompt = request.getMessages().get(1).getContent();
            assertThat(prompt)
                    .contains("用户: 你好")
                    .contains("助手: 收到")
                    .contains("用户最新问题：\n他昨天说了什么");
        }

        @Test
        void 角色为空或未知时提示词应使用回退前缀() {
            when(modelService.getDefaultClient()).thenReturn(client);
            when(client.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("改写结果").build());
            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            ChatMessage withoutRole = ChatMessage.builder().content("没有角色的消息").build();
            ChatMessage toolMessage = ChatMessage.builder().role("tool").content("工具结果").build();

            rewriter.rewrite("问题", List.of(withoutRole, toolMessage));

            verify(client).chat(captor.capture());
            String prompt = captor.getValue().getMessages().get(1).getContent();
            assertThat(prompt)
                    .contains("用户: 没有角色的消息")
                    .contains("tool: 工具结果");
        }
    }
}
