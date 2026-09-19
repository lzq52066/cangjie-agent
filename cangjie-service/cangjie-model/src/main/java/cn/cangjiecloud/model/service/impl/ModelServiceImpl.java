package cn.cangjiecloud.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.model.*;
import cn.cangjiecloud.model.api.dto.ModelCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelUpdateDTO;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.entity.ModelProviderEntity;
import cn.cangjiecloud.model.mapper.ModelMapper;
import cn.cangjiecloud.model.mapper.ModelProviderMapper;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.security.ApiKeyCipher;
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

    /** 瞬时故障（限流 / 5xx / 超时 / 网络）最大重试次数，0 表示关闭自动重试 */
    @Value("${cangjie.model.retry.max-retries:2}")
    private int maxRetries;

    /** 首次重试延迟（毫秒），后续按 2 倍指数退避 */
    @Value("${cangjie.model.retry.delay-ms:500}")
    private long retryDelayMs;

    /**
     * LLM 调用监听器（由可观测模块注册），统一收敛请求 / 响应 / 异常与真实 token usage。
     * 未注册任何监听器时注入为 null，客户端自行按空列表处理。
     */
    @Autowired(required = false)
    private List<dev.langchain4j.model.chat.listener.ChatModelListener> chatModelListeners;

    @Autowired
    @Qualifier("llmStreamExecutor")
    private AsyncTaskExecutor streamExecutor;

    @Autowired
    private cn.cangjiecloud.model.circuitbreaker.ModelCircuitBreaker circuitBreaker;

    @Autowired
    private ApiKeyCipher apiKeyCipher;

    @Autowired
    private ModelProviderMapper modelProviderMapper;

    private OpenAICompatibleClient newClient(ModelEntity entity) {
        // 模型不持有凭证，API Key / Base URL 一律取自关联厂商（数据库中为密文）
        ModelProviderEntity provider = requireProvider(entity.getProviderId());
        assertProviderReady(provider);
        return new OpenAICompatibleClient(entity, apiKeyCipher.decrypt(provider.getApiKey()),
                provider.getBaseUrl(), Duration.ofSeconds(timeoutSeconds),
                streamExecutor, circuitBreaker, chatModelListeners, maxRetries, retryDelayMs);
    }

    /**
     * 加载厂商配置，未选择或厂商不存在时抛出友好异常
     */
    private ModelProviderEntity requireProvider(String providerId) {
        if (!StringUtils.hasText(providerId)) {
            throw new ApiException("请选择厂商");
        }
        ModelProviderEntity provider = modelProviderMapper.selectById(providerId);
        if (provider == null) {
            throw new ApiException("关联厂商不存在或已删除: " + providerId);
        }
        return provider;
    }

    /**
     * 校验厂商凭证齐备，避免创建出无法调用的模型
     */
    private void assertProviderReady(ModelProviderEntity provider) {
        if (!StringUtils.hasText(provider.getBaseUrl())) {
            throw new ApiException("厂商「" + provider.getName() + "」未配置 Base URL，请先在厂商管理中维护");
        }
        if (!StringUtils.hasText(provider.getApiKey())) {
            throw new ApiException("厂商「" + provider.getName() + "」未配置 API Key，请先在厂商管理中维护");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelEntity create(ModelCreateDTO dto) {
        // 凭证统一由厂商维护：模型不存储 API Key / Base URL，运行时全部继承厂商配置
        ModelProviderEntity provider = requireProvider(dto.getProviderId());
        assertProviderReady(provider);

        ModelEntity entity = new ModelEntity();
        entity.setName(dto.getName());
        entity.setModelType(provider.getCode());
        entity.setProviderId(provider.getId());
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
        // providerId 为空表示不修改关联
        if (StringUtils.hasText(dto.getProviderId())) {
            ModelProviderEntity provider = requireProvider(dto.getProviderId());
            assertProviderReady(provider);
            entity.setProviderId(provider.getId());
            // 关联厂商后类型以厂商标识为准
            entity.setModelType(provider.getCode());
        }
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
    public IPage<ModelEntity> pageQuery(String keyword, String modelType, String providerId,
                                        Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ModelEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ModelEntity::getName, keyword);
        }
        if (StringUtils.hasText(modelType)) {
            wrapper.eq(ModelEntity::getModelType, modelType);
        }
        if (StringUtils.hasText(providerId)) {
            wrapper.eq(ModelEntity::getProviderId, providerId);
        }
        wrapper.orderByDesc(ModelEntity::getIsDefault).orderByDesc(ModelEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
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
                    // 连通性测试同样纳入统一 trace，便于排查与成本归集
                    .traceContext(ChatTraceContext.builder()
                            .requestId("model-test-" + modelId)
                            .modelId(entity.getId())
                            .modelName(entity.getName())
                            .build())
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

    /**
     * 失效所有继承该厂商凭证的模型客户端缓存（厂商凭证/地址变更时调用）
     */
    @Override
    public void evictClientsOfProvider(String providerId) {
        if (!StringUtils.hasText(providerId)) {
            return;
        }
        List<ModelEntity> models = list(new LambdaQueryWrapper<ModelEntity>()
                .select(ModelEntity::getId)
                .eq(ModelEntity::getProviderId, providerId));
        models.forEach(m -> evictClient(m.getId()));
        if (!models.isEmpty()) {
            log.info("厂商 {} 凭证变更，已失效 {} 个模型客户端缓存", providerId, models.size());
        }
    }

    private void clearOtherDefaults(String excludeId) {
        lambdaUpdate()
                .ne(ModelEntity::getId, excludeId)
                .set(ModelEntity::getIsDefault, false)
                .update();
    }
}
