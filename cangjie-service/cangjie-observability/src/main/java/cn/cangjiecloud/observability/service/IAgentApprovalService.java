package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.core.harness.ApprovalStore;
import cn.cangjiecloud.observability.entity.AgentApprovalEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * Agent 审批服务（同时是 core {@link ApprovalStore} SPI 的实现）
 */
public interface IAgentApprovalService extends IService<AgentApprovalEntity>, ApprovalStore {

    /**
     * 审批单分页查询
     *
     * @param status  状态过滤（可空）
     * @param appId   应用过滤（可空）
     * @param userId  申请人过滤（可空）
     */
    IPage<AgentApprovalEntity> pageQuery(String status, String appId, String userId,
                                        Integer pageNum, Integer pageSize);
}
