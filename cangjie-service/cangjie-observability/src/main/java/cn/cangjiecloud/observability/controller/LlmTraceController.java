package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.observability.dto.LlmTraceQueryDTO;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * 单条 LLM 调用详情
     */
    @GetMapping("/{id}")
    public R<LlmTraceEntity> detail(@PathVariable String id) {
        LlmTraceEntity entity = llmTraceService.getDetail(id);
        return entity != null ? R.data(entity) : R.fail("LLM 调用记录不存在");
    }

    /**
     * 按会话 ID 查询 LLM 调用时间线
     */
    @GetMapping("/timeline")
    public R<List<LlmTraceEntity>> timeline(String sessionId) {
        return R.data(llmTraceService.timeline(sessionId));
    }
}