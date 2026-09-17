package cn.cangjiecloud.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModelType} 单元测试：枚举取值、code/label 以及 {@code of()} 的全部分支。
 */
class ModelTypeTest {

    @Test
    void valuesShouldCoverAllSevenProvidersInOrder() {
        assertThat(ModelType.values()).containsExactly(
                ModelType.OPENAI, ModelType.DEEPSEEK, ModelType.QWEN, ModelType.ZHIPU,
                ModelType.WENXIN, ModelType.OLLAMA, ModelType.CUSTOM);
    }

    @ParameterizedTest
    @CsvSource({
            "OPENAI,openai,OpenAI",
            "DEEPSEEK,deepseek,DeepSeek",
            "QWEN,qwen,通义千问",
            "ZHIPU,zhipu,智谱清言",
            "WENXIN,wenxin,文心一言",
            "OLLAMA,ollama,Ollama 本地模型",
            "CUSTOM,custom,自定义"
    })
    void getCodeAndGetLabelShouldReturnDeclaredValues(ModelType type, String code, String label) {
        assertThat(type.getCode()).isEqualTo(code);
        assertThat(type.getLabel()).isEqualTo(label);
    }

    @ParameterizedTest
    @EnumSource(ModelType.class)
    void valueOfShouldResolveEveryConstantByName(ModelType type) {
        assertThat(ModelType.valueOf(type.name())).isSameAs(type);
    }

    // ---------- of(code) ----------

    @Test
    void ofShouldFallBackToOpenAiWhenCodeIsNull() {
        assertThat(ModelType.of(null)).isSameAs(ModelType.OPENAI);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void ofShouldReturnCustomForBlankUnknownCode(String code) {
        // 空白串不匹配任何 code/name，落入兜底 CUSTOM
        assertThat(ModelType.of(code)).isSameAs(ModelType.CUSTOM);
    }

    @ParameterizedTest
    @CsvSource({
            "openai,OPENAI",
            "deepseek,DEEPSEEK",
            "qwen,QWEN",
            "zhipu,ZHIPU",
            "wenxin,WENXIN",
            "ollama,OLLAMA",
            "custom,CUSTOM"
    })
    void ofShouldMatchCodeIgnoreCase(String code, ModelType expected) {
        assertThat(ModelType.of(code)).isSameAs(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "OPENAI,OPENAI",
            "DeepSeek,DEEPSEEK",
            "QwEn,QWEN",
            "ZhiPu,ZHIPU",
            "WENXIN,WENXIN",
            "Ollama,OLLAMA"
    })
    void ofShouldMatchEnumNameIgnoreCase(String code, ModelType expected) {
        assertThat(ModelType.of(code)).isSameAs(expected);
    }

    @Test
    void ofShouldReturnCustomForUpperCaseCustomName() {
        assertThat(ModelType.of("CUSTOM")).isSameAs(ModelType.CUSTOM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gpt-4o", "unknown", "0", "null", "OPENAI_X", "智谱"})
    void ofShouldReturnCustomForUnknownCode(String code) {
        assertThat(ModelType.of(code)).isSameAs(ModelType.CUSTOM);
    }

    @Test
    void ofShouldBeIdempotentForEveryResolvedCode() {
        for (ModelType type : ModelType.values()) {
            assertThat(ModelType.of(ModelType.of(type.getCode()).getCode())).isSameAs(type);
        }
    }

    @Test
    void ordinalShouldFollowDeclarationOrder() {
        assertThat(ModelType.OPENAI.ordinal()).isZero();
        assertThat(ModelType.CUSTOM.ordinal()).isEqualTo(ModelType.values().length - 1);
    }
}
