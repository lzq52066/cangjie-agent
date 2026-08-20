package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;

import java.util.List;
import java.util.Map;

/**
 * 工具处理器抽象基类 — 策略模式
 * <p>
 * 每种子类实现具体的工具注册和执行逻辑
 */
public abstract class AbsToolHandler {

    /**
     * 将单条工具实体转换为 LLM 可用的 tool specification
     */
    public abstract ToolSpecification buildToolSpecification(ToolEntity entity);

    /**
     * 批量转换
     */
    public List<ToolSpecification> buildToolSpecifications(List<ToolEntity> entities) {
        return entities.stream().map(this::buildToolSpecification).toList();
    }

    /**
     * 执行工具
     */
    public abstract ToolExecuteResultDTO execute(ToolEntity entity, Map<String, Object> params);
}