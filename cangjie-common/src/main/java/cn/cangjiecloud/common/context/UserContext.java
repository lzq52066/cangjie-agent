package cn.cangjiecloud.common.context;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class UserContext {

    private static final String IDENTITY_KEY = "identity";

    public static String getUserId() {
        if (!StpUtil.isLogin()) return null;
        return (String) StpUtil.getLoginIdDefaultNull();
    }

    public static String getWorkspaceId() {
        String wid = (String) StpUtil.getSession().get("workspaceId");
        return wid == null ? AppConst.Workspace.DEFAULT_WORKSPACE_ID : wid;
    }

    public static UserIdentity getIdentity() {
        Object raw = StpUtil.getSession().get(IDENTITY_KEY);
        if (raw == null) return null;
        if (raw instanceof ObjectNode j) {
            return JsonUtils.toObject(j, UserIdentity.class);
        }
        return JsonUtils.convert(raw, UserIdentity.class);
    }

    public static void setIdentity(UserIdentity identity) {
        StpUtil.getSession().set(IDENTITY_KEY, identity);
        StpUtil.getSession().set("workspaceId", identity.getWorkspaceId());
    }

    /** 清除会话中缓存的身份信息，下次获取时按最新用户数据重建 */
    public static void clearIdentity() {
        StpUtil.getSession().delete(IDENTITY_KEY);
    }

    public static void setUserId(String userId) {
        StpUtil.login(userId);
    }
}
