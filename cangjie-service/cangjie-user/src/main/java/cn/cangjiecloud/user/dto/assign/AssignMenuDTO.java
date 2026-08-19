package cn.cangjiecloud.user.dto.assign;

import lombok.Data;

import java.util.List;

/**
 * 分配菜单 DTO
 */
@Data
public class AssignMenuDTO {

    /** 角色 ID */
    private String roleId;

    /** 菜单 ID 列表 */
    private List<String> menuIds;
}