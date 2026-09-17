package cn.cangjiecloud.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseCreateDTO;
import cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseUpdateDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/knowledge/base")
public class KnowledgeBaseController {

    private final IKnowledgeBaseService knowledgeBaseService;

    @PostMapping
    public R<KnowledgeBaseEntity> create(@Valid @RequestBody KnowledgeBaseCreateDTO dto) {
        return R.data(knowledgeBaseService.create(dto));
    }

    @PutMapping("/{id}")
    public R<KnowledgeBaseEntity> update(@PathVariable String id, @RequestBody KnowledgeBaseUpdateDTO dto) {
        return R.data(knowledgeBaseService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        knowledgeBaseService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<KnowledgeBaseEntity> get(@PathVariable String id) {
        return R.data(knowledgeBaseService.getById(id));
    }

    @GetMapping
    public R<PageResult<KnowledgeBaseEntity>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(knowledgeBaseService.pageQuery(keyword, pageNum, pageSize)));
    }
}
