package cn.cangjiecloud.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

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
    private String tenantId;
    private String workspaceId;
}
