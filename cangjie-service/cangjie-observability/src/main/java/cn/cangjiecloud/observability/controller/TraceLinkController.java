package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.observability.dto.TraceLinkDetailVO;
import cn.cangjiecloud.observability.dto.TraceLinkQueryDTO;
import cn.cangjiecloud.observability.dto.TraceLinkVO;
import cn.cangjiecloud.observability.service.ITraceLinkService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链路追踪接口：按 traceId 把请求级 span、Agent 执行与模型调用合成一个视图。
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/observability/trace-links")
public class TraceLinkController {

    private final ITraceLinkService traceLinkService;

    /**
     * 链路摘要分页
     */
    @GetMapping
    public R<IPage<TraceLinkVO>> page(TraceLinkQueryDTO query) {
        return R.data(traceLinkService.pageQuery(query));
    }

    /**
     * 单条链路明细。链路键可能带 # 占位前缀，因此用查询参数而非路径变量传递。
     */
    @GetMapping("/detail")
    public R<TraceLinkDetailVO> detail(@RequestParam String linkId) {
        TraceLinkDetailVO detail = traceLinkService.detail(linkId);
        return detail != null ? R.data(detail) : R.fail("链路不存在或已超出保留期");
    }
}
