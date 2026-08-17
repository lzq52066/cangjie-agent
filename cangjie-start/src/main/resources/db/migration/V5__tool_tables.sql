-- ----------------------------
-- CangJie Agent V5: 工具/插件系统模块表结构（万物即插件）
-- ----------------------------

-- ----------------------------
-- tool 工具配置
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."tool" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "type" varchar(50),
    "function_name" varchar(200),
    "parameters" text,
    "implementation" varchar(500),
    "config" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "icon" varchar(200),
    "category" varchar(100),
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "tool_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "tool_name_idx" ON "public"."tool" USING btree ("name");
CREATE INDEX IF NOT EXISTS "tool_type_idx" ON "public"."tool" USING btree ("type");
CREATE INDEX IF NOT EXISTS "tool_function_name_idx" ON "public"."tool" USING btree ("function_name");
CREATE INDEX IF NOT EXISTS "tool_status_idx" ON "public"."tool" USING btree ("status");
CREATE INDEX IF NOT EXISTS "tool_category_idx" ON "public"."tool" USING btree ("category");
CREATE INDEX IF NOT EXISTS "tool_tenant_idx" ON "public"."tool" USING btree ("tenant_id");

-- ----------------------------
-- plugin_instance 插件实例
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."plugin_instance" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "type" varchar(50),
    "description" text,
    "class_name" varchar(500),
    "version" varchar(50),
    "config" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "loaded" bool NOT NULL DEFAULT false,
    "load_error" text,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "plugin_instance_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "plugin_instance_name_idx" ON "public"."plugin_instance" USING btree ("name");
CREATE INDEX IF NOT EXISTS "plugin_instance_type_idx" ON "public"."plugin_instance" USING btree ("type");
CREATE INDEX IF NOT EXISTS "plugin_instance_class_name_idx" ON "public"."plugin_instance" USING btree ("class_name");
CREATE INDEX IF NOT EXISTS "plugin_instance_status_idx" ON "public"."plugin_instance" USING btree ("status");
CREATE INDEX IF NOT EXISTS "plugin_instance_loaded_idx" ON "public"."plugin_instance" USING btree ("loaded");
CREATE INDEX IF NOT EXISTS "plugin_instance_tenant_idx" ON "public"."plugin_instance" USING btree ("tenant_id");
