package cn.cangjiecloud.trigger.service;

import cn.cangjiecloud.trigger.api.dto.ChannelCreateDTO;
import cn.cangjiecloud.trigger.api.dto.ChannelReplyDTO;
import cn.cangjiecloud.trigger.api.dto.ChannelUpdateDTO;
import cn.cangjiecloud.trigger.entity.ChannelEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IChannelService extends IService<ChannelEntity> {

    ChannelEntity create(ChannelCreateDTO dto);

    ChannelEntity update(String id, ChannelUpdateDTO dto);

    void delete(String id);

    IPage<ChannelEntity> pageQuery(String keyword, String type, Integer pageNum, Integer pageSize);

    /**
     * 查询某类型所有启用渠道
     */
    List<ChannelEntity> getByType(String type);

    /**
     * 启用渠道
     */
    ChannelEntity enable(String id);

    /**
     * 停用渠道
     */
    ChannelEntity disable(String id);

    /**
     * 处理渠道消息：转发到内部对话服务并记录消息
     *
     * @return 回复内容（event 等无回复场景 message 为 null）
     */
    ChannelReplyDTO handleMessage(ChannelReplyDTO dto);
}
