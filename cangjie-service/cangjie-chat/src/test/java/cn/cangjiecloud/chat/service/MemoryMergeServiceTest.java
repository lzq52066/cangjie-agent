package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link MemoryMergeService} 单元测试：覆盖合并判定的合并/不合并/输出空白/
 * 格式异常/调用异常等分支。
 */
@ExtendWith(MockitoExtension.class)
class MemoryMergeServiceTest {

    /** OpenAI 客户端桩 */
    @Mock
    private OpenAICompatibleClient client;

    /** 被测试记忆合并服务 */
    private MemoryMergeService service;

    @BeforeEach
    void setUp() {
        service = new MemoryMergeService();
    }

    /**
     * 让模型返回指定正文
     */
    private void modelReturns(String content) {
        when(client.chat(any())).thenReturn(ChatResponse.builder().content(content).build());
    }

    /** 判定合并且有内容时返回合并文本 */
    @Test
    void shouldReturnMergedContent() {
        modelReturns("{\"merge\":true,\"content\":\"喜欢喝美式咖啡\"}");

        String result = service.decideMergedContent(client, 0.0,
                "喜欢美式", "喝咖啡", "app-1", "应用", "m1", "s1", "u1");

        assertThat(result).isEqualTo("喜欢喝美式咖啡");
    }

    /** 兼容被解释性文字包裹的 JSON */
    @Test
    void shouldExtractJsonFromWrappedText() {
        modelReturns("好的，分析如下：\n{\"merge\":true,\"content\":\"合并结果\"}\n以上。");

        assertThat(service.decideMergedContent(client, null,
                "a", "b", null, null, null, null, null))
                .isEqualTo("合并结果");
    }

    /** 判定不合并时返回 null */
    @Test
    void shouldReturnNullWhenNotMerging() {
        modelReturns("{\"merge\":false,\"content\":\"\"}");

        assertThat(service.decideMergedContent(client, 0.0,
                "a", "b", null, null, null, null, null)).isNull();
    }

    /** 判定合并但内容空白时返回 null */
    @Test
    void shouldReturnNullWhenMergedContentBlank() {
        modelReturns("{\"merge\":true,\"content\":\"  \"}");

        assertThat(service.decideMergedContent(client, 0.0,
                "a", "b", null, null, null, null, null)).isNull();
    }

    /** 模型正文不含 JSON 结构时返回 null */
    @Test
    void shouldReturnNullWhenNoJsonPresent() {
        modelReturns("没有结构化输出");

        assertThat(service.decideMergedContent(client, 0.0,
                "a", "b", null, null, null, null, null)).isNull();
    }

    /** 模型响应为 null 或正文空白时返回 null */
    @Test
    void shouldReturnNullWhenResponseEmpty() {
        when(client.chat(any())).thenReturn(null);

        assertThat(service.decideMergedContent(client, 0.0,
                "a", "b", null, null, null, null, null)).isNull();
    }

    /** 客户端调用抛异常时容错返回 null */
    @Test
    void shouldReturnNullWhenClientThrows() {
        when(client.chat(any())).thenThrow(new RuntimeException("网络异常"));

        assertThat(service.decideMergedContent(client, 0.0,
                "a", "b", null, null, null, null, null)).isNull();
    }
}
