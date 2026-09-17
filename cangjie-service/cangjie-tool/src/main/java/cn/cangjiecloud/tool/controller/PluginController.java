package cn.cangjiecloud.tool.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.tool.entity.PluginEntity;
import cn.cangjiecloud.tool.service.IPluginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/plugin")
public class PluginController {

    private final IPluginService pluginService;

    @PostMapping
    public R<PluginEntity> create(@RequestBody PluginEntity entity) {
        return R.data(pluginService.create(entity));
    }

    @PutMapping("/{id}")
    public R<PluginEntity> update(@PathVariable String id, @RequestBody PluginEntity entity) {
        return R.data(pluginService.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        pluginService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<PluginEntity> get(@PathVariable String id) {
        return R.data(pluginService.getById(id));
    }

    @GetMapping
    public R<PageResult<PluginEntity>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(pluginService.pageQuery(keyword, type, pageNum, pageSize)));
    }

    @PostMapping("/{id}/reload")
    public R<PluginEntity> reload(@PathVariable String id) {
        return R.data(pluginService.reload(id));
    }

    @GetMapping("/scan")
    public R<List<PluginEntity>> scan() {
        return R.data(pluginService.scanPlugins());
    }
}
