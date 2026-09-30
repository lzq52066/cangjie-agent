package cn.cangjiecloud.trigger.controller;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.trigger.api.dto.ChannelReplyDTO;
import cn.cangjiecloud.trigger.entity.ChannelEntity;
import cn.cangjiecloud.trigger.service.IChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ChannelCallbackController} 单元测试：URL 验证、按渠道类型分发、
 * 微信 XML 解析与被动回复、钉钉/飞书 JSON 事件解析以及各异常分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("渠道回调接口 - ChannelCallbackController")
class ChannelCallbackControllerTest {

    @Mock
    private IChannelService channelService;

    private ChannelCallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new ChannelCallbackController(channelService);
    }

    private static ChannelEntity channel(String id, String type) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(id);
        channel.setName("渠道-" + id);
        channel.setType(type);
        channel.setStatus("active");
        channel.setToken("real-token");
        return channel;
    }

    private static String xml(String fromUser, String toUser, String msgType, String content) {
        return "<xml>"
                + "<ToUserName><![CDATA[" + toUser + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + fromUser + "]]></FromUserName>"
                + "<CreateTime>1700000000</CreateTime>"
                + "<MsgType><![CDATA[" + msgType + "]]></MsgType>"
                + "<Content><![CDATA[" + content + "]]></Content>"
                + "</xml>";
    }

    private static ChannelReplyDTO replyWith(String message) {
        ChannelReplyDTO reply = new ChannelReplyDTO();
        reply.setMessage(message);
        return reply;
    }

    private ChannelReplyDTO captureForwardedMessage() {
        ArgumentCaptor<ChannelReplyDTO> captor = ArgumentCaptor.forClass(ChannelReplyDTO.class);
        verify(channelService).handleMessage(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------
    // GET 微信 URL 验证
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("GET verify - 微信 URL 验证")
    class Verify {

        @Test
        @DisplayName("token 匹配时原样返回 echostr")
        void shouldReturnEchoStrWhenTokenMatches() {
            when(channelService.getByType("wechat")).thenReturn(List.of(channel("c1", "wechat")));

            String result = controller.verify("wechat", "sig", "1700000000", "nonce", "real-token", "echo-123");

            assertThat(result).isEqualTo("echo-123");
        }

        @Test
        @DisplayName("token 不匹配时返回 fail 且不返回 echostr")
        void shouldReturnFailWhenTokenMismatch() {
            when(channelService.getByType("wechat")).thenReturn(List.of(channel("c1", "wechat")));

            String result = controller.verify("wechat", "sig", "1700000000", "nonce", "wrong-token", "echo-123");

            assertThat(result).isEqualTo("fail");
        }

        @Test
        @DisplayName("未传 token 时跳过校验（现状：简化实现不做签名验证）")
        void shouldSkipTokenCheckWhenRequestTokenBlank() {
            when(channelService.getByType("wechat")).thenReturn(List.of(channel("c1", "wechat")));

            assertThat(controller.verify("wechat", null, null, null, null, "echo-123"))
                    .isEqualTo("echo-123");
        }

        @Test
        @DisplayName("无 echostr 时返回 success")
        void shouldReturnSuccessWhenEchoStrMissing() {
            when(channelService.getByType("wechat")).thenReturn(List.of(channel("c1", "wechat")));

            assertThat(controller.verify("wechat", null, null, null, "real-token", null))
                    .isEqualTo("success");
        }

        @Test
        @DisplayName("该类型下没有启用渠道时抛 ApiException")
        void shouldThrowWhenNoActiveChannel() {
            when(channelService.getByType("wechat")).thenReturn(List.of());

            assertThatThrownBy(() -> controller.verify("wechat", null, null, null, null, "echo-123"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("没有启用的渠道");
        }
    }

    // ------------------------------------------------------------------
    // POST 分发与微信回调
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("POST callback - 微信分支")
    class WechatCallback {

        @Test
        @DisplayName("不支持的渠道类型直接抛 ApiException")
        void shouldRejectUnsupportedChannelType() {
            assertThatThrownBy(() -> controller.callback("slack", null, null, null, null, "<xml/>"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("不支持的渠道类型: slack");

            verifyNoInteractions(channelService);
        }

        @Test
        @DisplayName("解析 XML 并返回 CDATA 被动回复，from/to 正确交换")
        void shouldParseXmlAndBuildCdataReply() {
            when(channelService.getByType("wechat")).thenReturn(List.of(channel("c1", "wechat")));
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(replyWith("收到啦"));

            String response = controller.callback("wechat", null, null, null, null,
                    xml("user-open-1", "gh_official", "text", "你好"));

            assertThat(response)
                    .contains("<ToUserName><![CDATA[user-open-1]]></ToUserName>")
                    .contains("<FromUserName><![CDATA[gh_official]]></FromUserName>")
                    .contains("<MsgType><![CDATA[text]]></MsgType>")
                    .contains("<Content><![CDATA[收到啦]]></Content>")
                    .containsPattern("<CreateTime>\\d+</CreateTime>");

            ChannelReplyDTO forwarded = captureForwardedMessage();
            assertThat(forwarded.getChannelId()).isEqualTo("c1");
            assertThat(forwarded.getOpenId()).isEqualTo("user-open-1");
            assertThat(forwarded.getMsgType()).isEqualTo("text");
            assertThat(forwarded.getMessage()).isEqualTo("你好");
            assertThat(forwarded.getEventType()).isNull();
        }

        @Test
        @DisplayName("wechat_work 等别名同样走微信分支")
        void shouldRouteWechatAliases() {
            when(channelService.getByType("wechat_work")).thenReturn(List.of(channel("c2", "wechat_work")));
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(replyWith("好的"));

            String response = controller.callback("wechat_work", null, null, null, null,
                    xml("user-1", "corp-1", "text", "在吗"));

            assertThat(response).contains("<Content><![CDATA[好的]]></Content>");
            assertThat(captureForwardedMessage().getChannelId()).isEqualTo("c2");
        }

        @Test
        @DisplayName("空报文直接返回空串，不查询渠道")
        void shouldReturnEmptyOnBlankBody() {
            assertThat(controller.callback("wechat", null, null, null, null, "  ")).isEmpty();

            verifyNoInteractions(channelService);
        }

        @Test
        @DisplayName("渠道无回复（事件消息）时返回空串表示不被动回复")
        void shouldReturnEmptyWhenNoReply() {
            when(channelService.getByType("wechat")).thenReturn(List.of(channel("c1", "wechat")));
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(replyWith(null));

            String response = controller.callback("wechat", null, null, null, null,
                    xml("user-1", "gh_1", "event", ""));

            assertThat(response).isEmpty();
        }

        @Test
        @DisplayName("非 CDATA 标签可解析并去除首尾空白，事件类型被透传")
        void shouldParsePlainTagsAndEventType() {
            when(channelService.getByType("wechat_mp")).thenReturn(List.of(channel("c3", "wechat_mp")));
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(replyWith("欢迎关注"));

            String body = "<xml><ToUserName>gh_1</ToUserName><FromUserName> user-9 </FromUserName>"
                    + "<MsgType> event </MsgType><Event> subscribe </Event></xml>";
            String response = controller.callback("wechat_mp", null, null, null, null, body);

            assertThat(response).contains("<Content><![CDATA[欢迎关注]]></Content>");
            ChannelReplyDTO forwarded = captureForwardedMessage();
            assertThat(forwarded.getOpenId()).isEqualTo("user-9");
            assertThat(forwarded.getMsgType()).isEqualTo("event");
            assertThat(forwarded.getEventType()).isEqualTo("subscribe");
            assertThat(forwarded.getMessage()).isNull();
        }

        @Test
        @DisplayName("缺少标签时相关字段为 null，回复仍按空串处理")
        void shouldTolerateMissingTags() {
            when(channelService.getByType("wechat")).thenReturn(List.of(channel("c1", "wechat")));
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(replyWith(null));

            String response = controller.callback("wechat", null, null, null, null,
                    "<xml><FromUserName><![CDATA[only-user]]></FromUserName></xml>");

            assertThat(response).isEmpty();
            ChannelReplyDTO forwarded = captureForwardedMessage();
            assertThat(forwarded.getOpenId()).isEqualTo("only-user");
            assertThat(forwarded.getMessage()).isNull();
            assertThat(forwarded.getMsgType()).isNull();
            assertThat(forwarded.getEventType()).isNull();
        }
    }

    // ------------------------------------------------------------------
    // POST 钉钉回调
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("POST callback - 钉钉分支")
    class DingtalkCallback {

        @Test
        @DisplayName("token 不匹配时返回 fail 且不转发消息")
        void shouldRejectTokenMismatch() {
            when(channelService.getByType("dingtalk")).thenReturn(List.of(channel("c5", "dingtalk")));

            String result = controller.callback("dingtalk", null, null, null, "wrong-token",
                    "{\"msgtype\":\"text\"}");

            assertThat(result).isEqualTo("fail");
            verify(channelService, never()).handleMessage(any(ChannelReplyDTO.class));
        }

        @Test
        @DisplayName("空报文返回 success")
        void shouldReturnSuccessOnBlankBody() {
            when(channelService.getByType("dingtalk")).thenReturn(List.of(channel("c5", "dingtalk")));

            assertThat(controller.callback("dingtalk", null, null, null, "real-token", null))
                    .isEqualTo("success");
            verify(channelService, never()).handleMessage(any(ChannelReplyDTO.class));
        }

        @Test
        @DisplayName("加密报文跳过解密直接返回 success")
        void shouldSkipEncryptedPayload() {
            when(channelService.getByType("dingtalk")).thenReturn(List.of(channel("c5", "dingtalk")));

            String result = controller.callback("dingtalk", null, null, null, "real-token",
                    "{\"encrypt\":\"cipher-text\"}");

            assertThat(result).isEqualTo("success");
            verify(channelService, never()).handleMessage(any(ChannelReplyDTO.class));
        }

        @Test
        @DisplayName("解析文本消息并转发，返回 success")
        void shouldParseTextMessage() {
            when(channelService.getByType("dingtalk")).thenReturn(List.of(channel("c5", "dingtalk")));
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(replyWith("ok"));

            String result = controller.callback("dingtalk", null, null, null, "real-token",
                    "{\"msgtype\":\"text\",\"text\":{\"content\":\"钉钉你好\"},\"senderId\":\"staff-1\"}");

            assertThat(result).isEqualTo("success");
            ChannelReplyDTO forwarded = captureForwardedMessage();
            assertThat(forwarded.getChannelId()).isEqualTo("c5");
            assertThat(forwarded.getMessage()).isEqualTo("钉钉你好");
            assertThat(forwarded.getOpenId()).isEqualTo("staff-1");
            assertThat(forwarded.getMsgType()).isEqualTo("text");
        }

        @Test
        @DisplayName("缺少 text 对象时消息内容为 null，仍转发并返回 success")
        void shouldTolerateMissingTextBlock() {
            when(channelService.getByType("dingtalk")).thenReturn(List.of(channel("c5", "dingtalk")));
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(replyWith("ok"));

            String result = controller.callback("dingtalk", null, null, null, "real-token",
                    "{\"msgtype\":\"event\",\"senderId\":\"staff-2\"}");

            assertThat(result).isEqualTo("success");
            ChannelReplyDTO forwarded = captureForwardedMessage();
            assertThat(forwarded.getMessage()).isNull();
            assertThat(forwarded.getMsgType()).isEqualTo("event");
            assertThat(forwarded.getOpenId()).isEqualTo("staff-2");
        }

        @Test
        @DisplayName("畸形 JSON 当前直接抛 IllegalStateException（现状锁定：未走 success 兜底）")
        void shouldThrowOnMalformedJson() {
            when(channelService.getByType("dingtalk")).thenReturn(List.of(channel("c5", "dingtalk")));

            // JsonUtils.parseObject 解析失败抛 IllegalStateException，因此下方 obj == null 的兜底分支不可达
            assertThatThrownBy(() -> controller.callback("dingtalk", null, null, null, "real-token", "not-a-json"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ------------------------------------------------------------------
    // POST 飞书回调
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("POST callback - 飞书分支")
    class FeishuCallback {

        @Test
        @DisplayName("URL 验证直接返回 challenge，不校验 token、不转发消息")
        void shouldAnswerUrlVerificationChallenge() {
            when(channelService.getByType("feishu")).thenReturn(List.of(channel("c6", "feishu")));

            String result = controller.callback("feishu", null, null, null, null,
                    "{\"type\":\"url_verification\",\"challenge\":\"challenge-123\",\"token\":\"whatever\"}");

            assertThat(JsonUtils.parseObject(result).path("challenge").asText()).isEqualTo("challenge-123");
            verify(channelService, never()).handleMessage(any(ChannelReplyDTO.class));
        }

        @Test
        @DisplayName("事件报文 token 不匹配时返回 fail")
        void shouldRejectTokenMismatch() {
            when(channelService.getByType("feishu")).thenReturn(List.of(channel("c6", "feishu")));

            String result = controller.callback("feishu", null, null, null, "wrong-token",
                    "{\"type\":\"event_callback\",\"token\":\"wrong-token\"}");

            assertThat(result).isEqualTo("fail");
            verify(channelService, never()).handleMessage(any(ChannelReplyDTO.class));
        }

        @Test
        @DisplayName("空报文返回 success")
        void shouldReturnSuccessOnBlankBody() {
            when(channelService.getByType("feishu")).thenReturn(List.of(channel("c6", "feishu")));

            assertThat(controller.callback("feishu", null, null, null, "real-token", null))
                    .isEqualTo("success");
            verify(channelService, never()).handleMessage(any(ChannelReplyDTO.class));
        }

        @Test
        @DisplayName("解析事件消息：content 取 JSON 中的 text，openId 取 sender_id.open_id")
        void shouldParseEventMessage() {
            when(channelService.getByType("feishu")).thenReturn(List.of(channel("c6", "feishu")));
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(replyWith("ok"));

            String body = "{\"type\":\"event_callback\",\"token\":\"real-token\","
                    + "\"event\":{\"message\":{\"message_type\":\"text\",\"content\":\"{\\\"text\\\":\\\"飞书你好\\\"}\"},"
                    + "\"sender\":{\"sender_id\":{\"open_id\":\"ou_1\"}}}}";
            String result = controller.callback("feishu", null, null, null, "real-token", body);

            assertThat(result).isEqualTo("success");
            ChannelReplyDTO forwarded = captureForwardedMessage();
            assertThat(forwarded.getChannelId()).isEqualTo("c6");
            assertThat(forwarded.getMessage()).isEqualTo("飞书你好");
            assertThat(forwarded.getOpenId()).isEqualTo("ou_1");
            assertThat(forwarded.getMsgType()).isEqualTo("text");
        }

        @Test
        @DisplayName("content 非 JSON 时回退为原始文本，sender 缺失时 openId 为 null")
        void shouldFallbackToRawContentWhenNotJson() {
            when(channelService.getByType("feishu")).thenReturn(List.of(channel("c6", "feishu")));
            when(channelService.handleMessage(any(ChannelReplyDTO.class))).thenReturn(replyWith("ok"));

            String body = "{\"type\":\"event_callback\",\"token\":\"real-token\","
                    + "\"event\":{\"message\":{\"message_type\":\"text\",\"content\":\"纯文本消息\"}}}";
            String result = controller.callback("feishu", null, null, null, "real-token", body);

            assertThat(result).isEqualTo("success");
            ChannelReplyDTO forwarded = captureForwardedMessage();
            assertThat(forwarded.getMessage()).isEqualTo("纯文本消息");
            assertThat(forwarded.getOpenId()).isNull();
        }

        @Test
        @DisplayName("无 event 字段时返回 success 且不转发")
        void shouldReturnSuccessWhenEventMissing() {
            when(channelService.getByType("feishu")).thenReturn(List.of(channel("c6", "feishu")));

            String result = controller.callback("feishu", null, null, null, "real-token",
                    "{\"type\":\"event_callback\",\"token\":\"real-token\"}");

            assertThat(result).isEqualTo("success");
            verify(channelService, never()).handleMessage(any(ChannelReplyDTO.class));
        }
    }
}
