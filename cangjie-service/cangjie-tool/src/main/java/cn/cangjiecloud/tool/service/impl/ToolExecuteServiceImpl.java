package cn.cangjiecloud.tool.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.handler.AbsToolHandler;
import cn.cangjiecloud.tool.handler.ToolHandlerRegistry;
import cn.cangjiecloud.tool.service.IToolExecuteService;
import cn.cangjiecloud.tool.service.ToolServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 工具执行服务实现 — 按 toolType 策略分发
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecuteServiceImpl implements IToolExecuteService {

    private final ToolHandlerRegistry handlerRegistry;
    private final ToolServiceImpl toolService;

    @Override
    public ToolExecuteResultDTO httpExecute(String toolId, Map<String, Object> params) {
        ToolEntity entity = getActiveTool(toolId);
        return dispatch(ToolConstants.ToolType.HTTP, entity, params);
    }

    @Override
    public ToolExecuteResultDTO customExecute(String toolId, String scriptCode, Map<String, Object> params) {
        ToolEntity entity = getActiveTool(toolId);
        // 临时注入脚本代码到 config
        if (scriptCode != null) {
            entity.setConfig(scriptCode);
        }
        return dispatch(ToolConstants.ToolType.CUSTOM, entity, params);
    }

    @Override
    public ToolExecuteResultDTO mcpExecute(String toolId, Map<String, Object> params) {
        ToolEntity entity = getActiveTool(toolId);
        return dispatch(ToolConstants.ToolType.MCP, entity, params);
    }

    @Override
    public ToolExecuteResultDTO pluginExecute(String toolId, Map<String, Object> params) {
        ToolEntity entity = getActiveTool(toolId);
        return dispatch(ToolConstants.ToolType.PLUGIN, entity, params);
    }

    private ToolExecuteResultDTO dispatch(String toolType, ToolEntity entity, Map<String, Object> params) {
        AbsToolHandler handler = handlerRegistry.get(toolType);
        if (handler == null) {
            throw new ApiException("不支持的工具类型: " + toolType);
        }
        return handler.execute(entity, params);
    }

    private ToolEntity getActiveTool(String toolId) {
        ToolEntity entity = toolService.getById(toolId);
        if (entity == null) {
            throw new ApiException("工具不存在: " + toolId);
        }
        if (!ToolConstants.STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new ApiException("工具未激活: " + toolId);
        }
        return entity;
    }
}