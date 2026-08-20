package cn.cangjiecloud.tool.service;

import cn.cangjiecloud.core.tool.ToolSpecification;

import java.util.List;
import java.util.Map;

/**
 * 工具提供者服务 — 负责将工具实体转换为 AI 可用的 ToolSpecification 列表
 * <p>
 * 用于 Chat 流程中将应用的 tools 注入到 LLM function calling 中
 */
public interface IToolProviderService {

    /**
     * 根据工具 ID 列表获取工具规格
     *
     * @param toolIds 工具 ID 列表
     * @return 工具规格列表
     */
    List<ToolSpecification> getToolSpecifications(List<String> toolIds);

    /**
     * 根据应用 ID 获取该应用关联的所有工具规格
     *
     * @param applicationId 应用 ID
     * @return 工具规格列表
     */
    List<ToolSpecification> getToolSpecificationsByApp(String applicationId);

    /**
     * 执行某个工具调用（由 LLM function calling 触发）
     *
     * @param toolName   function calling 中的 function name
     * @param arguments  调用参数
     * @return 执行结果 JSON
     */
    String executeToolCall(String toolName, Map<String, Object> arguments);
}