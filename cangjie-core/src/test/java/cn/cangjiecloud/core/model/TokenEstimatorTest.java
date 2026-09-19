package cn.cangjiecloud.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TokenEstimator} 单元测试：基于 cl100k_base BPE 的 token 计数口径。
 */
class TokenEstimatorTest {

    @Test
    void nullAndEmptyTextShouldCountZero() {
        assertThat(TokenEstimator.count((String) null)).isZero();
        assertThat(TokenEstimator.count("")).isZero();
    }

    @Test
    void englishWordsShouldCountAsWholeTokens() {
        // 常见英文单词在 cl100k 词表内，一个单词约等于一个 token
        assertThat(TokenEstimator.count("hello world")).isEqualTo(2);
    }

    @Test
    void chineseShouldCountSignificantlyHigherThanCharTimesThreeQuarters() {
        String zh = "今天天气真不错，我们一起出去走走吧";
        int tokens = TokenEstimator.count(zh);
        // 旧口径 ceil(len * 0.75) = 15；BPE 下每个汉字通常 1~2 token
        assertThat(tokens).isGreaterThan((int) Math.ceil(zh.length() * 0.75));
        assertThat(tokens).isBetween(zh.length(), zh.length() * 2);
    }

    @Test
    void repeatedCountShouldBeStable() {
        String text = "OpenAI compatible tokenizer 稳定性验证 123";
        assertThat(TokenEstimator.count(text)).isEqualTo(TokenEstimator.count(text));
    }

    @Test
    void nullMessageShouldCountZero() {
        assertThat(TokenEstimator.count((ChatMessage) null)).isZero();
        assertThat(TokenEstimator.count((List<ChatMessage>) null)).isZero();
        assertThat(TokenEstimator.count(List.of())).isZero();
    }

    @Test
    void messageCountShouldIncludeToolCalls() {
        ChatMessage plain = ChatMessage.assistant("hello world");
        ChatMessage withToolCall = ChatMessage.assistant("hello world",
                List.of(ChatMessage.ToolCallRef.of("call-1", "get_weather", "{\"city\":\"北京\"}")));

        int plainTokens = TokenEstimator.count(plain);
        int withToolCallTokens = TokenEstimator.count(withToolCall);
        assertThat(withToolCallTokens).isGreaterThan(plainTokens);
    }

    @Test
    void messageListShouldSumEachMessage() {
        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是一个助手"),
                ChatMessage.user("你好"),
                ChatMessage.assistant("你好，有什么可以帮你？"));

        int expected = messages.stream().mapToInt(TokenEstimator::count).sum();
        assertThat(TokenEstimator.count(messages)).isEqualTo(expected);
        assertThat(TokenEstimator.count(messages)).isPositive();
    }
}
