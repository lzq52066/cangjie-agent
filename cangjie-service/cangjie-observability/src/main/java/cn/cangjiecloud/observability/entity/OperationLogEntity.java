package cn.cangjiecloud.observability.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "operation_log", autoResultMap = true)
public class OperationLogEntity extends BaseEntity {

    /** 模块 */
    private String module;

    /** 操作 */
    private String action;

    /** 请求方法（HTTP Method） */
    private String method;

    /** 请求路径 */
    private String uri;

    /** 请求参数（JSON） */
    private String params;

    /** 返回结果（JSON） */
    private String result;

    /** 链路 ID */
    private String traceId;

    /** 客户端 IP */
    private String ip;

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 耗时（毫秒） */
    private Long duration;

    /** 状态：success / fail */
    private String status;

    /** 错误信息 */
    private String errorMessage;
}