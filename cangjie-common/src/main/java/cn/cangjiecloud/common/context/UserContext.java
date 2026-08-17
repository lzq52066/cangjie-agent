package cn.cangjiecloud.common.context;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.UserIdentity;

public class UserContext {

    private static final String IDENTITY_KEY = "identity";

    public static String getUserId() {
        if (!StpUtil.isLogin()) return null;
        return (String) StpUtil.getLoginIdDefaultNull();
    }

    public static String getTenantId() {
        String tid = (String) StpUtil.getSession().get("tenantId");
        return tid == null ? AppConst.DEFAULT_TENANT_ID : tid;
    }

    public static String getWorkspaceId() {
        String wid = (String) StpUtil.getSession().get("workspaceId");
        return wid == null ? AppConst.Workspace.DEFAULT_WORKSPACE_ID : wid;
    }

    public static UserIdentity getIdentity() {
        Object raw = StpUtil.getSession().get(IDENTITY_KEY);
        if (raw == null) return null;
        if (raw instanceof JSONObject j) {
            return j.toJavaObject(UserIdentity.class);
        }
        return JSON.parseObject(JSON.toJSONString(raw), UserIdentity.class);
    }

    public static void setIdentity(UserIdentity identity) {
        StpUtil.getSession().set(IDENTITY_KEY, identity);
        StpUtil.getSession().set("tenantId", identity.getTenantId());
        StpUtil.getSession().set("workspaceId", identity.getWorkspaceId());
    }

    public static void setUserId(String userId) {
        StpUtil.login(userId);
    }
}
