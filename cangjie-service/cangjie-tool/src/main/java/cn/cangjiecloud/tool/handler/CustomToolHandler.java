package cn.cangjiecloud.tool.handler;

import com.alibaba.fastjson.JSON;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.annotation.ToolHandlerType;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.executor.GroovyScriptExecutor;
import cn.cangjiecloud.tool.util.ToolNaming;
import cn.cangjiecloud.core.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 自定义脚本工具处理器（Groovy）
 */
@Slf4j
@Component
@ToolHandlerType(ToolConstants.ToolType.CUSTOM)
public class CustomToolHandler extends AbsToolHandler {

    @Override
    public ToolSpecification buildToolSpecification(ToolEntity entity) {
        return ToolSpecification.builder()
                .toolId(entity.getId())
                .name(ToolNaming.buildToolName(entity.getId()))
                .description(entity.getDescription() != null ? entity.getDescription() : entity.getName())
                .toolType(ToolConstants.ToolType.CUSTOM)
                .parameters(parseParameters(entity.getParameters()))
                .build();
    }

    @Override
    public ToolExecuteResultDTO execute(ToolEntity entity, Map<String, Object> params) {
        long start = System.currentTimeMillis();
        try {
            String scriptCode = entity.getConfig();
            GroovyScriptExecutor.ScriptResult result =
                    new GroovyScriptExecutor().execute(scriptCode, params);

            if (result.isSuccess()) {
                return ToolExecuteResultDTO.builder()
                        .success(true)
                        .output(result.result())
                        .executionTime(result.durationMs())
                        .build();
            } else {
                return ToolExecuteResultDTO.builder()
                        .success(false)
                        .error(result.error())
                        .executionTime(result.durationMs())
                        .build();
            }
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("自定义工具执行失败: {} ({})", entity.getName(), entity.getId(), e);
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error(e.getMessage())
                    .executionTime(cost)
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParameters(String parameters) {
        if (parameters == null || parameters.isEmpty()) return Map.of();
        try {
            return com.alibaba.fastjson.JSON.parseObject(parameters);
        } catch (Exception e) {
            return Map.of();
        }
    }
}