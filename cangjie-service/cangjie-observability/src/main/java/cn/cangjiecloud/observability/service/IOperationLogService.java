package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.observability.entity.OperationLogEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 操作日志服务
 */
public interface IOperationLogService extends IService<OperationLogEntity> {

    /**
     * 记录操作日志
     */
    void record(OperationLogEntity entity);

    /**
     * 分页查询操作日志
     */
    IPage<OperationLogEntity> pageQuery(String module, String action, String status,
                                        Integer pageNum, Integer pageSize);
}