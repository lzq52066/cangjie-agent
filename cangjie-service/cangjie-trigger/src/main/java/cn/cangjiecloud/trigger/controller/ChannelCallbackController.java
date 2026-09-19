package cn.cangjiecloud.trigger.controller;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.trigger.api.dto.ChannelReplyDTO;
import cn.cangjiecloud.trigger.entity.ChannelEntity;
import cn.cangjiecloud.trigger.service.IChannelService;
import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 渠道回调接口：接收外部平台（微信/钉钉/飞书）的 Webhook 回调。
 * <p>
 * 回调路径：/trigger/{channelType}/callback
 * - GET：微信 URL 验证，校验通过返回 echostr
 * - POST：微信 XML 消息 / 钉钉 JSON / 飞书 JSON 事件
 * <p>
 * 注意：当前为简化实现，验证仅做简单 token 比对，
 * 生产环境应补充各平台的签名/加解密验证（见各方法 TODO）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.TRIGGER_API + "/{channelType}/callback")
public class ChannelCallbackController {

    private final IChannelService channelService;

    /**
     * GET 回调：微信 URL 验证
     */
    @GetMapping
    public String verify(@PathVariable String channelType,
                         @RequestParam(required = false) String signature,
                         @RequestParam(required = false) String timestamp,
                         @RequestParam(required = false) String nonce,
                         @RequestParam(required = false) String token,
                         @RequestParam(required = false) String echostr) {
        ChannelEntity channel = resolveChannel(channelType);
        // 简化验证：请求参数 token 与渠道配置 token 比对
        // TODO 生产环境应补充微信官方签名验证：signature = sha1(sort(token, timestamp, nonce))，与请求签名比对
        if (StringUtils.hasText(token) && StringUtils.hasText(channel.getToken())
                && !channel.getToken().equals(token)) {
            log.warn("微信回调验证失败: channel={}, token 不匹配", channel.getId());
            return "fail";
        }
        return StringUtils.hasText(echostr) ? echostr : "success";
    }

    /**
     * POST 回调统一入口：按渠道类型分发处理
     */
    @PostMapping
    public String callback(@PathVariable String channelType,
                           @RequestParam(required = false) String signature,
                           @RequestParam(required = false) String timestamp,
                           @RequestParam(required = false) String nonce,
                           @RequestParam(required = false) String token,
                           @RequestBody(required = false) String body) {
        return switch (channelType) {
            case "wechat", "wechat_mp", "wechat_work" -> handleWechat(channelType, body);
            case "dingtalk" -> handleDingtalk(channelType, token, body);
            case "feishu" -> handleFeishu(channelType, token, body);
            default -> throw new ApiException("不支持的渠道类型: " + channelType);
        };
    }

    /**
     * 微信回调：解析 XML 消息，转发后返回被动回复文本
     */
    private String handleWechat(String channelType, String xml) {
        if (!StringUtils.hasText(xml)) {
            return "";
        }
        ChannelEntity channel = resolveChannel(channelType);
        String fromUser = extractXmlValue(xml, "FromUserName");
        String toUser = extractXmlValue(xml, "ToUserName");
        String msgType = extractXmlValue(xml, "MsgType");
        String content = extractXmlValue(xml, "Content");
        String eventType = extractXmlValue(xml, "Event");
        log.info("微信回调收到消息: channel={}, fromUser={}, msgType={}, event={}",
                channel.getId(), fromUser, msgType, eventType);

        ChannelReplyDTO dto = new ChannelReplyDTO();
        dto.setChannelId(channel.getId());
        dto.setMessage(content);
        dto.setOpenId(fromUser);
        dto.setMsgType(msgType);
        dto.setEventType(eventType);
        ChannelReplyDTO result = channelService.handleMessage(dto);

        // 无回复（如事件消息）返回空串表示不被动回复
        if (!StringUtils.hasText(result.getMessage())) {
            return "";
        }
        long createTime = System.currentTimeMillis() / 1000;
        return "<xml>\n"
                + "<ToUserName><![CDATA[" + safe(fromUser) + "]]></ToUserName>\n"
                + "<FromUserName><![CDATA[" + safe(toUser) + "]]></FromUserName>\n"
                + "<CreateTime>" + createTime + "</CreateTime>\n"
                + "<MsgType><![CDATA[text]]></MsgType>\n"
                + "<Content><![CDATA[" + safe(result.getMessage()) + "]]></Content>\n"
                + "</xml>";
    }

    /**
     * 钉钉回调：签名校验（简化）后解析 JSON 消息
     */
    private String handleDingtalk(String channelType, String token, String body) {
        ChannelEntity channel = resolveChannel(channelType);
        // 简化签名校验：请求参数 token 与渠道配置 token 比对
        // TODO 生产环境应校验钉钉签名：sign = Base64(HMAC-SHA256(timestamp + "\n" + token, appSecret))
        if (StringUtils.hasText(token) && StringUtils.hasText(channel.getToken())
                && !channel.getToken().equals(token)) {
            log.warn("钉钉回调签名校验失败: channel={}", channel.getId());
            return "fail";
        }
        if (!StringUtils.hasText(body)) {
            return "success";
        }
        ObjectNode obj = JsonUtils.parseObject(body);
        if (obj == null) {
            return "success";
        }
        // 加密报文：TODO 生产环境使用 AES 解密 encrypt 字段后解析明文消息
        if (obj.has("encrypt")) {
            log.info("钉钉回调为加密报文，当前跳过解密处理: channel={}", channel.getId());
            return "success";
        }
        String msgType = textOrNull(obj, "msgtype");
        ObjectNode text = objectField(obj, "text");
        String content = text != null ? textOrNull(text, "content") : null;
        String senderId = textOrNull(obj, "senderId");
        log.info("钉钉回调收到消息: channel={}, senderId={}, msgType={}", channel.getId(), senderId, msgType);

        ChannelReplyDTO dto = new ChannelReplyDTO();
        dto.setChannelId(channel.getId());
        dto.setMessage(content);
        dto.setOpenId(senderId);
        dto.setMsgType(msgType);
        channelService.handleMessage(dto);
        return "success";
    }

    /**
     * 飞书回调：URL 验证返回 challenge，事件消息解析转发
     */
    private String handleFeishu(String channelType, String token, String body) {
        ChannelEntity channel = resolveChannel(channelType);
        if (!StringUtils.hasText(body)) {
            return "success";
        }
        ObjectNode obj = JsonUtils.parseObject(body);
        if (obj == null) {
            return "success";
        }
        // URL 验证：返回 challenge
        if ("url_verification".equals(textOrNull(obj, "type")) && obj.has("challenge")) {
            ObjectNode resp = JsonUtils.newObject();
            resp.put("challenge", textOrNull(obj, "challenge"));
            return resp.toString();
        }
        // 简化验证：请求参数 token 与渠道配置 token 比对
        // TODO 生产环境应校验飞书请求头 X-Lark-Signature（HMAC-SHA256(timestamp + nonce + body, encryptKey)）
        if (StringUtils.hasText(token) && StringUtils.hasText(channel.getToken())
                && !channel.getToken().equals(token)) {
            log.warn("飞书回调验证失败: channel={}", channel.getId());
            return "fail";
        }
        // 事件消息
        ObjectNode event = objectField(obj, "event");
        if (event == null) {
            return "success";
        }
        ObjectNode message = objectField(event, "message");
        String msgType = message != null ? textOrNull(message, "message_type") : null;
        String content = null;
        String rawContent = message != null ? textOrNull(message, "content") : null;
        if (message != null && StringUtils.hasText(rawContent)) {
            try {
                ObjectNode contentObj = JsonUtils.parseObject(rawContent);
                content = contentObj != null ? textOrNull(contentObj, "text") : null;
            } catch (Exception e) {
                content = rawContent;
            }
        }
        String openId = null;
        ObjectNode sender = objectField(event, "sender");
        if (sender != null) {
            ObjectNode senderId = objectField(sender, "sender_id");
            if (senderId != null) {
                openId = textOrNull(senderId, "open_id");
            }
        }
        log.info("飞书回调收到消息: channel={}, openId={}, msgType={}", channel.getId(), openId, msgType);

        ChannelReplyDTO dto = new ChannelReplyDTO();
        dto.setChannelId(channel.getId());
        dto.setMessage(content);
        dto.setOpenId(openId);
        dto.setMsgType(msgType);
        channelService.handleMessage(dto);
        return "success";
    }

    /**
     * 按类型解析启用渠道（取该类型第一个启用渠道）
     */
    private ChannelEntity resolveChannel(String channelType) {
        List<ChannelEntity> channels = channelService.getByType(channelType);
        if (channels == null || channels.isEmpty()) {
            throw new ApiException("该类型下没有启用的渠道: " + channelType);
        }
        return channels.get(0);
    }

    private String extractXmlValue(String xml, String tag) {
        if (!StringUtils.hasText(xml)) {
            return null;
        }
        Pattern cdataPattern = Pattern.compile("<" + tag + "><!\\[CDATA\\[(.*?)\\]\\]></" + tag + ">");
        Matcher cdataMatcher = cdataPattern.matcher(xml);
        if (cdataMatcher.find()) {
            return cdataMatcher.group(1);
        }
        Pattern plainPattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher plainMatcher = plainPattern.matcher(xml);
        if (plainMatcher.find()) {
            return plainMatcher.group(1).trim();
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** 取出对象字段：不存在、为 null 或不是 JSON 对象时返回 null */
    private static ObjectNode objectField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value instanceof ObjectNode object ? object : null;
    }

    /** 取出文本字段：不存在或为 null 时返回 null（数值等其它类型取字面量） */
    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
