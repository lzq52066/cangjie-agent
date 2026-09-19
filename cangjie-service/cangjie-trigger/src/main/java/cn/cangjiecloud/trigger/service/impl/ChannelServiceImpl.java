package cn.cangjiecloud.trigger.service.impl;

import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
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
import cn.cangjiecloud.workflow.entity.WorkflowEntity;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;
import cn.cangjiecloud.workflow.service.IWorkflowService;
import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelServiceImpl extends ServiceImpl<ChannelMapper, ChannelEntity>
        implements IChannelService {

    private final IChatService chatService;
    private final IChannelMessageService channelMessageService;
    private final IApplicationService applicationService;
    private final IWorkflowService workflowService;

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
    public IPage<ChannelEntity> pageQuery(String keyword, String type, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ChannelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ChannelEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ChannelEntity::getName, keyword);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(ChannelEntity::getType, type);
        }
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
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

        try {
            String reply;
            // 根据应用类型路由：workflow 类型走工作流引擎，其他走对话服务
            ApplicationEntity application = applicationService.getById(channel.getApplicationId());
            if (application != null && "workflow".equals(application.getType())) {
                reply = executeWorkflow(channel.getApplicationId(), dto.getMessage(), sessionId);
            } else {
                ChatRequestDTO request = new ChatRequestDTO();
                request.setApplicationId(channel.getApplicationId());
                request.setMessage(dto.getMessage());
                request.setSource(channel.getType());
                request.setSessionId(sessionId);
                ChatResponseDTO response = chatService.chat(request);
                reply = response != null ? response.getMessage() : null;
            }
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

    /**
     * 执行工作流并将输出转为回复文本
     */
    private String executeWorkflow(String applicationId, String message, String sessionId) {
        WorkflowEntity workflow = workflowService.getByApplicationId(applicationId);
        if (workflow == null) {
            throw new ApiException("关联的工作流不存在或未发布，应用ID: " + applicationId);
        }
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input", message);
        inputs.put("session_id", sessionId);
        WorkflowExecutionEntity execution = workflowService.execute(workflow.getId(), inputs);
        if ("failed".equals(execution.getStatus())) {
            throw new ApiException("工作流执行失败: " + execution.getErrorMessage());
        }
        return extractWorkflowOutput(execution.getOutputs());
    }

    /**
     * 从工作流输出 JSON 中提取回复文本
     */
    private String extractWorkflowOutput(String outputsJson) {
        if (!StringUtils.hasText(outputsJson)) {
            return null;
        }
        try {
            ObjectNode outputs = JsonUtils.parseObject(outputsJson);
            // 按优先级查找常见的输出变量
            for (String key : List.of("output", "result", "reply", "response", "answer", "message")) {
                String value = textOrNull(outputs, key);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            // 兜底：返回第一个非 __end__ 的字符串值
            Iterator<Map.Entry<String, JsonNode>> fields = outputs.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if ("__end__".equals(entry.getKey())) continue;
                JsonNode value = entry.getValue();
                if (value.isTextual() && StringUtils.hasText(value.asText())) {
                    return value.asText();
                }
            }
            return outputsJson;
        } catch (Exception e) {
            log.warn("解析工作流输出失败: {}", e.getMessage());
            return outputsJson;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
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
