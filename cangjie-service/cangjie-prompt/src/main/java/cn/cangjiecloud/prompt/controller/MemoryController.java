package cn.cangjiecloud.prompt.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.memory.MemoryScorer;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆管理：查看/手动录入/编辑/停用/激活/删除用户画像与场景记忆
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/memory")
public class MemoryController {

    private final ILongTermMemoryService longTermMemoryService;
    private final MemoryScorer memoryScorer;

    /**
     * 参与评分排序的候选集上限：评分为内存计算值无法下推 SQL，
     * 只能先取候选集整体排序再分页，超出该窗口的记忆不参与排序。
     */
    private static final int SCORE_WINDOW = 1000;

    /**
     * 记忆列表（按强度评分降序），附评分信息。
     * 排序在候选集（最多 {@value #SCORE_WINDOW} 条）上整体完成后才分页，保证跨页顺序一致。
     */
    @GetMapping
    public R<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String applicationId,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String memoryType,
            @RequestParam(required = false) Boolean includeInactive,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        boolean admin = isAdmin();
        if (StringUtils.hasText(userId)) {
            requireOwnerOrAdmin(userId);
        } else if (!admin) {
            // 非管理员不传 userId 时，强制限定为本人记忆
            userId = UserContext.getUserId();
            if (!StringUtils.hasText(userId)) {
                throw new ApiException("userId 不能为空");
            }
        }
        LambdaQueryWrapper<LongTermMemoryEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(userId)) {
            // 管理员传了 userId 则只查该用户，不传则查全部
            wrapper.eq(LongTermMemoryEntity::getUserId, userId);
        }
        if (StringUtils.hasText(applicationId)) {
            wrapper.eq(LongTermMemoryEntity::getApplicationId, applicationId);
        }
        if (StringUtils.hasText(dimension)) {
            wrapper.eq(LongTermMemoryEntity::getDimension, dimension);
        }
        if (StringUtils.hasText(memoryType)) {
            wrapper.eq(LongTermMemoryEntity::getMemoryType, memoryType);
        }
        if (!Boolean.TRUE.equals(includeInactive)) {
            wrapper.eq(LongTermMemoryEntity::getIsActive, true);
        }

        long current = Math.max(1, pageNum == null ? 1 : pageNum);
        long size = Math.max(1, pageSize == null ? 10 : pageSize);
        IPage<LongTermMemoryEntity> candidates = longTermMemoryService.page(
                new Page<>(1, SCORE_WINDOW), wrapper);
        List<Map<String, Object>> ranked = candidates.getRecords().stream()
                .sorted(Comparator.comparingDouble(memoryScorer::score).reversed())
                .map(this::withScore)
                .toList();
        int from = (int) Math.min((current - 1) * size, ranked.size());
        int to = (int) Math.min(from + size, ranked.size());
        return R.data(new PageResult<>(ranked.subList(from, to),
                Math.min(candidates.getTotal(), SCORE_WINDOW), current, size));
    }

    /**
     * 手动录入记忆（source=explicit，不参与自动遗忘）
     */
    @PostMapping
    public R<LongTermMemoryEntity> create(@RequestBody LongTermMemoryEntity entity) {
        if (!StringUtils.hasText(entity.getUserId()) || !StringUtils.hasText(entity.getContent())) {
            throw new ApiException("userId 与 content 不能为空");
        }
        requireOwnerOrAdmin(entity.getUserId());
        if (!StringUtils.hasText(entity.getDimension())) {
            entity.setDimension("preference");
        }
        if (!StringUtils.hasText(entity.getApplicationId())) {
            entity.setApplicationId("global");
        }
        entity.setSource("explicit");
        entity.setConfidence(entity.getConfidence() != null ? entity.getConfidence() : 1.0);
        entity.setIsActive(true);
        entity.setTriggerCount(0);
        entity.setLastTriggeredAt(LocalDateTime.now());
        if (!StringUtils.hasText(entity.getMemoryType())) {
            entity.setMemoryType("user");
        }
        return R.data(longTermMemoryService.upsert(entity));
    }

    /**
     * 编辑记忆内容/置信度/维度（内容变更时同步重算向量）
     */
    @PutMapping("/{id}")
    public R<LongTermMemoryEntity> update(@PathVariable String id, @RequestBody LongTermMemoryEntity patch) {
        LongTermMemoryEntity entity = requireOwnedMemory(id);
        longTermMemoryService.editMemory(id, patch.getContent(), patch.getConfidence(), patch.getDimension());
        return R.data(longTermMemoryService.getById(id));
    }

    /**
     * 停用（软遗忘，可恢复）
     */
    @PostMapping("/{id}/deactivate")
    public R<Void> deactivate(@PathVariable String id) {
        requireOwnedMemory(id);
        longTermMemoryService.deactivate(id);
        return R.ok();
    }

    /**
     * 重新激活
     */
    @PostMapping("/{id}/reactivate")
    public R<Void> reactivate(@PathVariable String id) {
        requireOwnedMemory(id);
        longTermMemoryService.reactivate(id);
        return R.ok();
    }

    /**
     * 彻底删除
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        requireOwnedMemory(id);
        longTermMemoryService.deleteMemory(id);
        return R.ok();
    }

    /**
     * 数据权限：仅管理员可跨用户操作，否则只能操作本人记忆
     */
    private void requireOwnerOrAdmin(String targetUserId) {
        if (isAdmin()) {
            return;
        }
        String currentUserId = UserContext.getUserId();
        if (!StringUtils.hasText(currentUserId) || !currentUserId.equals(targetUserId)) {
            throw new ApiException("无权操作其他用户的记忆");
        }
    }

    private LongTermMemoryEntity requireOwnedMemory(String id) {
        LongTermMemoryEntity entity = longTermMemoryService.getById(id);
        if (entity == null) {
            throw new ApiException("记忆不存在");
        }
        requireOwnerOrAdmin(entity.getUserId());
        return entity;
    }

    private boolean isAdmin() {
        UserIdentity identity = UserContext.getIdentity();
        return identity != null && AppConst.ROLE_ADMIN.equalsIgnoreCase(identity.getRole());
    }

    private Map<String, Object> withScore(LongTermMemoryEntity memory) {
        Map<String, Object> item = new HashMap<>();
        item.put("memory", memory);
        item.put("score", Math.round(memoryScorer.score(memory) * 1000) / 1000.0);
        return item;
    }
}
