package cn.cangjiecloud.trigger.plugin;

import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.chat.service.IChatService;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginContext;
import cn.cangjiecloud.trigger.entity.ChannelEntity;
import cn.cangjiecloud.trigger.service.IChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * 微信渠道插件：接收渠道消息并转发到对话服务
 */
@Component
@RequiredArgsConstructor
public class WechatChannelPlugin implements Plugin {

    private final IChannelService channelService;
    private final IChatService chatService;

    @Override
    public String getName() {
        return "wechat";
    }

    @Override
    public String getDescription() {
        return "微信渠道插件：将外部平台消息转发到内部对话服务";
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
        ChannelEntity channel = channelService.getById(channelId);
        if (channel == null) {
            throw new ApiException("渠道不存在: " + channelId);
        }
        if (!StringUtils.hasText(openId)) {
            throw new ApiException("缺少外部用户 ID（openId）");
        }
        ChatRequestDTO request = new ChatRequestDTO();
        request.setApplicationId(channel.getApplicationId());
        request.setMessage(message);
        request.setSource(channel.getType());
        request.setSessionId("ch_" + channel.getId() + "_" + openId);
        ChatResponseDTO response = chatService.chat(request);
        return response != null ? response.getMessage() : null;
    }
}
