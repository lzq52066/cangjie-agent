package cn.cangjiecloud.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.knowledge.api.dto.ProblemCreateDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeProblemEntity;
import cn.cangjiecloud.knowledge.service.IKnowledgeProblemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库问题管理（常见问题 + 段落关联，支撑问题路召回）
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/knowledge/problem")
public class KnowledgeProblemController {

    private final IKnowledgeProblemService problemService;

    @GetMapping("/list/{knowledgeBaseId}")
    public R<List<KnowledgeProblemEntity>> list(@PathVariable String knowledgeBaseId) {
        return R.data(problemService.listByKnowledgeBase(knowledgeBaseId));
    }

    @PostMapping("/{knowledgeBaseId}")
    public R<KnowledgeProblemEntity> create(@PathVariable String knowledgeBaseId,
                                            @Valid @RequestBody ProblemCreateDTO dto) {
        return R.data(problemService.create(knowledgeBaseId, dto));
    }

    @PutMapping("/{problemId}")
    public R<KnowledgeProblemEntity> update(@PathVariable String problemId,
                                            @RequestBody ProblemCreateDTO dto) {
        return R.data(problemService.update(problemId, dto));
    }

    @DeleteMapping("/{problemId}")
    public R<Void> delete(@PathVariable String problemId) {
        problemService.delete(problemId);
        return R.ok();
    }

    /**
     * 补充关联段落（body: {"paragraphIds": ["..."]}）
     */
    @PostMapping("/{problemId}/associate")
    @SuppressWarnings("unchecked")
    public R<Void> associate(@PathVariable String problemId,
                             @RequestBody Map<String, Object> body) {
        List<String> paragraphIds = body.get("paragraphIds") instanceof List
                ? ((List<Object>) body.get("paragraphIds")).stream().map(String::valueOf).toList()
                : List.of();
        problemService.associate(problemId, paragraphIds);
        return R.ok();
    }

    @GetMapping("/{problemId}/paragraphs")
    public R<List<String>> paragraphs(@PathVariable String problemId) {
        return R.data(problemService.listParagraphIds(problemId));
    }
}
