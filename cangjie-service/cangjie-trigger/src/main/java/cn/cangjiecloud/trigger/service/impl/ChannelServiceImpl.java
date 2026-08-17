package cn.cangjiecloud.trigger.service.impl;

import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.chat.service.IChatService;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.trigger.api.dto.ChannelCreateDTO;
import cn.cangjiecloud.trigger.api.dto.ChannelReplyDTO;
import cn.cangjiecloud.trigger.api.dto.ChannelUpdateDTO;
import cn.cangjiecloud.trigger.entity.ChannelEntity;
import cn.cangjiecloud.trigger.entity.ChannelMessageEntity;
import cn.cangjiecloud.trigger.mapper.ChannelMapper;
import cn.cangjiecloud.trigger.service.IChannelMessageService;
import cn.cangjiecloud.trigger.service.IChannelService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelServiceImpl extends ServiceImpl<ChannelMapper, ChannelEntity>
        implements IChannelService {

    private final IChatService chatService;
    private final IChannelMessageService channelMessageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelEntity create(ChannelCreateDTO dto) {
        ChannelEntity entity = new ChannelEntity();
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setApplicationId(dto.getApplicationId());
        entity.setAppId(dto.getAppId());
        entity.setAppSecret(dto.getAppSecret());
        entity.setToken(dto.getToken());
        entity.setEncodingAesKey(dto.getEncodingAesKey());
        entity.setVerifyToken(dto.getVerifyToken());
        entity.setConfig(dto.getConfig());
        entity.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "inactive");
        entity.setCallCount(0);
        save(entity);
        log.info("渠道已创建: {} ({}) type={}", entity.getName(), entity.getId(), entity.getType());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelEntity update(String id, ChannelUpdateDTO dto) {
        ChannelEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("渠道不存在");
        }
        if (StringUtils.hasText(dto.getName())) entity.setName(dto.getName());
        if (StringUtils.hasText(dto.getType())) entity.setType(dto.getType());
        if (StringUtils.hasText(dto.getApplicationId())) entity.setApplicationId(dto.getApplicationId());
        if (StringUtils.hasText(dto.getAppId())) entity.setAppId(dto.getAppId());
        if (StringUtils.hasText(dto.getAppSecret())) entity.setAppSecret(dto.getAppSecret());
        if (StringUtils.hasText(dto.getToken())) entity.setToken(dto.getToken());
        if (StringUtils.hasText(dto.getEncodingAesKey())) entity.setEncodingAesKey(dto.getEncodingAesKey());
        if (StringUtils.hasText(dto.getVerifyToken())) entity.setVerifyToken(dto.getVerifyToken());
        if (StringUtils.hasText(dto.getConfig())) entity.setConfig(dto.getConfig());
        if (StringUtils.hasText(dto.getStatus())) entity.setStatus(dto.getStatus());
        updateById(entity);
        log.info("渠道已更新: {} ({})", entity.getName(), id);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ChannelEntity entity = getById(id);
        if (entity == null) {
            return;
        }
        removeById(id);
        log.info("渠道已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public List<ChannelEntity> list(String keyword, String type) {
        LambdaQueryWrapper<ChannelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ChannelEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ChannelEntity::getName, keyword);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(ChannelEntity::getType, type);
        }
        return list(wrapper);
    }

    @Override
    public List<ChannelEntity> getByType(String type) {
        if (!StringUtils.hasText(type)) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<ChannelEntity>()
                .eq(ChannelEntity::getType, type)
                .eq(ChannelEntity::getStatus, "active")
                .orderByDesc(ChannelEntity::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelEntity enable(String id) {
        ChannelEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("渠道不存在");
        }
        entity.setStatus("active");
        updateById(entity);
        log.info("渠道已启用: {} ({})", entity.getName(), id);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelEntity disable(String id) {
        ChannelEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("渠道不存在");
        }
        entity.setStatus("inactive");
        updateById(entity);
        log.info("渠道已停用: {} ({})", entity.getName(), id);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelReplyDTO handleMessage(ChannelReplyDTO dto) {
        ChannelEntity channel = getById(dto.getChannelId());
        if (channel == null) {
            throw new ApiException("渠道不存在: " + dto.getChannelId());
        }
        if (!"active".equals(channel.getStatus())) {
            throw new ApiException("渠道未启用: " + channel.getName());
        }
        long start = System.currentTimeMillis();

        ChannelReplyDTO result = new ChannelReplyDTO();
        result.setChannelId(channel.getId());
        result.setMessage(null);
        result.setOpenId(dto.getOpenId());
        result.setMsgType(dto.getMsgType());
        result.setEventType(dto.getEventType());
        result.setApplicationId(channel.getApplicationId());

        // event 等无文本内容的消息不调用对话服务，仅记录
        if (!StringUtils.hasText(dto.getMessage())) {
            saveMessage(channel, dto, null, "processed", null, System.currentTimeMillis() - start);
            updateCallStat(channel, start);
            return result;
        }

        // 用 openId 生成会话前缀，保证不同渠道/用户的会话隔离
        String sessionId = "ch_" + channel.getId() + "_" + dto.getOpenId();

        ChatRequestDTO request = new ChatRequestDTO();
        request.setApplicationId(channel.getApplicationId());
        request.setMessage(dto.getMessage());
        request.setSource(channel.getType());
        request.setSessionId(sessionId);

        try {
            ChatResponseDTO response = chatService.chat(request);
            String reply = response != null ? response.getMessage() : null;
            saveMessage(channel, dto, sessionId, "processed", reply, System.currentTimeMillis() - start);
            updateCallStat(channel, start);
            result.setMessage(reply);
            return result;
        } catch (Exception e) {
            saveMessage(channel, dto, sessionId, "failed", null, System.currentTimeMillis() - start, e.getMessage());
            log.error("渠道消息处理失败: channel={}, openId={}, error={}",
                    channel.getId(), dto.getOpenId(), e.getMessage(), e);
            throw new ApiException("渠道消息处理失败: " + e.getMessage(), e);
        }
    }

    private void saveMessage(ChannelEntity channel, ChannelReplyDTO dto, String sessionId,
                             String status, String replyContent, Long costTime) {
        saveMessage(channel, dto, sessionId, status, replyContent, costTime, null);
    }

    private void saveMessage(ChannelEntity channel, ChannelReplyDTO dto, String sessionId,
                             String status, String replyContent, Long costTime, String errorMessage) {
        ChannelMessageEntity record = new ChannelMessageEntity();
        record.setChannelId(channel.getId());
        record.setChannelType(channel.getType());
        record.setApplicationId(channel.getApplicationId());
        record.setOpenId(dto.getOpenId());
        record.setSessionId(sessionId);
        record.setMsgType(dto.getMsgType());
        record.setContent(dto.getMessage());
        record.setEventType(dto.getEventType());
        record.setReplyContent(replyContent);
        record.setStatus(status);
        record.setErrorMessage(errorMessage);
        record.setCostTime(costTime);
        channelMessageService.save(record);
    }

    private void updateCallStat(ChannelEntity channel, long start) {
        channel.setLastCallTime(LocalDateTime.now());
        channel.setCallCount((channel.getCallCount() == null ? 0 : channel.getCallCount()) + 1);
        updateById(channel);
    }
}
