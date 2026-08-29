package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.QueryRewriterFactory;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.knowledge.api.dto.RetrievalQueryDTO;
import cn.cangjiecloud.knowledge.api.dto.RetrievalResultDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.rag.PgVectorStore;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import cn.cangjiecloud.knowledge.service.IKnowledgeProblemService;
import cn.cangjiecloud.knowledge.service.IRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements IRetrievalService {

    private final HybridRetriever hybridRetriever;
    private final QueryRewriterFactory queryRewriterFactory;
    private final IKnowledgeBaseService knowledgeBaseService;
    private final IKnowledgeProblemService knowledgeProblemService;
    private final PgVectorStore pgVectorStore;

    @Value("${cangjie.rag.query-rewrite.type:none}")
    private String queryRewriteType;

    @Override
    public List<RetrievalResultDTO> retrieve(RetrievalQueryDTO query) {
        // 数据权限：校验当前用户对所有目标知识库的访问权限
        if (query.getKnowledgeBaseIds() != null) {
            query.getKnowledgeBaseIds().forEach(knowledgeBaseService::checkAccess);
        }
        if (query.getKnowledgeBaseId() != null) {
            knowledgeBaseService.checkAccess(query.getKnowledgeBaseId());
        }

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
        List<String> kbIds = resolveKbIds(query);
        if (kbIds.size() > 1) {
            results = hybridRetriever.retrieve(retrievalQuery, kbIds, topK, similarityThreshold, searchMode);
        } else if (kbIds.size() == 1) {
            results = hybridRetriever.retrieve(retrievalQuery, kbIds.get(0), topK, similarityThreshold);
        } else {
            throw new IllegalArgumentException("必须指定 knowledgeBaseId 或 knowledgeBaseIds");
        }

        List<RetrievalResultDTO> dtos = new ArrayList<>();
        Set<String> seenParagraphIds = new HashSet<>();
        for (RetrievalResult r : results) {
            seenParagraphIds.add(r.getParagraphId());
            dtos.add(toDto(r, null));
        }

        // 问题路召回：匹配常见问题 → 取关联段落 → 去重合并
        if (!Boolean.FALSE.equals(query.getEnableProblem())) {
            mergeProblemRecall(kbIds, query.getQuery(), topK, dtos, seenParagraphIds);
        }
        return dtos;
    }

    /**
     * 问题路召回合并：常见问题精确/模糊命中的段落优先补充进结果
     */
    private void mergeProblemRecall(List<String> kbIds, String queryText, int topK,
                                    List<RetrievalResultDTO> dtos, Set<String> seenParagraphIds) {
        try {
            Map<String, String> paragraphToProblem =
                    knowledgeProblemService.recallParagraphs(kbIds, queryText, topK);
            if (paragraphToProblem.isEmpty()) {
                return;
            }
            List<String> missing = paragraphToProblem.keySet().stream()
                    .filter(id -> !seenParagraphIds.contains(id))
                    .toList();
            // 已命中的段落补上问题标记
            for (RetrievalResultDTO dto : dtos) {
                String problem = paragraphToProblem.get(dto.getParagraphId());
                if (problem != null) {
                    dto.setMatchedProblem(problem);
                }
            }
            if (missing.isEmpty()) {
                return;
            }
            List<RetrievalResult> problemResults = pgVectorStore.getByParagraphIds(missing, missing.size());
            for (RetrievalResult r : problemResults) {
                seenParagraphIds.add(r.getParagraphId());
                // 问题命中视为高置信结果
                r.setFinalScore(1.0);
                dtos.add(toDto(r, paragraphToProblem.get(r.getParagraphId())));
            }
        } catch (Exception e) {
            log.warn("问题路召回失败（不影响主检索）: {}", e.getMessage());
        }
    }

    private RetrievalResultDTO toDto(RetrievalResult r, String matchedProblem) {
        return RetrievalResultDTO.builder()
                .paragraphId(r.getParagraphId())
                .documentId(r.getDocumentId())
                .knowledgeBaseId(r.getKnowledgeBaseId())
                .content(r.getContent())
                .vectorScore(r.getVectorScore())
                .fullTextScore(r.getFullTextScore())
                .finalScore(r.getFinalScore())
                .metadata(r.getMetadata())
                .documentName(r.getMetadata() != null ? (String) r.getMetadata().get("title") : null)
                .matchedProblem(matchedProblem)
                .build();
    }

    private List<String> resolveKbIds(RetrievalQueryDTO query) {
        if (query.getKnowledgeBaseIds() != null && !query.getKnowledgeBaseIds().isEmpty()) {
            return query.getKnowledgeBaseIds();
        }
        if (query.getKnowledgeBaseId() != null) {
            return List.of(query.getKnowledgeBaseId());
        }
        return List.of();
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