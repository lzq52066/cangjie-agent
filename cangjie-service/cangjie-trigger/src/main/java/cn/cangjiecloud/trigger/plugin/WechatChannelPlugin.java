package cn.cangjiecloud.trigger.plugin;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginContext;
import cn.cangjiecloud.trigger.api.dto.ChannelReplyDTO;
import cn.cangjiecloud.trigger.service.IChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * 微信渠道插件：接收渠道消息并转发到内部对话服务或工作流引擎
 */
@Component
@RequiredArgsConstructor
public class WechatChannelPlugin implements Plugin {

    private final IChannelService channelService;

    @Override
    public String getName() {
        return "wechat";
    }

    @Override
    public String getDescription() {
        return "微信渠道插件：将外部平台消息转发到内部对话服务或工作流引擎";
    }

    @Override
    public String getType() {
        return "channel";
    }

    @Override
    public Object execute(PluginContext context) {
        Map<String, Object> params = context.getParams() != null ? context.getParams() : Collections.emptyMap();
        String channelId = String.valueOf(params.get("channelId"));
        String message = String.valueOf(params.get("message"));
        String openId = String.valueOf(params.get("openId"));
        if (!StringUtils.hasText(openId)) {
            throw new ApiException("缺少外部用户 ID（openId）");
        }

        ChannelReplyDTO dto = new ChannelReplyDTO();
        dto.setChannelId(channelId);
        dto.setMessage(message);
        dto.setOpenId(openId);
        dto.setMsgType("text");

        ChannelReplyDTO reply = channelService.handleMessage(dto);
        return reply != null ? reply.getMessage() : null;
    }
}
