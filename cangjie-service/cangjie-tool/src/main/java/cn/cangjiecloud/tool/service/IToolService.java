package cn.cangjiecloud.tool.service;

import cn.cangjiecloud.core.tool.ToolSpecification;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;

import java.util.List;
import java.util.Map;

public interface IToolService extends IService<ToolEntity> {

    ToolEntity create(ToolEntity entity);

    ToolEntity update(String id, ToolEntity entity);

    void delete(String id);

    List<ToolEntity> list(String keyword, String type);

    /**
     * 执行工具（策略分发）
     *
     * @param toolId 工具 ID
     * @param input  调用入参
     */
    ToolExecuteResultDTO executeTool(String toolId, Map<String, Object> input);

    /**
     * 获取激活的工具规格列表（用于 LLM function calling）
     *
     * @param toolIds 工具 ID 列表
     * @return ToolSpecification 列表
     */
    List<ToolSpecification> getToolSpecifications(List<String> toolIds);

    /**
     * 根据名称解析并执行工具调用（由 LLM function calling 触发）
     *
     * @param toolName  function calling name
     * @param arguments 入参
     * @return 执行结果字符串
     */
    String executeToolCall(String toolName, Map<String, Object> arguments);

    /**
     * 获取激活的技能规格列表（用于 LLM function calling）
     *
     * @param skillIds 技能 ID 列表
     * @return ToolSpecification 列表
     */
    List<ToolSpecification> getSkillSpecifications(List<String> skillIds);
}
