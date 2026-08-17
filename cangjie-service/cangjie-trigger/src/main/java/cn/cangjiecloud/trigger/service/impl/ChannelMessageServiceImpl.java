package cn.cangjiecloud.trigger.service.impl;

import cn.cangjiecloud.trigger.entity.ChannelMessageEntity;
import cn.cangjiecloud.trigger.mapper.ChannelMessageMapper;
import cn.cangjiecloud.trigger.service.IChannelMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class ChannelMessageServiceImpl extends ServiceImpl<ChannelMessageMapper, ChannelMessageEntity>
        implements IChannelMessageService {

    @Override
    public List<ChannelMessageEntity> listByChannel(String channelId) {
        LambdaQueryWrapper<ChannelMessageEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(channelId)) {
            wrapper.eq(ChannelMessageEntity::getChannelId, channelId);
        }
        wrapper.orderByDesc(ChannelMessageEntity::getCreateTime);
        return list(wrapper);
    }
}
