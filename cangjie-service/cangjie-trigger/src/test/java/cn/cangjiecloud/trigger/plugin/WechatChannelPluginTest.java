package cn.cangjiecloud.trigger.plugin;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.plugin.PluginContext;
import cn.cangjiecloud.trigger.api.dto.ChannelReplyDTO;
import cn.cangjiecloud.trigger.service.IChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link WechatChannelPlugin} 单元测试：插件元信息、参数组装与转发、异常分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("渠道插件 - WechatChannelPlugin")
class WechatChannelPluginTest {

    @Mock
    private IChannelService channelService;

    private WechatChannelPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new WechatChannelPlugin(channelService);
    }

    private static PluginContext context(Map<String, Object> params) {
        return PluginContext.builder().params(params).build();
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private ChannelReplyDTO captureForwarded() {
        ArgumentCaptor<ChannelReplyDTO> captor = ArgumentCaptor.forClass(ChannelReplyDTO.class);
        verify(channelService).handleMessage(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("插件元信息：name/description/type")
    void shouldExposeMetadata() {
        assertThat(plugin.getName()).isEqualTo("wechat");
        assertThat(plugin.getType()).isEqualTo("channel");
        assertThat(plugin.getDescription()).isNotBlank();
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("正常转发：参数组装为 ChannelReplyDTO 并返回渠道回复内容")
        void shouldForwardMessageAndReturnReply() {
            ChannelReplyDTO reply = new ChannelReplyDTO();
            reply.setMessage("微信回复");
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(reply);

            Object result = plugin.execute(context(params(
                    "channelId", "c1", "message", "你好", "openId", "open-1")));

            assertThat(result).isEqualTo("微信回复");

            ChannelReplyDTO forwarded = captureForwarded();
            assertThat(forwarded.getChannelId()).isEqualTo("c1");
            assertThat(forwarded.getMessage()).isEqualTo("你好");
            assertThat(forwarded.getOpenId()).isEqualTo("open-1");
            assertThat(forwarded.getMsgType()).isEqualTo("text");
        }

        @Test
        @DisplayName("渠道服务返回 null 或回复内容为空时返回 null")
        void shouldReturnNullWhenNoReply() {
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(null);

            assertThat(plugin.execute(context(params(
                    "channelId", "c1", "message", "你好", "openId", "open-1")))).isNull();
        }

        @Test
        @DisplayName("回复对象存在但 message 为空串时原样返回空串")
        void shouldReturnBlankMessageAsIs() {
            ChannelReplyDTO reply = new ChannelReplyDTO();
            reply.setMessage("");
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(reply);

            Object result = plugin.execute(context(params(
                    "channelId", "c1", "message", "你好", "openId", "open-1")));
            assertThat(result).isInstanceOf(String.class);
            assertThat((String) result).isEmpty();
        }

        @Test
        @DisplayName("渠道服务异常原样向上抛出")
        void shouldPropagateServiceException() {
            when(channelService.handleMessage(any(ChannelReplyDTO.class)))
                    .thenThrow(new ApiException("渠道未启用: 测试渠道"));

            assertThatThrownBy(() -> plugin.execute(context(params(
                    "channelId", "c1", "message", "你好", "openId", "open-1"))))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("渠道未启用");
        }

        @Test
        @DisplayName("openId 为空串时抛出 ApiException")
        void shouldRejectBlankOpenId() {
            assertThatThrownBy(() -> plugin.execute(context(params(
                    "channelId", "c1", "message", "你好", "openId", ""))))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("缺少外部用户 ID");

            verifyNoInteractions(channelService);
        }

        @Test
        @DisplayName("params 为 null 时不抛 NPE，按字面量 null 转发（现状锁定）")
        void shouldTolerateNullParams() {
            // 注意：String.valueOf(null) 得到字符串 "null"，因此缺少 openId 时参数校验不会触发，
            // 会以字面量 "null" 继续转发（疑似缺陷），此处锁定当前行为防止无意变更
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(null);

            assertThat(plugin.execute(context(null))).isNull();

            ChannelReplyDTO forwarded = captureForwarded();
            assertThat(forwarded.getChannelId()).isEqualTo("null");
            assertThat(forwarded.getMessage()).isEqualTo("null");
            assertThat(forwarded.getOpenId()).isEqualTo("null");
        }
    }
}
