package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.knowledge.api.dto.RetrievalQueryDTO;
import cn.cangjiecloud.knowledge.api.dto.RetrievalResultDTO;
import cn.cangjiecloud.knowledge.service.IRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements IRetrievalService {

    private final HybridRetriever hybridRetriever;

    @Override
    public List<RetrievalResultDTO> retrieve(RetrievalQueryDTO query) {
        int topK = query.getTopK() != null ? query.getTopK() : 5;
        List<RetrievalResult> results;

        if (query.getKnowledgeBaseIds() != null && !query.getKnowledgeBaseIds().isEmpty()) {
            results = hybridRetriever.retrieve(query.getQuery(), query.getKnowledgeBaseIds(), topK);
        } else if (query.getKnowledgeBaseId() != null) {
            results = hybridRetriever.retrieve(query.getQuery(), query.getKnowledgeBaseId(), topK);
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
}
