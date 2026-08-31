package cn.cangjiecloud.application.template;

import cn.cangjiecloud.application.entity.ApplicationTemplateEntity;
import cn.cangjiecloud.application.service.IApplicationTemplateService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 官方内置模板引导：启动时从 classpath:official-templates/*.json 幂等导入
 * <p>
 * 规则：templateKey 不存在 → 导入；已存在且内置版本更高 → 覆盖升级；
 * 用户自定义过的内置模板若版本不高于内置版本则不覆盖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficialTemplateBootstrap implements ApplicationRunner {

    private static final String TEMPLATE_LOCATION = "classpath*:official-templates/*.json";

    private final IApplicationTemplateService templateService;

    @Value("${cangjie.templates.import-builtin:true}")
    private boolean importBuiltin;

    @Override
    public void run(ApplicationArguments args) {
        if (!importBuiltin) {
            log.info("官方内置模板导入已关闭（cangjie.templates.import-builtin=false）");
            return;
        }
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(TEMPLATE_LOCATION);
            int imported = 0;
            int upgraded = 0;
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    String json = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    String outcome = importIfNeeded(json);
                    if ("imported".equals(outcome)) {
                        imported++;
                    } else if ("upgraded".equals(outcome)) {
                        upgraded++;
                    }
                } catch (Exception e) {
                    log.warn("内置模板导入失败: {} -> {}", resource.getFilename(), e.getMessage());
                }
            }
            if (imported > 0 || upgraded > 0) {
                log.info("官方内置模板导入完成: 新增 {} 个, 升级 {} 个", imported, upgraded);
            }
        } catch (Exception e) {
            log.warn("官方内置模板扫描失败（不影响启动）: {}", e.getMessage());
        }
    }

    private String importIfNeeded(String bundleJson) {
        JSONObject bundle = JSON.parseObject(bundleJson);
        String templateKey = bundle.getString("templateKey");
        int bundleVersion = bundle.getIntValue("version") > 0 ? bundle.getIntValue("version") : 1;

        ApplicationTemplateEntity existing = templateKey == null ? null
                : templateService.getOne(new LambdaQueryWrapper<ApplicationTemplateEntity>()
                        .eq(ApplicationTemplateEntity::getTemplateKey, templateKey)
                        .last("LIMIT 1"));

        if (existing == null) {
            templateService.importBundle(bundleJson, true);
            return "imported";
        }
        if (Boolean.TRUE.equals(existing.getBuiltin())
                && (existing.getVersion() == null || bundleVersion > existing.getVersion())) {
            templateService.importBundle(bundleJson, true);
            return "upgraded";
        }
        return "skipped";
    }
}
