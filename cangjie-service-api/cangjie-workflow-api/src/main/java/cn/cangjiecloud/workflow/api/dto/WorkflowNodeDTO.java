package cn.cangjiecloud.workflow.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 工作流节点定义 DTO
 * <p>
 * 对应工作流编辑器中的节点描述，序列化为 JSON 后存入 workflow.nodes。
 */
@Data
public class WorkflowNodeDTO {

    /** 节点 ID */
    private String id;

    /** 节点类型：start/llm/knowledge/tool/condition/loop/end/api/code */
    private String type;

    /** 节点名称 */
    private String name;

    /** 节点配置 */
    private Map<String, Object> config;

    /** 节点位置（前端画布坐标） */
    private Map<String, Object> position;
}
