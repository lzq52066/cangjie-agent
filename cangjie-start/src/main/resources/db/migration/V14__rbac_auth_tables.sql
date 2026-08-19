-- ----------------------------
-- CangJie Agent V12: RBAC 权限管理模块表结构
-- ----------------------------

-- ----------------------------
-- role 角色表
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."role" (
    "id" varchar(50) NOT NULL,
    "name" varchar(100) NOT NULL,
    "code" varchar(100) NOT NULL,
    "description" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "role_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "role_code_idx" ON "public"."role" USING btree ("code");
CREATE INDEX IF NOT EXISTS "role_status_idx" ON "public"."role" USING btree ("status");
CREATE INDEX IF NOT EXISTS "role_tenant_idx" ON "public"."role" USING btree ("tenant_id");

-- ----------------------------
-- menu 菜单/权限表
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."menu" (
    "id" varchar(50) NOT NULL,
    "name" varchar(100) NOT NULL,
    "code" varchar(100) NOT NULL,
    "path" varchar(200),
    "component" varchar(200),
    "icon" varchar(100),
    "parent_id" varchar(50),
    "sort" int4 NOT NULL DEFAULT 0,
    "type" varchar(20) NOT NULL DEFAULT 'menu',
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "menu_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "menu_parent_id_idx" ON "public"."menu" USING btree ("parent_id");
CREATE INDEX IF NOT EXISTS "menu_type_idx" ON "public"."menu" USING btree ("type");
CREATE INDEX IF NOT EXISTS "menu_status_idx" ON "public"."menu" USING btree ("status");
CREATE INDEX IF NOT EXISTS "menu_tenant_idx" ON "public"."menu" USING btree ("tenant_id");

-- ----------------------------
-- user_role 用户角色关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."user_role" (
    "id" varchar(50) NOT NULL,
    "user_id" varchar(50) NOT NULL,
    "role_id" varchar(50) NOT NULL,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "user_role_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "user_role_user_id_idx" ON "public"."user_role" USING btree ("user_id");
CREATE INDEX IF NOT EXISTS "user_role_role_id_idx" ON "public"."user_role" USING btree ("role_id");
CREATE INDEX IF NOT EXISTS "user_role_tenant_idx" ON "public"."user_role" USING btree ("tenant_id");

-- ----------------------------
-- role_menu 角色菜单关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."role_menu" (
    "id" varchar(50) NOT NULL,
    "role_id" varchar(50) NOT NULL,
    "menu_id" varchar(50) NOT NULL,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "role_menu_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "role_menu_role_id_idx" ON "public"."role_menu" USING btree ("role_id");
CREATE INDEX IF NOT EXISTS "role_menu_menu_id_idx" ON "public"."role_menu" USING btree ("menu_id");
CREATE INDEX IF NOT EXISTS "role_menu_tenant_idx" ON "public"."role_menu" USING btree ("tenant_id");