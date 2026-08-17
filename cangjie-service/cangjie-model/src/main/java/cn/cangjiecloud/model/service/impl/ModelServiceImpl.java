package cn.cangjiecloud.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.model.*;
import cn.cangjiecloud.model.api.dto.ModelCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelUpdateDTO;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.mapper.ModelMapper;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class ModelServiceImpl extends ServiceImpl<ModelMapper, ModelEntity>
        implements IModelService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelEntity create(ModelCreateDTO dto) {
        ModelType type = ModelType.of(dto.getModelType());

        ModelEntity entity = new ModelEntity();
        entity.setName(dto.getName());
        entity.setModelType(type.getCode());
        entity.setApiKey(dto.getApiKey());
        entity.setBaseUrl(StringUtils.hasText(dto.getBaseUrl()) ? dto.getBaseUrl() : OpenAICompatibleClient.defaultBaseUrl(type));
        entity.setModelName(dto.getModelName());
        entity.setTemperature(dto.getTemperature() != null ? dto.getTemperature() : 0.7);
        entity.setMaxTokens(dto.getMaxTokens() != null ? dto.getMaxTokens() : 4096);
        entity.setTopP(dto.getTopP() != null ? dto.getTopP() : 1.0);
        entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        entity.setSupportEmbedding(dto.getSupportEmbedding() != null ? dto.getSupportEmbedding() : false);
        entity.setEmbeddingDimension(dto.getEmbeddingDimension());
        entity.setStatus("active");
        entity.setDescription(dto.getDescription());
        save(entity);

        // 如果设为默认，取消其他默认
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearOtherDefaults(entity.getId());
        }
        log.info("模型已创建: {} ({})", entity.getName(), entity.getModelType());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelEntity update(String id, ModelUpdateDTO dto) {
        ModelEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("模型不存在");
        }
        if (StringUtils.hasText(dto.getName())) entity.setName(dto.getName());
        if (StringUtils.hasText(dto.getApiKey())) entity.setApiKey(dto.getApiKey());
        if (StringUtils.hasText(dto.getBaseUrl())) entity.setBaseUrl(dto.getBaseUrl());
        if (StringUtils.hasText(dto.getModelName())) entity.setModelName(dto.getModelName());
        if (dto.getTemperature() != null) entity.setTemperature(dto.getTemperature());
        if (dto.getMaxTokens() != null) entity.setMaxTokens(dto.getMaxTokens());
        if (dto.getTopP() != null) entity.setTopP(dto.getTopP());
        if (dto.getSupportEmbedding() != null) entity.setSupportEmbedding(dto.getSupportEmbedding());
        if (dto.getEmbeddingDimension() != null) entity.setEmbeddingDimension(dto.getEmbeddingDimension());
        if (StringUtils.hasText(dto.getDescription())) entity.setDescription(dto.getDescription());
        if (StringUtils.hasText(dto.getStatus())) entity.setStatus(dto.getStatus());
        if (dto.getIsDefault() != null && dto.getIsDefault()) {
            clearOtherDefaults(id);
            entity.setIsDefault(true);
        }
        updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ModelEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        log.info("模型已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public List<ModelEntity> list(String keyword, String modelType) {
        LambdaQueryWrapper<ModelEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ModelEntity::getIsDefault).orderByDesc(ModelEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ModelEntity::getName, keyword);
        }
        if (StringUtils.hasText(modelType)) {
            wrapper.eq(ModelEntity::getModelType, modelType);
        }
        return list(wrapper);
    }

    @Override
    public String testModel(String modelId, String message) {
        ModelEntity entity = getById(modelId);
        if (entity == null) {
            throw new ApiException("模型不存在");
        }
        if (!"active".equals(entity.getStatus())) {
            throw new ApiException("模型未激活");
        }

        try {
            OpenAICompatibleClient client = new OpenAICompatibleClient(entity);
            ChatRequest request = ChatRequest.builder()
                    .model(entity.getModelName())
                    .messages(List.of(
                            ChatMessage.system("你是一个简洁的助手，请用中文回答。"),
                            ChatMessage.user(message != null ? message : "你好，请介绍一下自己。")
                    ))
                    .temperature(entity.getTemperature() != null ? entity.getTemperature() : 0.7)
                    .maxTokens(entity.getMaxTokens() != null ? entity.getMaxTokens() : 1024)
                    .build();
            ChatResponse response = client.chat(request);
            log.info("模型测试成功: {} → {} tokens", entity.getName(), response.getTotalTokens());
            return response.getContent();
        } catch (Exception e) {
            log.error("模型测试失败: {}", entity.getName(), e);
            throw new ApiException("模型测试失败: " + e.getMessage());
        }
    }

    @Override
    public ModelEntity getDefaultModel() {
        return getOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getIsDefault, true)
                .eq(ModelEntity::getStatus, "active")
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(String modelId) {
        ModelEntity entity = getById(modelId);
        if (entity == null) {
            throw new ApiException("模型不存在");
        }
        clearOtherDefaults(modelId);
        entity.setIsDefault(true);
        updateById(entity);
    }

    /**
     * 获取模型客户端
     */
    public OpenAICompatibleClient getClient(String modelId) {
        ModelEntity entity = getById(modelId);
        if (entity == null) {
            throw new ApiException("模型不存在: " + modelId);
        }
        return new OpenAICompatibleClient(entity);
    }

    /**
     * 获取默认模型客户端
     */
    public OpenAICompatibleClient getDefaultClient() {
        ModelEntity entity = getDefaultModel();
        if (entity == null) {
            throw new ApiException("未配置默认模型");
        }
        return new OpenAICompatibleClient(entity);
    }

    private void clearOtherDefaults(String excludeId) {
        lambdaUpdate()
                .ne(ModelEntity::getId, excludeId)
                .set(ModelEntity::getIsDefault, false)
                .update();
    }
}
