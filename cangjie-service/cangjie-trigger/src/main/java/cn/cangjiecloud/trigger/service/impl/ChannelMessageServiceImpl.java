package cn.cangjiecloud.trigger.service.impl;

import cn.cangjiecloud.trigger.entity.ChannelMessageEntity;
import cn.cangjiecloud.trigger.mapper.ChannelMessageMapper;
import cn.cangjiecloud.trigger.service.IChannelMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ChannelMessageServiceImpl extends ServiceImpl<ChannelMessageMapper, ChannelMessageEntity>
        implements IChannelMessageService {

    @Override
    public IPage<ChannelMessageEntity> pageQuery(String channelId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ChannelMessageEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(channelId)) {
            wrapper.eq(ChannelMessageEntity::getChannelId, channelId);
        }
        wrapper.orderByDesc(ChannelMessageEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }
}
