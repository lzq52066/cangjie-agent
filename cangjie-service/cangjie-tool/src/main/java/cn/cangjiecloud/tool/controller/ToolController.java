package cn.cangjiecloud.tool.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.tool.api.dto.ToolExecuteDTO;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.service.IToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/tool")
public class ToolController {

    private final IToolService toolService;

    @PostMapping
    public R<ToolEntity> create(@RequestBody ToolEntity entity) {
        return R.data(toolService.create(entity));
    }

    @PutMapping("/{id}")
    public R<ToolEntity> update(@PathVariable String id, @RequestBody ToolEntity entity) {
        return R.data(toolService.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        toolService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<ToolEntity> get(@PathVariable String id) {
        return R.data(toolService.getById(id));
    }

    @GetMapping
    public R<List<ToolEntity>> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String type) {
        return R.data(toolService.list(keyword, type));
    }

    @PostMapping("/{id}/execute")
    public R<ToolExecuteResultDTO> execute(@PathVariable String id,
                                           @RequestBody ToolExecuteDTO dto) {
        return R.data(toolService.executeTool(id, dto.getInput()));
    }
}
