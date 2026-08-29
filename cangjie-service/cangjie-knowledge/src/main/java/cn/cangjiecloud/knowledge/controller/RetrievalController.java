package cn.cangjiecloud.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.knowledge.api.dto.RetrievalQueryDTO;
import cn.cangjiecloud.knowledge.api.dto.RetrievalResultDTO;
import cn.cangjiecloud.knowledge.service.IRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/knowledge/retrieval")
public class RetrievalController {

    private final IRetrievalService retrievalService;

    @PostMapping("/search")
    public R<List<RetrievalResultDTO>> search(@RequestBody RetrievalQueryDTO query) {
        return R.data(retrievalService.retrieve(query));
    }

    /**
     * 命中测试：调试检索效果，返回每条结果的向量分/全文分/融合分与问题路命中标记
     */
    @PostMapping("/hit-test")
    public R<List<RetrievalResultDTO>> hitTest(@RequestBody RetrievalQueryDTO query) {
        return R.data(retrievalService.retrieve(query));
    }
}
