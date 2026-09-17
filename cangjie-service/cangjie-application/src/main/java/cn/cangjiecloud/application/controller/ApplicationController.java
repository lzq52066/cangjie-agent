package cn.cangjiecloud.application.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.application.api.dto.ApplicationCreateDTO;
import cn.cangjiecloud.application.api.dto.ApplicationVersionDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.application.service.IApplicationVersionService;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/application")
public class ApplicationController {

    private final IApplicationService applicationService;
    private final IApplicationVersionService applicationVersionService;

    @PostMapping
    public R<ApplicationEntity> create(@Valid @RequestBody ApplicationCreateDTO dto) {
        return R.data(applicationService.create(dto));
    }

    @PutMapping("/{id}")
    public R<ApplicationEntity> update(@PathVariable String id, @RequestBody ApplicationCreateDTO dto) {
        return R.data(applicationService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        applicationService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<ApplicationEntity> get(@PathVariable String id) {
        return R.data(applicationService.getById(id));
    }

    @GetMapping
    public R<PageResult<ApplicationEntity>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(applicationService.pageQuery(keyword, type, pageNum, pageSize)));
    }

    @PostMapping("/{id}/publish")
    @Transactional(rollbackFor = Exception.class)
    public R<ApplicationEntity> publish(@PathVariable String id,
                                        @RequestParam(required = false) String operator) {
        ApplicationEntity published = applicationService.publish(id);
        applicationVersionService.publish(id, operator != null ? operator : "system");
        return R.data(published);
    }

    @GetMapping("/apikey/{apikey}")
    public R<ApplicationEntity> getByApikey(@PathVariable String apikey) {
        return R.data(applicationService.getByApikey(apikey));
    }
}