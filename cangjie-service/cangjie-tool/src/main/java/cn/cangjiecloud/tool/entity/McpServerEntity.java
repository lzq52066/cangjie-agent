package cn.cangjiecloud.tool.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * MCP 服务注册实体（工具市场）
 * <p>
 * 每个 MCP 服务可发现并同步其工具列表到 tool 表（toolType=MCP）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "mcp_server", autoResultMap = true)
public class McpServerEntity extends BaseEntity {

    /** 服务名称 */
    private String name;

    /** 服务描述 */
    private String description;

    /** MCP 服务地址（JSON-RPC over HTTP） */
    private String serverUrl;

    /** 状态：active / inactive */
    private String status;

    /** 最近连通性检查时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCheckTime;

    /** 最近连通性检查结果：success / failed */
    private String lastCheckResult;

    /** 已同步工具数量 */
    private Integer toolCount;
}
