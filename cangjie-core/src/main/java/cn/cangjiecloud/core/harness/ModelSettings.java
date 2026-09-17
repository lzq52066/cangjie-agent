package cn.cangjiecloud.core.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单次模型调用的采样参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelSettings {

    /** 温度（0~2） */
    @Builder.Default
    private double temperature = 0.7;

    /** 最大输出 token 数（0 表示不限制） */
    private int maxTokens;

    /** top_p */
    @Builder.Default
    private double topP = 1.0;

    /** 模型特有扩展参数 */
    private Map<String, Object> extra;

    public static ModelSettings defaults() {
        return ModelSettings.builder().build();
    }
}
