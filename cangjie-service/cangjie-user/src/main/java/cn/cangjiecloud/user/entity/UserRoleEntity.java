package cn.cangjiecloud.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户角色关联实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "user_role", autoResultMap = true)
public class UserRoleEntity extends BaseEntity {

    /** 用户 ID */
    private String userId;

    /** 角色 ID */
    private String roleId;
}