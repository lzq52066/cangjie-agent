package cn.cangjiecloud.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.model.api.dto.ModelProviderCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelProviderUpdateDTO;
import cn.cangjiecloud.model.entity.ModelProviderEntity;

import java.util.List;

/**
 * 厂商配置服务：统一维护各模型厂商的 API Key 与 Base URL
 */
public interface IModelProviderService extends IService<ModelProviderEntity> {

    ModelProviderEntity create(ModelProviderCreateDTO dto);

    ModelProviderEntity update(String id, ModelProviderUpdateDTO dto);

    void delete(String id);

    /**
     * 厂商列表（API Key 已脱敏）
     */
    List<ModelProviderEntity> list(String keyword, String status);
}
