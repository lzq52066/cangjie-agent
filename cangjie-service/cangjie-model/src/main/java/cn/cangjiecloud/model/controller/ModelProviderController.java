package cn.cangjiecloud.model.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.model.api.dto.ModelProviderCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelProviderUpdateDTO;
import cn.cangjiecloud.model.entity.ModelProviderEntity;
import cn.cangjiecloud.model.service.IModelProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 厂商管理：统一维护各模型厂商的 API Key 与 Base URL
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/model-provider")
public class ModelProviderController {

    private final IModelProviderService modelProviderService;

    @PostMapping
    public R<ModelProviderEntity> create(@Valid @RequestBody ModelProviderCreateDTO dto) {
        return R.data(modelProviderService.create(dto));
    }

    @PutMapping("/{id}")
    public R<ModelProviderEntity> update(@PathVariable String id, @RequestBody ModelProviderUpdateDTO dto) {
        return R.data(modelProviderService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        modelProviderService.delete(id);
        return R.ok();
    }

    @GetMapping
    public R<PageResult<ModelProviderEntity>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(modelProviderService.pageQuery(keyword, status, pageNum, pageSize)));
    }
}
