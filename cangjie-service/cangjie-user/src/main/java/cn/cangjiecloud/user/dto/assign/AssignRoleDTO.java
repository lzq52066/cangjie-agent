package cn.cangjiecloud.user.dto.assign;

import lombok.Data;

import java.util.List;

/**
 * 分配角色 DTO
 */
@Data
public class AssignRoleDTO {

    /** 角色 ID */
    private String roleId;

    /** 用户 ID 列表 */
    private List<String> userIds;
}