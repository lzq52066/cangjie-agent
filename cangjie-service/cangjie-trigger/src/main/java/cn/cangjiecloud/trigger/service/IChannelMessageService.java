package cn.cangjiecloud.trigger.service;

import cn.cangjiecloud.trigger.entity.ChannelMessageEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IChannelMessageService extends IService<ChannelMessageEntity> {

    /**
     * 分页查询渠道消息记录（按创建时间倒序）
     */
    IPage<ChannelMessageEntity> pageQuery(String channelId, Integer pageNum, Integer pageSize);
}
