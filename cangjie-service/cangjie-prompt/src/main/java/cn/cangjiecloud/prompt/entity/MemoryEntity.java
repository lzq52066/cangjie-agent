package cn.cangjiecloud.prompt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "memory", autoResultMap = true)
public class MemoryEntity extends BaseEntity {

    /** 应用ID */
    private String applicationId;

    /** 会话ID */
    private String sessionId;

    /** 角色 */
    private String role;

    /** 内容 */
    private String content;

    /** 摘要 */
    private String summary;

    /** 重要性 */
    private Integer importance;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 状态：active / inactive */
    private String status;
}
