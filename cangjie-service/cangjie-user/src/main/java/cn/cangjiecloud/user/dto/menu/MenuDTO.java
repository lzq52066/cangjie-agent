package cn.cangjiecloud.user.dto.menu;

import lombok.Data;

/**
 * 菜单 DTO
 */
@Data
public class MenuDTO {

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