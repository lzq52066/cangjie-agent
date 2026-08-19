package cn.cangjiecloud.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationVersionDTO {
    private String id;
    private String applicationId;
    private Integer version;
    private String name;
    private String description;
    private String type;
    private String modelId;
    private String knowledgeBaseIds;
    private String promptTemplateId;
    private String skillIds;
    private String ruleIds;
    private Boolean memoryEnabled;
    private Integer maxTurns;
    private Double temperature;
    private String config;
    private String suggestions;
    private String icon;
    private String publishLog;
    private String publishBy;
    private String createTime;
}