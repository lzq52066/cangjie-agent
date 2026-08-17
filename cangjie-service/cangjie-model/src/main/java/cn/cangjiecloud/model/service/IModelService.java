package cn.cangjiecloud.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.model.api.dto.ModelCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelUpdateDTO;
import cn.cangjiecloud.model.entity.ModelEntity;

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
}
