package cn.cangjiecloud.application.auth;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对外接口鉴权结果。
 */
@Getter
@RequiredArgsConstructor
public class ApiAuthResult {

    public enum Type {
        /** 应用 API Key（企业后端到后端调用） */
        APIKEY,
        /** 企业 OIDC 签发的 JWT（企业用户浏览器直连） */
        JWT
    }

    private final Type type;

    private final ApplicationEntity application;

    /** JWT 验签下的企业用户标识（subject），apikey 场景为 null */
    private final String principalId;

    public static ApiAuthResult apikey(ApplicationEntity application) {
        return new ApiAuthResult(Type.APIKEY, application, null);
    }

    public static ApiAuthResult jwt(ApplicationEntity application, String principalId) {
        return new ApiAuthResult(Type.JWT, application, principalId);
    }
}