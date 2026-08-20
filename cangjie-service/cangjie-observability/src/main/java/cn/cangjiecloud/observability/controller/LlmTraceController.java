package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.observability.dto.LlmTraceQueryDTO;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 调用追踪查询接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/observability/llm-traces")
public class LlmTraceController {

    private final ILlmTraceService llmTraceService;

    /**
     * LLM 调用追踪分页查询
     */
    @GetMapping
    public R<IPage<LlmTraceEntity>> page(LlmTraceQueryDTO query) {
        return R.data(llmTraceService.pageQuery(query));
    }
}