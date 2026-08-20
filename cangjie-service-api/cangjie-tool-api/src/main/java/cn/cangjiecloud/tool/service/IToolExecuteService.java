package cn.cangjiecloud.tool.service;

import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;

import java.util.Map;

/**
 * 工具执行服务接口 — 按 toolType 分发执行
 */
public interface IToolExecuteService {

    /**
     * HTTP 工具执行
     *
     * @param toolId 工具 ID
     * @param params 请求参数
     */
    ToolExecuteResultDTO httpExecute(String toolId, Map<String, Object> params);

    /**
     * 自定义脚本执行（Groovy）
     *
     * @param toolId     工具 ID
     * @param scriptCode 脚本代码
     * @param params     脚本参数
     */
    ToolExecuteResultDTO customExecute(String toolId, String scriptCode, Map<String, Object> params);

    /**
     * MCP 工具执行
     *
     * @param toolId 工具 ID
     * @param params 调用参数
     */
    ToolExecuteResultDTO mcpExecute(String toolId, Map<String, Object> params);

    /**
     * Plugin 反射执行（兼容旧版）
     *
     * @param toolId 工具 ID
     * @param params 调用参数
     */
    ToolExecuteResultDTO pluginExecute(String toolId, Map<String, Object> params);
}