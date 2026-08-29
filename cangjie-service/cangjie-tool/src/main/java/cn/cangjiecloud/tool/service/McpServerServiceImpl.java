package cn.cangjiecloud.tool.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.McpServerEntity;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.mapper.McpServerMapper;
import cn.cangjiecloud.tool.mcp.McpClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 工具市场服务实现
 * <p>
 * MCP 服务注册后通过 tools/list 自动发现工具，同步为 tool 表记录（toolType=MCP），
 * 即可被应用绑定参与 Function Calling。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl extends ServiceImpl<McpServerMapper, McpServerEntity>
        implements IMcpServerService {

    private final McpClientManager mcpClientManager;
    private final IToolService toolService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerEntity register(McpServerEntity entity) {
        if (!StringUtils.hasText(entity.getServerUrl())) {
            throw new ApiException("MCP 服务地址不能为空");
        }
        if (!StringUtils.hasText(entity.getName())) {
            throw new ApiException("MCP 服务名称不能为空");
        }
        McpServerEntity existing = getOne(new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getServerUrl, entity.getServerUrl())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new ApiException("该 MCP 服务已注册: " + existing.getName());
        }
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("active");
        }
        entity.setToolCount(0);
        save(entity);
        log.info("MCP 服务已注册: {} -> {}", entity.getName(), entity.getServerUrl());
        return entity;
    }

    @Override
    public List<McpServerEntity> listServers() {
        return list(new LambdaQueryWrapper<McpServerEntity>()
                .orderByDesc(McpServerEntity::getCreateTime));
    }

    @Override
    public McpServerEntity testConnection(String id) {
        McpServerEntity entity = requireServer(id);
        boolean ok;
        try {
            ok = mcpClientManager.testConnection(entity.getServerUrl());
        } catch (Exception e) {
            log.warn("MCP 连通性测试异常: {} -> {}", entity.getServerUrl(), e.getMessage());
            ok = false;
        }
        entity.setLastCheckTime(LocalDateTime.now());
        entity.setLastCheckResult(ok ? "success" : "failed");
        updateById(entity);
        log.info("MCP 连通性测试: {} -> {}", entity.getServerUrl(), ok ? "success" : "failed");
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> syncTools(String id) {
        McpServerEntity entity = requireServer(id);
        List<Map<String, Object>> discovered;
        try {
            discovered = mcpClientManager.listTools(entity.getServerUrl(), true);
        } catch (Exception e) {
            throw new ApiException("MCP 工具发现失败: " + e.getMessage());
        }

        // 该服务已同步的工具（按 functionName 索引）
        List<ToolEntity> mcpTools = toolService.list(new LambdaQueryWrapper<ToolEntity>()
                .eq(ToolEntity::getToolType, ToolConstants.ToolType.MCP));
        Map<String, ToolEntity> existingByName = new HashMap<>();
        for (ToolEntity tool : mcpTools) {
            if (isServerTool(tool, entity.getServerUrl())) {
                existingByName.put(tool.getFunctionName(), tool);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> tool : discovered) {
            String name = tool.get("name") != null ? tool.get("name").toString() : null;
            if (!StringUtils.hasText(name)) {
                continue;
            }
            seen.add(name);
            Object desc = tool.get("description");
            Object schema = tool.get("inputSchema");

            ToolEntity target = existingByName.get(name);
            boolean isNew = target == null;
            if (isNew) {
                target = new ToolEntity();
            }
            target.setName(name);
            target.setDescription(desc != null ? desc.toString() : name);
            target.setType("function");
            target.setToolType(ToolConstants.ToolType.MCP);
            target.setFunctionName(name);
            target.setParameters(schema != null ? JSON.toJSONString(schema) : "{}");
            target.setConfig(JSON.toJSONString(Map.of("serverUrl", entity.getServerUrl())));
            target.setStatus(ToolConstants.STATUS_ACTIVE);
            target.setCategory("MCP:" + entity.getName());
            toolService.saveOrUpdate(target);
            result.add(Map.of("name", name, "isNew", isNew));
        }

        // 服务端已移除的工具置为失效
        for (Map.Entry<String, ToolEntity> entry : existingByName.entrySet()) {
            if (!seen.contains(entry.getKey())) {
                entry.getValue().setStatus("inactive");
                toolService.updateById(entry.getValue());
            }
        }

        entity.setToolCount(seen.size());
        entity.setLastCheckTime(LocalDateTime.now());
        entity.setLastCheckResult("success");
        updateById(entity);
        log.info("MCP 工具同步完成: {} -> {} 个工具", entity.getName(), seen.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteServer(String id) {
        McpServerEntity entity = requireServer(id);
        // 失效该服务同步出的工具
        List<ToolEntity> mcpTools = toolService.list(new LambdaQueryWrapper<ToolEntity>()
                .eq(ToolEntity::getToolType, ToolConstants.ToolType.MCP));
        for (ToolEntity tool : mcpTools) {
            if (isServerTool(tool, entity.getServerUrl())) {
                tool.setStatus("inactive");
                toolService.updateById(tool);
            }
        }
        mcpClientManager.evictSession(entity.getServerUrl());
        removeById(id);
        log.info("MCP 服务已删除: {} -> {}", entity.getName(), entity.getServerUrl());
    }

    private boolean isServerTool(ToolEntity tool, String serverUrl) {
        if (!StringUtils.hasText(tool.getConfig())) {
            return false;
        }
        try {
            Map<String, Object> config = JSON.parseObject(tool.getConfig());
            return serverUrl.equals(config.get("serverUrl"));
        } catch (Exception e) {
            return false;
        }
    }

    private McpServerEntity requireServer(String id) {
        McpServerEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("MCP 服务不存在");
        }
        return entity;
    }
}
