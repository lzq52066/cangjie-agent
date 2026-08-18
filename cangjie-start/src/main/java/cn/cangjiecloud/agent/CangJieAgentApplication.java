package cn.cangjiecloud.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import cn.cangjiecloud.oss.storage.MinioProperties;

@EnableAsync
@EnableCaching
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = "cn.cangjiecloud")
@EnableConfigurationProperties(MinioProperties.class)
@MapperScan("cn.cangjiecloud.**.mapper")
@SpringBootApplication(scanBasePackages = "cn.cangjiecloud", exclude = {SecurityAutoConfiguration.class})
public class CangJieAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CangJieAgentApplication.class, args);
    }
}
