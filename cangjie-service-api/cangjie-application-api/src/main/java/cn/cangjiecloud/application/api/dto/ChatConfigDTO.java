package cn.cangjiecloud.application.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 对话入口配置：应用名称、建议问题等，供 chat 前端按应用加载。
 */
@Data
public class ChatConfigDTO {

    /** 应用名称 */
    private String title;

    /** 建议问题列表 */
    private List<String> suggestions;
}
