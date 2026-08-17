package cn.cangjiecloud.trigger.service;

import cn.cangjiecloud.trigger.entity.ChannelMessageEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IChannelMessageService extends IService<ChannelMessageEntity> {

    /**
     * 查询渠道消息记录
     */
    List<ChannelMessageEntity> listByChannel(String channelId);
}
