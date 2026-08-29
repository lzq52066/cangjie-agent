package cn.cangjiecloud.tool.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.tool.entity.McpServerEntity;
import cn.cangjiecloud.tool.service.IMcpServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具市场：MCP 服务注册、连通性测试、工具发现同步
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/mcp")
public class McpServerController {

    private final IMcpServerService mcpServerService;

    @GetMapping
    public R<List<McpServerEntity>> list() {
        return R.data(mcpServerService.listServers());
    }

    @PostMapping
    public R<McpServerEntity> register(@RequestBody McpServerEntity entity) {
        return R.data(mcpServerService.register(entity));
    }

    /**
     * 连通性测试（强制重新握手）
     */
    @PostMapping("/{id}/test")
    public R<McpServerEntity> test(@PathVariable String id) {
        return R.data(mcpServerService.testConnection(id));
    }

    /**
     * 发现并同步工具到工具库
     */
    @PostMapping("/{id}/sync")
    public R<List<Map<String, Object>>> sync(@PathVariable String id) {
        return R.data(mcpServerService.syncTools(id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        mcpServerService.deleteServer(id);
        return R.ok();
    }
}
