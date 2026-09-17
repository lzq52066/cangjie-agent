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

    @GetMapping("/{id}/messages")
    public R<PageResult<ChannelMessageEntity>> messages(@PathVariable String id,
                                                        @RequestParam(defaultValue = "1") Integer pageNum,
                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(channelMessageService.pageQuery(id, pageNum, pageSize)));
    }
}
