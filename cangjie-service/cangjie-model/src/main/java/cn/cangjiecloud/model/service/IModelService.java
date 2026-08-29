package cn.cangjiecloud.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.model.api.dto.ModelCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelUpdateDTO;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;

import java.util.List;

public interface IModelService extends IService<ModelEntity> {

    ModelEntity create(ModelCreateDTO dto);

    ModelEntity update(String id, ModelUpdateDTO dto);

    void delete(String id);

    List<ModelEntity> list(String keyword, String modelType);

    /**
     * 测试模型连通性
     */
    String testModel(String modelId, String message);

    /**
     * 获取默认模型
     */
    ModelEntity getDefaultModel();

    /**
     * 设置默认模型
     */
    void setDefault(String modelId);

    /**
     * 根据模型 ID 获取 OpenAI 兼容客户端
     */
    OpenAICompatibleClient getClient(String modelId);

    /**
     * 获取默认模型的客户端
     */
    OpenAICompatibleClient getDefaultClient();

    /**
     * 失效指定模型的客户端缓存（模型配置变更时调用）
     */
    void evictClient(String modelId);

    /**
     * API Key 脱敏（解密后掩码，用于对外展示）
     */
    String maskApiKey(String stored);
}
