package cn.cangjiecloud.common.mp.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MetaObjectHandlerImpl implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String userId = currentUserId();
        String tenantId = currentTenantId();

        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createBy", String.class, userId);
        this.strictInsertFill(metaObject, "updateBy", String.class, userId);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
        this.strictInsertFill(metaObject, "tenantId", String.class, tenantId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", String.class, currentUserId());
    }

    private String currentUserId() {
        try {
            String uid = UserContext.getUserId();
            return uid == null ? "system" : uid;
        } catch (Exception e) {
            return "system";
        }
    }

    private String currentTenantId() {
        try {
            String tid = UserContext.getTenantId();
            return tid == null ? AppConst.DEFAULT_TENANT_ID : tid;
        } catch (Exception e) {
            return AppConst.DEFAULT_TENANT_ID;
        }
    }
}
