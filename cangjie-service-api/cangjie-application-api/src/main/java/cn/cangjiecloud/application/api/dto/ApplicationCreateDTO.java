package cn.cangjiecloud.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ApplicationCreateDTO {

    @NotBlank(message = "应用名称不能为空")
    private String name;

    private String description;

    /** 应用类型：chat / agent / workflow */
    @NotBlank(message = "应用类型不能为空")
    private String type;

    /** 关联模型 ID */
    private String modelId;

    /** 知识库 ID 列表（JSON 数组） */
    private List<String> knowledgeBaseIds;

    /** 提示词模板 ID */
    private String promptTemplateId;

    /** 技能 ID 列表（JSON 数组） */
    private List<String> skillIds;

    /** 规则 ID 列表（JSON 数组） */
    private List<String> ruleIds;

    /** 工具 ID 列表（JSON 数组） */
    private List<String> toolIds;

    /** 是否启用记忆 */
    private Boolean memoryEnabled = false;

    /** 最大对话轮数 */
    private Integer maxTurns = 20;

    /** 温度参数 */
    private Double temperature = 0.7;

    /** 额外配置（JSON） */
    private String config;

    /** 建议问题列表（用于 chat 入口欢迎页展示） */
    private List<String> suggestions;

    /** 图标 */
    private String icon;

    /** token 配额（0 表示不限制） */
    private Long tokenQuota;
}
