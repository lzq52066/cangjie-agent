package cn.cangjiecloud.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.AesUtil;
import cn.cangjiecloud.core.model.*;
import cn.cangjiecloud.model.api.dto.ModelCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelUpdateDTO;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.mapper.ModelMapper;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ModelServiceImpl extends ServiceImpl<ModelMapper, ModelEntity>
        implements IModelService {

    /**
     * 模型客户端缓存：key = modelId，value = OpenAICompatibleClient
     * <p>
     * 避免每次对话都重新创建客户端实例，减少连接开销。
     * 模型配置变更时通过 {@link #evictClient(String)} 主动失效。
     */
    private final Map<String, OpenAICompatibleClient> clientCache = new ConcurrentHashMap<>();

    /** 单次 LLM 请求超时（秒） */
    @Value("${cangjie.model.timeout-seconds:120}")
    private long timeoutSeconds;

    /** 指定降级模型 ID（可选，未配置时使用默认模型降级） */
    @Value("${cangjie.model.fallback-model-id:}")
    private String fallbackModelId;

    /** 模型 API Key 加密密钥（生产环境务必修改） */
    @Value("${cangjie.security.model-key-secret:cangjie-model-key-change-me-pls}")
    private String keySecret;

    @Autowired
    @Qualifier("llmStreamExecutor")
    private AsyncTaskExecutor streamExecutor;

    @Autowired
    private cn.cangjiecloud.model.circuitbreaker.ModelCircuitBreaker circuitBreaker;

    private OpenAICompatibleClient newClient(ModelEntity entity) {
        // 解密 API Key 后构建客户端，数据库始终存储密文
        ModelEntity copy = new ModelEntity();
        org.springframework.beans.BeanUtils.copyProperties(entity, copy);
        copy.setApiKey(decryptApiKey(entity.getApiKey()));
        return new OpenAICompatibleClient(copy, Duration.ofSeconds(timeoutSeconds),
                streamExecutor, circuitBreaker);
    }

    private String encryptApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        return AesUtil.encrypt(apiKey, keySecret);
    }

    /**
     * 解密 API Key；兼容存量明文数据（解密失败原样返回）
     */
    private String decryptApiKey(String stored) {
        if (!StringUtils.hasText(stored)) {
            return stored;
        }
        try {
            return AesUtil.decrypt(stored, keySecret);
        } catch (Exception e) {
            log.debug("API Key 非密文格式，按明文处理（存量数据兼容）");
            return stored;
        }
    }

    @Override
    public String maskApiKey(String stored) {
        String plain = decryptApiKey(stored);
        if (!StringUtils.hasText(plain)) {
            return plain;
        }
        if (plain.length() <= 8) {
            return "****";
        }
        return "****" + plain.substring(plain.length() - 4);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelEntity create(ModelCreateDTO dto) {
        ModelType type = ModelType.of(dto.getModelType());

        ModelEntity entity = new ModelEntity();
        entity.setName(dto.getName());
        entity.setModelType(type.getCode());
        entity.setApiKey(encryptApiKey(dto.getApiKey()));
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
        entity.setApiKey(maskApiKey(entity.getApiKey()));
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
        if (StringUtils.hasText(dto.getApiKey())) entity.setApiKey(encryptApiKey(dto.getApiKey()));
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
        // 配置变更后失效缓存，避免生产路径继续使用旧配置
        evictClient(id);
        entity.setApiKey(maskApiKey(entity.getApiKey()));
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ModelEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        evictClient(id);
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
        List<ModelEntity> models = list(wrapper);
        models.forEach(m -> m.setApiKey(maskApiKey(m.getApiKey())));
        return models;
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
            OpenAICompatibleClient client = newClient(entity);
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
        evictClient(modelId);
    }

    /**
     * 获取模型客户端（带缓存 + 熔断降级）
     * <p>
     * 目标模型处于熔断状态时，自动降级到配置的降级模型或默认模型；
     * 降级目标也不可用时抛出友好异常。
     */
    public OpenAICompatibleClient getClient(String modelId) {
        if (circuitBreaker.allowRequest(modelId)) {
            return clientCache.computeIfAbsent(modelId, id -> {
                ModelEntity entity = getById(id);
                if (entity == null) {
                    throw new ApiException("模型不存在: " + id);
                }
                log.info("创建模型客户端缓存: {} ({})", entity.getName(), id);
                return newClient(entity);
            });
        }
        OpenAICompatibleClient fallback = resolveFallbackClient(modelId);
        if (fallback != null) {
            return fallback;
        }
        throw new ApiException("模型暂时不可用（熔断中）且无可用降级模型，请稍后重试");
    }

    /**
     * 解析降级模型客户端：优先配置的 fallback 模型，其次默认模型
     */
    private OpenAICompatibleClient resolveFallbackClient(String failedModelId) {
        ModelEntity fallback = null;
        if (StringUtils.hasText(fallbackModelId) && !fallbackModelId.equals(failedModelId)) {
            fallback = getById(fallbackModelId);
        }
        if (fallback == null) {
            ModelEntity def = getDefaultModel();
            if (def != null && !def.getId().equals(failedModelId)) {
                fallback = def;
            }
        }
        if (fallback == null || !circuitBreaker.allowRequest(fallback.getId())) {
            return null;
        }
        log.warn("模型 {} 已熔断，降级到模型: {} ({})", failedModelId, fallback.getName(), fallback.getId());
        ModelEntity target = fallback;
        return clientCache.computeIfAbsent(target.getId(), id -> newClient(target));
    }

    /**
     * 获取默认模型客户端（带缓存）
     */
    public OpenAICompatibleClient getDefaultClient() {
        ModelEntity entity = getDefaultModel();
        if (entity == null) {
            throw new ApiException("未配置默认模型");
        }
        return clientCache.computeIfAbsent(entity.getId(), id -> {
            log.info("创建默认模型客户端缓存: {} ({})", entity.getName(), id);
            return newClient(entity);
        });
    }

    /**
     * 失效指定模型的客户端缓存
     */
    public void evictClient(String modelId) {
        OpenAICompatibleClient removed = clientCache.remove(modelId);
        if (removed != null) {
            log.info("模型客户端缓存已失效: {}", modelId);
        }
    }

    private void clearOtherDefaults(String excludeId) {
        lambdaUpdate()
                .ne(ModelEntity::getId, excludeId)
                .set(ModelEntity::getIsDefault, false)
                .update();
    }
}
