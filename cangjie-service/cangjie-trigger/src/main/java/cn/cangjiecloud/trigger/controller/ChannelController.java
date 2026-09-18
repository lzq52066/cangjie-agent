package cn.cangjiecloud.trigger.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.trigger.api.dto.ChannelCreateDTO;
import cn.cangjiecloud.trigger.api.dto.ChannelUpdateDTO;
import cn.cangjiecloud.trigger.entity.ChannelEntity;
import cn.cangjiecloud.trigger.entity.ChannelMessageEntity;
import cn.cangjiecloud.trigger.service.IChannelMessageService;
import cn.cangjiecloud.trigger.service.IChannelService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 渠道管理端接口
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/channel")
public class ChannelController {

    private final IChannelService channelService;
    private final IChannelMessageService channelMessageService;

    @PostMapping
    public R<ChannelEntity> create(@Valid @RequestBody ChannelCreateDTO dto) {
        return R.data(channelService.create(dto));
    }

    @PutMapping("/{id}")
    public R<ChannelEntity> update(@PathVariable String id, @RequestBody ChannelUpdateDTO dto) {
        return R.data(channelService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        channelService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<ChannelEntity> get(@PathVariable String id) {
        return R.data(channelService.getById(id));
    }

    @GetMapping
    public R<PageResult<ChannelEntity>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(channelService.pageQuery(keyword, type, pageNum, pageSize)));
    }

    @PostMapping("/{id}/enable")
    public R<ChannelEntity> enable(@PathVariable String id) {
        return R.data(channelService.enable(id));
    }

    @PostMapping("/{id}/disable")
    public R<ChannelEntity> disable(@PathVariable String id) {
        return R.data(channelService.disable(id));
    }

    /**
     * 跨渠道消息全局审计：可按渠道 / 类型 / 应用 / 外部用户 / 处理状态 / 关键词筛选
     */
    @GetMapping("/messages")
    public R<PageResult<ChannelMessageEntity>> allMessages(@RequestParam(required = false) String channelId,
                                                           @RequestParam(required = false) String channelType,
                                                           @RequestParam(required = false) String applicationId,
                                                           @RequestParam(required = false) String openId,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChannelMessageEntity> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChannelMessageEntity>()
                        .eq(channelId != null && !channelId.isBlank(), ChannelMessageEntity::getChannelId, channelId)
                        .eq(channelType != null && !channelType.isBlank(), ChannelMessageEntity::getChannelType, channelType)
                        .eq(applicationId != null && !applicationId.isBlank(), ChannelMessageEntity::getApplicationId, applicationId)
                        .eq(openId != null && !openId.isBlank(), ChannelMessageEntity::getOpenId, openId)
                        .eq(status != null && !status.isBlank(), ChannelMessageEntity::getStatus, status)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(ChannelMessageEntity::getContent, keyword)
                                .or().like(ChannelMessageEntity::getReplyContent, keyword))
                        .orderByDesc(ChannelMessageEntity::getCreateTime);
        return R.data(PageResult.of(channelMessageService.page(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize), wrapper)));
    }

    @GetMapping("/{id}/messages")
    public R<PageResult<ChannelMessageEntity>> messages(@PathVariable String id,
                                                        @RequestParam(defaultValue = "1") Integer pageNum,
                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(channelMessageService.pageQuery(id, pageNum, pageSize)));
    }
}
