package cn.cangjiecloud.user.dto.role;

import lombok.Data;

/**
 * 角色 DTO
 */
@Data
public class RoleDTO {

    /** 角色名称 */
    private String name;

    /** 角色编码 */
    private String code;

    /** 描述 */
    private String description;

    /** 状态：active / inactive */
    private String status;
}