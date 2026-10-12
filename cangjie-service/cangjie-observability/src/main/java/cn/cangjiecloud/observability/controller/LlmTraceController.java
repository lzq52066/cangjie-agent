package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.observability.dto.LlmTraceQueryDTO;
import cn.cangjiecloud.observability.dto.OnlineEvalTrendDTO;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import cn.cangjiecloud.observability.service.ILlmTraceStatsService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * LLM 调用追踪查询接口
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/observability/llm-traces")
public class LlmTraceController {

    private final ILlmTraceService llmTraceService;
    private final ILlmTraceStatsService llmTraceStatsService;

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

    /**
     * 在线评测的水位与趋势：评了多少、积压多少、各指标均值按天怎么走。
     * <p>
     * 挂在调用记录同一个接口下是因为它们回答的是同一个问题：「线上现在好不好」。
     * 均值必须跟覆盖率一起看 —— 只评了 3% 的样本，那条曲线基本只是噪声。
     */
    @GetMapping("/eval-trend")
    public R<OnlineEvalTrendDTO> evalTrend(@RequestParam(defaultValue = "7") int days) {
        return R.data(llmTraceStatsService.onlineEvalTrend(days));
    }
}