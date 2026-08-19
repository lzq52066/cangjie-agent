package cn.cangjiecloud.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单/权限实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "menu", autoResultMap = true)
public class MenuEntity extends BaseEntity {

    /** 菜单名称 */
    private String name;

    /** 权限标识 */
    private String code;

    /** 路由路径 */
    private String path;

    /** 组件路径 */
    private String component;

    /** 图标 */
    private String icon;

    /** 父菜单 ID */
    private String parentId;

    /** 排序 */
    private Integer sort;

    /** 类型：menu / button */
    private String type;

    /** 状态：active / inactive */
    private String status;
}