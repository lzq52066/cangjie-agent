package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.QueryRewriterFactory;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.knowledge.api.dto.RetrievalQueryDTO;
import cn.cangjiecloud.knowledge.api.dto.RetrievalResultDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import cn.cangjiecloud.knowledge.service.IRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements IRetrievalService {

    private final HybridRetriever hybridRetriever;
    private final QueryRewriterFactory queryRewriterFactory;
    private final IKnowledgeBaseService knowledgeBaseService;

    @Value("${cangjie.rag.query-rewrite.type:none}")
    private String queryRewriteType;

    @Override
    public List<RetrievalResultDTO> retrieve(RetrievalQueryDTO query) {
        int topK = query.getTopK() != null ? query.getTopK() : 5;
        double similarityThreshold = query.getSimilarityThreshold() != null ? query.getSimilarityThreshold() : 0.0;

        // 查询改写（可选开启；retrieval 场景无对话历史，仅对 query 做消歧扩写）
        String retrievalQuery = query.getQuery();
        if (Boolean.TRUE.equals(query.getRewrite())) {
            retrievalQuery = queryRewriterFactory.get(queryRewriteType)
                    .rewrite(retrievalQuery, java.util.List.of());
        }

        List<RetrievalResult> results;
        String searchMode = resolveSearchMode(query);
        if (query.getKnowledgeBaseIds() != null && !query.getKnowledgeBaseIds().isEmpty()) {
            results = hybridRetriever.retrieve(retrievalQuery, query.getKnowledgeBaseIds(), topK, similarityThreshold, searchMode);
        } else if (query.getKnowledgeBaseId() != null) {
            results = hybridRetriever.retrieve(retrievalQuery, query.getKnowledgeBaseId(), topK, similarityThreshold);
        } else {
            throw new IllegalArgumentException("必须指定 knowledgeBaseId 或 knowledgeBaseIds");
        }

        List<RetrievalResultDTO> dtos = new ArrayList<>();
        for (RetrievalResult r : results) {
            dtos.add(RetrievalResultDTO.builder()
                    .paragraphId(r.getParagraphId())
                    .documentId(r.getDocumentId())
                    .knowledgeBaseId(r.getKnowledgeBaseId())
                    .content(r.getContent())
                    .vectorScore(r.getVectorScore())
                    .fullTextScore(r.getFullTextScore())
                    .finalScore(r.getFinalScore())
                    .metadata(r.getMetadata())
                    .documentName(r.getMetadata() != null ? (String) r.getMetadata().get("title") : null)
                    .build());
        }
        return dtos;
    }

    /**
     * 从知识库配置中解析检索模式
     */
    private String resolveSearchMode(RetrievalQueryDTO query) {
        String kbId = query.getKnowledgeBaseId();
        if (kbId == null && query.getKnowledgeBaseIds() != null && query.getKnowledgeBaseIds().size() == 1) {
            kbId = query.getKnowledgeBaseIds().get(0);
        }
        if (kbId == null) return "simple";
        try {
            KnowledgeBaseEntity kb = knowledgeBaseService.getById(kbId);
            if (kb != null && "two_stage".equalsIgnoreCase(kb.getSearchMode())) {
                return "two_stage";
            }
        } catch (Exception e) {
            log.warn("读取知识库检索模式失败，降级为 simple: {}", e.getMessage());
        }
        return "simple";
    }
}