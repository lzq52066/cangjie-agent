package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SplitStrategy} 单元测试：枚举取值与 {@code of()} 的所有分支
 * （null/空白、code 匹配、name 匹配、数字兼容、旧值兼容、未知兜底）。
 */
class SplitStrategyTest {

    @Test
    void valuesShouldCoverSmartAndCustomOnly() {
        assertThat(SplitStrategy.values()).containsExactly(SplitStrategy.SMART, SplitStrategy.CUSTOM);
        assertThat(SplitStrategy.values()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({
            "SMART,smart,智能分段",
            "CUSTOM,custom,自定义分段"
    })
    void getCodeAndGetLabelShouldReturnDeclaredValues(SplitStrategy strategy, String code, String label) {
        assertThat(strategy.getCode()).isEqualTo(code);
        assertThat(strategy.getLabel()).isEqualTo(label);
    }

    @ParameterizedTest
    @EnumSource(SplitStrategy.class)
    void valueOfShouldResolveEveryConstantByName(SplitStrategy strategy) {
        assertThat(SplitStrategy.valueOf(strategy.name())).isSameAs(strategy);
    }

    // ---------- of(code) 兜底分支 ----------

    @Test
    void ofShouldReturnSmartWhenCodeIsNull() {
        assertThat(SplitStrategy.of(null)).isSameAs(SplitStrategy.SMART);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "   \n "})
    void ofShouldReturnSmartWhenCodeIsBlank(String code) {
        assertThat(SplitStrategy.of(code)).isSameAs(SplitStrategy.SMART);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "regex", "semantic", "0", "3", "99", " SMART ", "分块"})
    void ofShouldReturnSmartForUnrecognizedCode(String code) {
        assertThat(SplitStrategy.of(code)).isSameAs(SplitStrategy.SMART);
    }

    // ---------- of(code) 精确匹配分支 ----------

    @ParameterizedTest
    @CsvSource({
            "smart,SMART",
            "SMART,SMART",
            "SmArT,SMART",
            "custom,CUSTOM",
            "CUSTOM,CUSTOM",
            "CuStOm,CUSTOM"
    })
    void ofShouldMatchCodeOrNameIgnoringCase(String code, SplitStrategy expected) {
        assertThat(SplitStrategy.of(code)).isSameAs(expected);
    }

    // ---------- 数字编码兼容分支 ----------

    @ParameterizedTest
    @CsvSource({"1,SMART", "2,CUSTOM"})
    void ofShouldSupportLegacyNumericCodes(String code, SplitStrategy expected) {
        assertThat(SplitStrategy.of(code)).isSameAs(expected);
    }

    @Test
    void ofShouldNotTreatNumericLikeStringAsLegacyCode() {
        // "10" 走的是最终兜底分支而非数字兼容分支
        assertThat(SplitStrategy.of("10")).isSameAs(SplitStrategy.SMART);
        assertThat(SplitStrategy.of("01")).isSameAs(SplitStrategy.SMART);
    }

    // ---------- 旧值兼容分支 ----------

    @ParameterizedTest
    @ValueSource(strings = {"sentence", "SENTENCE", "structural", "Token", "toKeN"})
    void ofShouldMapLegacyStrategyNamesToSmart(String code) {
        assertThat(SplitStrategy.of(code)).isSameAs(SplitStrategy.SMART);
    }

    @Test
    void enumShouldBeComparableAndHashConsistently() {
        assertThat(SplitStrategy.SMART).hasSameHashCodeAs(SplitStrategy.valueOf("SMART"));
        assertThat(SplitStrategy.SMART.ordinal()).isLessThan(SplitStrategy.CUSTOM.ordinal());
    }
}
