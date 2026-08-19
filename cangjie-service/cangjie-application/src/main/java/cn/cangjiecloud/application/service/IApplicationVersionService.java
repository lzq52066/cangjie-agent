package cn.cangjiecloud.application.service;

import cn.cangjiecloud.application.api.dto.ApplicationRollbackDTO;
import cn.cangjiecloud.application.api.dto.ApplicationVersionDTO;

import java.util.List;

public interface IApplicationVersionService {

    /**
     * 按版本 ID 查询
     */
    ApplicationVersionDTO getVersion(String versionId);

    /**
     * 按应用 ID 查询版本列表
     */
    List<ApplicationVersionDTO> listByApplication(String applicationId);

    /**
     * 发布新版本：将当前应用状态快照为新的 version
     */
    ApplicationVersionDTO publish(String applicationId, String operator);

    /**
     * 回滚到指定版本
     */
    void rollback(String applicationId, String versionId);

    /**
     * 删除版本（保留最新版本不允许删除）
     */
    void delete(String versionId);
}