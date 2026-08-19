package cn.cangjiecloud.common.constant;

public interface AppConst {

    String ADMIN_API = "/api/admin";
    String CHAT_API = "/api/chat";
    String OPEN_API = "/api/open";
    String TRIGGER_API = "/trigger";
    String CHAT_PATH = "/chat";
    String ADMIN_PATH = "/admin";

    String DEFAULT_USERNAME = "admin";
    String DEFAULT_EMAIL = "admin@cangjiecloud.cn";
    String DEFAULT_PHONE = "18800000000";

    String ROLE_ADMIN = "ADMIN";
    String ROLE_USER = "USER";

    interface SourceType {
        Integer PARAGRAPH = 1;
        Integer PROBLEM = 2;
    }

    interface Workspace {
        String DEFAULT_WORKSPACE_ID = "default_workspace";
    }
}
