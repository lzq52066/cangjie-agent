package cn.cangjiecloud.tool.service;

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
     * 执行工具
     *
     * @param toolId 工具 ID
     * @param input  调用入参
     */
    ToolExecuteResultDTO executeTool(String toolId, Map<String, Object> input);
}
