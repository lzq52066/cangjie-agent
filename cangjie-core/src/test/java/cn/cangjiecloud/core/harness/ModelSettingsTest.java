package cn.cangjiecloud.core.harness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModelSettings} 单元测试。
 */
class ModelSettingsTest {

    @Test
    void defaultsShouldProvideStandardSamplingValues() {
        ModelSettings settings = ModelSettings.defaults();

        assertThat(settings.getTemperature()).isEqualTo(0.7);
        assertThat(settings.getTopP()).isEqualTo(1.0);
        assertThat(settings.getMaxTokens()).isZero();
        assertThat(settings.getExtra()).isNull();
    }

    @Test
    void builderShouldAllowOverriding() {
        ModelSettings settings = ModelSettings.builder()
                .temperature(0.2).maxTokens(500).topP(0.9).build();

        assertThat(settings.getTemperature()).isEqualTo(0.2);
        assertThat(settings.getMaxTokens()).isEqualTo(500);
        assertThat(settings.getTopP()).isEqualTo(0.9);
    }
}
