package cn.cangjiecloud.common.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "system")
public class SystemProperties {

    private String defaultUsername = "admin";
    private String defaultPassword = "cangjie@123456";
    private String defaultEmail = "admin@cangjiecloud.cn";
    private String defaultPhone = "18800000000";

    private String jwtSecretKey = "cangjie-cloud-secret-key-change-me-pls";

    /**
     * 前端静态资源嵌入上下文路径
     */
    private String frontContextPath = "/";
}
