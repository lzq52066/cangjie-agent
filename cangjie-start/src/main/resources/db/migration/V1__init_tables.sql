-- ----------------------------
-- CangJie Agent V1 init tables
-- ----------------------------
CREATE EXTENSION IF NOT EXISTS "vector";

-- ----------------------------
-- system_setting
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."system_setting" (
    "type" int4 NOT NULL,
    "meta" jsonb NOT NULL DEFAULT '{}'::jsonb,
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY ("type")
);

-- ----------------------------
-- user
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."user" (
    "id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "email" varchar(254) COLLATE "pg_catalog"."default",
    "phone" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
    "nickname" varchar(150) COLLATE "pg_catalog"."default" NOT NULL,
    "username" varchar(150) COLLATE "pg_catalog"."default" NOT NULL,
    "password" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
    "role" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "is_active" bool NOT NULL DEFAULT true,
    "source" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
    "language" varchar(10) COLLATE "pg_catalog"."default" NOT NULL,
    "avatar" varchar(512) COLLATE "pg_catalog"."default",
    "tenant_id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6),
    "update_time" timestamp(6),
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "user_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "user_username_key" UNIQUE ("username")
);

CREATE INDEX IF NOT EXISTS "user_email_idx" ON "public"."user" USING btree ("email");
CREATE INDEX IF NOT EXISTS "user_tenant_id_idx" ON "public"."user" USING btree ("tenant_id");

-- ----------------------------
-- user_resource_permission
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."user_resource_permission" (
    "id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "workspace_id" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
    "auth_target_type" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
    "target_id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "auth_type" varchar COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'ROLE',
    "permission_list" varchar(256)[] COLLATE "pg_catalog"."default" NOT NULL DEFAULT '{}',
    "user_id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "tenant_id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "user_resource_permission_pkey" PRIMARY KEY ("id")
);

-- ----------------------------
-- audit_log 审计日志
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."audit_log" (
    "id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "trace_id" varchar(64) COLLATE "pg_catalog"."default",
    "user_id" varchar(50) COLLATE "pg_catalog"."default",
    "username" varchar(150) COLLATE "pg_catalog"."default",
    "operation" varchar(64) COLLATE "pg_catalog"."default",
    "resource_type" varchar(64) COLLATE "pg_catalog"."default",
    "resource_id" varchar(50) COLLATE "pg_catalog"."default",
    "request_method" varchar(16) COLLATE "pg_catalog"."default",
    "request_uri" varchar(512) COLLATE "pg_catalog"."default",
    "client_ip" varchar(64) COLLATE "pg_catalog"."default",
    "request_params" text,
    "response_code" int4,
    "cost_ms" int8,
    "is_success" bool DEFAULT true,
    "error_msg" text,
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "tenant_id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'default',
    CONSTRAINT "audit_log_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "audit_log_user_id_idx" ON "public"."audit_log" USING btree ("user_id");
CREATE INDEX IF NOT EXISTS "audit_log_create_time_idx" ON "public"."audit_log" USING btree ("create_time" DESC);
