package cn.cangjiecloud.application.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.application.api.dto.ApplicationCreateDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;

import java.util.List;

public interface IApplicationService extends IService<ApplicationEntity> {

    ApplicationEntity create(ApplicationCreateDTO dto);

    ApplicationEntity update(String id, ApplicationCreateDTO dto);

    void delete(String id);

    List<ApplicationEntity> list(String keyword, String type);

    /**
     * 发布应用：生成对外 API Key，状态置为 published
     */
    ApplicationEntity publish(String id);

    /**
     * 根据 API Key 获取已发布应用
     */
    ApplicationEntity getByApikey(String apikey);
}
