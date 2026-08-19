package cn.cangjiecloud.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色菜单关联实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "role_menu", autoResultMap = true)
public class RoleMenuEntity extends BaseEntity {

    /** 角色 ID */
    private String roleId;

    /** 菜单 ID */
    private String menuId;
}