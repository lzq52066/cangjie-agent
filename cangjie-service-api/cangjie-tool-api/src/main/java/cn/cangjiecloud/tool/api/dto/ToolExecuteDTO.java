package cn.cangjiecloud.tool.api.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ToolExecuteDTO {

    /** 工具 ID */
    private String toolId;

    /** 调用入参 */
    private Map<String, Object> input;
}
