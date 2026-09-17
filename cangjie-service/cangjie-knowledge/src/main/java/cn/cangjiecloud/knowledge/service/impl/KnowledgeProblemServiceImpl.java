package cn.cangjiecloud.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.knowledge.api.dto.ProblemCreateDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeProblemEntity;
import cn.cangjiecloud.knowledge.entity.ProblemParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeProblemMapper;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import cn.cangjiecloud.knowledge.service.IKnowledgeProblemService;
import cn.cangjiecloud.knowledge.service.IProblemParagraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeProblemServiceImpl
        extends ServiceImpl<KnowledgeProblemMapper, KnowledgeProblemEntity>
        implements IKnowledgeProblemService {

    private final IKnowledgeBaseService knowledgeBaseService;
    private final IProblemParagraphService problemParagraphService;

    @Override
    public IPage<KnowledgeProblemEntity> pageQuery(String knowledgeBaseId, Integer pageNum, Integer pageSize) {
        knowledgeBaseService.checkAccess(knowledgeBaseId);
        LambdaQueryWrapper<KnowledgeProblemEntity> wrapper = new LambdaQueryWrapper<KnowledgeProblemEntity>()
                .eq(KnowledgeProblemEntity::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeProblemEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeProblemEntity create(String knowledgeBaseId, ProblemCreateDTO dto) {
        knowledgeBaseService.checkAccess(knowledgeBaseId);

        KnowledgeProblemEntity problem = new KnowledgeProblemEntity();
        problem.setKnowledgeBaseId(knowledgeBaseId);
        problem.setContent(dto.getContent().trim());
        problem.setSource("manual");
        save(problem);

        associate(problem, knowledgeBaseId, dto.getParagraphIds());
        log.info("知识库问题已创建: {} -> {}", knowledgeBaseId, problem.getContent());
        return problem;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeProblemEntity update(String problemId, ProblemCreateDTO dto) {
        KnowledgeProblemEntity problem = requireProblem(problemId);
        if (StringUtils.hasText(dto.getContent())) {
            problem.setContent(dto.getContent().trim());
        }
        updateById(problem);
        if (dto.getParagraphIds() != null) {
            // 全量替换关联
            problemParagraphService.remove(new LambdaQueryWrapper<ProblemParagraphEntity>()
                    .eq(ProblemParagraphEntity::getProblemId, problemId));
            associate(problem, problem.getKnowledgeBaseId(), dto.getParagraphIds());
        }
        return problem;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String problemId) {
        KnowledgeProblemEntity problem = requireProblem(problemId);
        problemParagraphService.remove(new LambdaQueryWrapper<ProblemParagraphEntity>()
                .eq(ProblemParagraphEntity::getProblemId, problemId));
        removeById(problemId);
        log.info("知识库问题已删除: {}", problem.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void associate(String problemId, List<String> paragraphIds) {
        KnowledgeProblemEntity problem = requireProblem(problemId);
        associate(problem, problem.getKnowledgeBaseId(), paragraphIds);
    }

    @Override
    public IPage<String> pageQueryParagraphIds(String problemId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ProblemParagraphEntity> wrapper = new LambdaQueryWrapper<ProblemParagraphEntity>()
                .eq(ProblemParagraphEntity::getProblemId, problemId)
                // 分页需要稳定排序，按主键升序
                .orderByAsc(ProblemParagraphEntity::getId);
        IPage<ProblemParagraphEntity> relationPage = problemParagraphService.page(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
        return relationPage.convert(ProblemParagraphEntity::getParagraphId);
    }

    @Override
    public Map<String, String> recallParagraphs(List<String> knowledgeBaseIds, String query, int limit) {
        Map<String, String> result = new LinkedHashMap<>();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || !StringUtils.hasText(query)) {
            return result;
        }
        // 1. 按问题内容模糊匹配（单库场景居多，控制扫描范围）
        List<KnowledgeProblemEntity> problems = list(new LambdaQueryWrapper<KnowledgeProblemEntity>()
                .in(KnowledgeProblemEntity::getKnowledgeBaseId, knowledgeBaseIds)
                .like(KnowledgeProblemEntity::getContent, query.trim())
                .last("LIMIT 20"));
        if (problems.isEmpty()) {
            return result;
        }
        // 2. 取关联段落
        List<String> problemIds = problems.stream().map(KnowledgeProblemEntity::getId).toList();
        Map<String, String> problemContentById = new LinkedHashMap<>();
        problems.forEach(p -> problemContentById.put(p.getId(), p.getContent()));

        List<ProblemParagraphEntity> relations = problemParagraphService.list(
                new LambdaQueryWrapper<ProblemParagraphEntity>()
                        .in(ProblemParagraphEntity::getProblemId, problemIds));
        for (ProblemParagraphEntity relation : relations) {
            if (result.size() >= limit) {
                break;
            }
            result.putIfAbsent(relation.getParagraphId(),
                    problemContentById.get(relation.getProblemId()));
        }
        if (!result.isEmpty()) {
            log.info("问题路召回命中: query={}, 段落数={}", query, result.size());
        }
        return result;
    }

    private void associate(KnowledgeProblemEntity problem, String knowledgeBaseId,
                           List<String> paragraphIds) {
        if (paragraphIds == null || paragraphIds.isEmpty()) {
            return;
        }
        List<ProblemParagraphEntity> relations = new ArrayList<>();
        for (String paragraphId : paragraphIds) {
            if (!StringUtils.hasText(paragraphId)) {
                continue;
            }
            ProblemParagraphEntity relation = new ProblemParagraphEntity();
            relation.setProblemId(problem.getId());
            relation.setParagraphId(paragraphId);
            relation.setKnowledgeBaseId(knowledgeBaseId);
            relations.add(relation);
        }
        if (!relations.isEmpty()) {
            problemParagraphService.saveBatch(relations);
        }
    }

    private KnowledgeProblemEntity requireProblem(String problemId) {
        KnowledgeProblemEntity problem = getById(problemId);
        if (problem == null) {
            throw new ApiException("问题不存在");
        }
        knowledgeBaseService.checkAccess(problem.getKnowledgeBaseId());
        return problem;
    }
}
