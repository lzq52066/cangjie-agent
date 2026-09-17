package cn.cangjiecloud.model.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.model.api.dto.ModelCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelTestDTO;
import cn.cangjiecloud.model.api.dto.ModelUpdateDTO;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.service.IModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/model")
public class ModelController {

    private final IModelService modelService;

    @PostMapping
    public R<ModelEntity> create(@Valid @RequestBody ModelCreateDTO dto) {
        return R.data(modelService.create(dto));
    }

    @PutMapping("/{id}")
    public R<ModelEntity> update(@PathVariable String id, @RequestBody ModelUpdateDTO dto) {
        return R.data(modelService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        modelService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<ModelEntity> get(@PathVariable String id) {
        return R.data(modelService.getById(id));
    }

    @GetMapping
    public R<PageResult<ModelEntity>> list(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String modelType,
                                           @RequestParam(required = false) String providerId,
                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(modelService.pageQuery(keyword, modelType, providerId, pageNum, pageSize)));
    }

    @PostMapping("/test")
    public R<String> test(@RequestBody ModelTestDTO dto) {
        return R.data(modelService.testModel(dto.getModelId(), dto.getMessage()));
    }

    @PutMapping("/{id}/default")
    public R<Void> setDefault(@PathVariable String id) {
        modelService.setDefault(id);
        return R.ok();
    }
}
