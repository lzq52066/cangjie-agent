package cn.cangjiecloud.core.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具规格定义 — 用于生成 function calling 的 tool specification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolSpecification {

    /** 工具名称（function calling 中的 function name） */
    private String name;

    /** 工具描述（供 LLM 理解何时调用） */
    private String description;

    /** JSON Schema 格式的参数定义 */
    private Map<String, Object> parameters;

    /** 工具唯一标识（用于执行时代理分发） */
    private String toolId;

    /** 工具类型：HTTP / CUSTOM / MCP / SKILL / PLUGIN */
    private String toolType;
}