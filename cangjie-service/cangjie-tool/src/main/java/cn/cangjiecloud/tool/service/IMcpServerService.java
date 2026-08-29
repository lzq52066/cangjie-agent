package cn.cangjiecloud.tool.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.tool.entity.McpServerEntity;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具市场服务：MCP 服务的注册、连通性测试与工具发现同步
 */
public interface IMcpServerService extends IService<McpServerEntity> {

    McpServerEntity register(McpServerEntity entity);

    List<McpServerEntity> listServers();

    /**
     * 连通性测试（强制重新握手），并回写检查结果
     */
    McpServerEntity testConnection(String id);

    /**
     * 发现并同步工具：将 MCP 服务的工具列表同步为 tool 表记录（toolType=MCP）
     *
     * @return 同步后的工具列表（名称/描述）
     */
    List<Map<String, Object>> syncTools(String id);

    void deleteServer(String id);
}
