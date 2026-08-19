package cn.cangjiecloud.application.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.application.api.dto.ApplicationRollbackDTO;
import cn.cangjiecloud.application.api.dto.ApplicationVersionDTO;
import cn.cangjiecloud.application.service.IApplicationVersionService;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/application/version")
public class ApplicationVersionController {

    private final IApplicationVersionService applicationVersionService;

    @GetMapping("/application/{applicationId}")
    public R<List<ApplicationVersionDTO>> listByApplication(@PathVariable String applicationId) {
        return R.data(applicationVersionService.listByApplication(applicationId));
    }

    @GetMapping("/{versionId}")
    public R<ApplicationVersionDTO> get(@PathVariable String versionId) {
        return R.data(applicationVersionService.getVersion(versionId));
    }

    @PostMapping("/rollback")
    public R<Void> rollback(@RequestParam String applicationId,
                            @Valid @RequestBody ApplicationRollbackDTO dto) {
        applicationVersionService.rollback(applicationId, dto.getVersionId());
        return R.ok("应用已回滚到指定版本");
    }

    @DeleteMapping("/{versionId}")
    public R<Void> delete(@PathVariable String versionId) {
        applicationVersionService.delete(versionId);
        return R.ok("版本已删除");
    }
}