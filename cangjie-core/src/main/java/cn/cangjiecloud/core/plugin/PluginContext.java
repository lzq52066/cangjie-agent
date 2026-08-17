package cn.cangjiecloud.core.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 插件执行上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginContext {

    /** 调用参数 */
    private Map<String, Object> params;

    /** 元数据（如 toolId、调用方信息等） */
    private Map<String, Object> metadata;
}
