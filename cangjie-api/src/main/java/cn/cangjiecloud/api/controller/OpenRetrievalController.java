package cn.cangjiecloud.api.controller;

import cn.cangjiecloud.knowledge.api.dto.RetrievalQueryDTO;
import cn.cangjiecloud.knowledge.api.dto.RetrievalResultDTO;
import cn.cangjiecloud.knowledge.service.IRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对外 RAG 检索接口（检索即服务）
 * <p>
 * 路径前缀 /api/open（免登录白名单），由 OpenApiAuthInterceptor 统一做 API Key 鉴权。
 * 供外部系统调用知识库混合检索能力（向量 + 全文 + 加权融合 + 可选重排）。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("${cangjie.openapi.prefix:/api/open}")
public class OpenRetrievalController {

    private final IRetrievalService retrievalService;

    /**
     * 知识库混合检索
     */
    @PostMapping("/retrieval/search")
    public List<RetrievalResultDTO> search(@RequestBody RetrievalQueryDTO query) {
        return retrievalService.retrieve(query);
    }
}