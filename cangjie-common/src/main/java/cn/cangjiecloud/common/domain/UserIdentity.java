package cn.cangjiecloud.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentity implements Serializable {
    private String userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String role;
    private String workspaceId;
    /** 权限码列表（按钮级权限） */
    private List<String> permissions;
    /** 菜单树（前端路由用） */
    private List<MenuVO> menus;
}
