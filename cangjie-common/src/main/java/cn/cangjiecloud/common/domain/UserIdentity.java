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
    /** 是否为待修改的初始密码：true 时前端强制跳转改密页 */
    private Boolean mustChangePassword;
    /** 权限码列表（按钮级权限） */
    private List<String> permissions;
    /** 菜单树（前端路由用） */
    private List<MenuVO> menus;
}
