package cn.cangjiecloud.tool.bootstrap;

import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.service.IToolService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 内置工具引导：启动时从 classpath:builtin-tools/tools.json 幂等导入一批官方工具。
 * <p>
 * 规则：
 * <ul>
 *     <li>固定 ID 不存在 → 新增；</li>
 *     <li>已存在（未被删除）→ 用内置定义覆盖核心字段，便于随版本升级脚本/配置；</li>
 *     <li>被用户删除（逻辑删除）的不复活；</li>
 *     <li>任何单条失败都不影响其它工具和应用启动。</li>
 * </ul>
 * 可通过 cangjie.tools.import-builtin=false 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinToolBootstrap implements ApplicationRunner {

    private static final String DEFINITION_LOCATION = "classpath*:builtin-tools/tools.json";
    private static final String SCRIPT_BASE = "builtin-tools/";

    private final IToolService toolService;

    @Value("${cangjie.tools.import-builtin:true}")
    private boolean importBuiltin;

    @Override
    public void run(ApplicationArguments args) {
        if (!importBuiltin) {
            log.info("内置工具导入已关闭（cangjie.tools.import-builtin=false）");
            return;
        }
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(DEFINITION_LOCATION);
            int inserted = 0;
            int updated = 0;
            for (Resource resource : resources) {
                String json;
                try {
                    json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    log.warn("内置工具定义读取失败: {} -> {}", resource.getFilename(), e.getMessage());
                    continue;
                }
                List<JSONObject> tools = JSON.parseObject(json).getJSONArray("tools").toJavaList(JSONObject.class);
                for (JSONObject def : tools) {
                    try {
                        String outcome = upsert(def);
                        if ("inserted".equals(outcome)) {
                            inserted++;
                        } else if ("updated".equals(outcome)) {
                            updated++;
                        }
                    } catch (Exception e) {
                        log.warn("内置工具[{}]导入失败: {}", def.getString("id"), e.getMessage());
                    }
                }
            }
            if (inserted > 0 || updated > 0) {
                log.info("内置工具导入完成: 新增 {} 个, 更新 {} 个", inserted, updated);
            }
        } catch (Exception e) {
            log.warn("内置工具扫描失败（不影响启动）: {}", e.getMessage());
        }
    }

    /** @return inserted / updated / skipped */
    private String upsert(JSONObject def) throws Exception {
        String id = def.getString("id");
        String toolType = def.getString("toolType");

        ToolEntity entity = buildEntity(def, toolType);

        ToolEntity existing = toolService.getById(id);
        if (existing == null) {
            // 显式 ID，避免被雪花 ID 覆盖
            entity.setId(id);
            entity.setStatus(ToolConstants.STATUS_ACTIVE);
            toolService.save(entity);
            return "inserted";
        }
        // 已存在：覆盖内置定义的核心字段（保留 id、状态与创建审计）
        entity.setId(id);
        entity.setStatus(existing.getStatus() != null ? existing.getStatus() : ToolConstants.STATUS_ACTIVE);
        toolService.updateById(entity);
        return "updated";
    }

    private ToolEntity buildEntity(JSONObject def, String toolType) throws Exception {
        ToolEntity entity = new ToolEntity();
        entity.setName(def.getString("name"));
        entity.setDescription(def.getString("description"));
        entity.setCategory(def.getString("category"));
        entity.setIcon(def.getString("icon"));
        // type（旧字段）与 toolType（新分发字段）双写
        entity.setToolType(toolType);
        entity.setType(toolType);
        entity.setFunctionName(null);

        JSONObject parameters = def.getJSONObject("parameters");
        entity.setParameters(parameters != null ? JSON.toJSONString(parameters) : null);

        switch (toolType) {
            case ToolConstants.ToolType.CUSTOM -> {
                // config 为 Groovy 脚本文本：优先读 scriptFile
                String scriptFile = def.getString("scriptFile");
                if (scriptFile == null || scriptFile.isBlank()) {
                    throw new IllegalArgumentException("CUSTOM 工具缺少 scriptFile");
                }
                entity.setConfig(readClasspath(SCRIPT_BASE + scriptFile));
            }
            case ToolConstants.ToolType.PLUGIN -> {
                String implementation = def.getString("implementation");
                if (implementation == null || implementation.isBlank()) {
                    throw new IllegalArgumentException("PLUGIN 工具缺少 implementation");
                }
                entity.setImplementation(implementation);
                entity.setConfig(null);
            }
            case ToolConstants.ToolType.HTTP -> {
                JSONObject config = def.getJSONObject("config");
                if (config == null) {
                    throw new IllegalArgumentException("HTTP 工具缺少 config");
                }
                entity.setConfig(JSON.toJSONString(config));
            }
            default -> throw new IllegalArgumentException("内置工具暂不支持的类型: " + toolType);
        }
        return entity;
    }

    private String readClasspath(String classpathLocation) throws Exception {
        Resource resource = new PathMatchingResourcePatternResolver().getResource("classpath:" + classpathLocation);
        if (!resource.exists()) {
            throw new IllegalArgumentException("脚本文件不存在: " + classpathLocation);
        }
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
