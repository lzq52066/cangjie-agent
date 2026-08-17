-- ----------------------------
-- CangJie Agent V4: 提示词/Skill/记忆/规则/命令模块表结构
-- ----------------------------

-- ----------------------------
-- prompt_template 提示词模板
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."prompt_template" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "category" varchar(100),
    "content" text,
    "description" text,
    "variables" text,
    "is_default" bool NOT NULL DEFAULT false,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "prompt_template_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "prompt_template_name_idx" ON "public"."prompt_template" USING btree ("name");
CREATE INDEX IF NOT EXISTS "prompt_template_category_idx" ON "public"."prompt_template" USING btree ("category");
CREATE INDEX IF NOT EXISTS "prompt_template_default_idx" ON "public"."prompt_template" USING btree ("is_default");
CREATE INDEX IF NOT EXISTS "prompt_template_status_idx" ON "public"."prompt_template" USING btree ("status");
CREATE INDEX IF NOT EXISTS "prompt_template_tenant_idx" ON "public"."prompt_template" USING btree ("tenant_id");

-- ----------------------------
-- skill Skill技能
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."skill" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "type" varchar(50),
    "content" text,
    "function_name" varchar(200),
    "parameters" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "skill_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "skill_name_idx" ON "public"."skill" USING btree ("name");
CREATE INDEX IF NOT EXISTS "skill_type_idx" ON "public"."skill" USING btree ("type");
CREATE INDEX IF NOT EXISTS "skill_function_name_idx" ON "public"."skill" USING btree ("function_name");
CREATE INDEX IF NOT EXISTS "skill_status_idx" ON "public"."skill" USING btree ("status");
CREATE INDEX IF NOT EXISTS "skill_tenant_idx" ON "public"."skill" USING btree ("tenant_id");

-- ----------------------------
-- memory 记忆
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."memory" (
    "id" varchar(50) NOT NULL,
    "application_id" varchar(50),
    "session_id" varchar(50),
    "role" varchar(50),
    "content" text,
    "summary" text,
    "importance" int4 DEFAULT 0,
    "expire_time" timestamp(6),
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "memory_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "memory_application_idx" ON "public"."memory" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "memory_session_idx" ON "public"."memory" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "memory_importance_idx" ON "public"."memory" USING btree ("importance");
CREATE INDEX IF NOT EXISTS "memory_status_idx" ON "public"."memory" USING btree ("status");
CREATE INDEX IF NOT EXISTS "memory_tenant_idx" ON "public"."memory" USING btree ("tenant_id");

-- ----------------------------
-- rule 规则
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."rule" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "type" varchar(50),
    "condition" text,
    "action" text,
    "priority" int4 DEFAULT 0,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "rule_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "rule_name_idx" ON "public"."rule" USING btree ("name");
CREATE INDEX IF NOT EXISTS "rule_type_idx" ON "public"."rule" USING btree ("type");
CREATE INDEX IF NOT EXISTS "rule_priority_idx" ON "public"."rule" USING btree ("priority");
CREATE INDEX IF NOT EXISTS "rule_status_idx" ON "public"."rule" USING btree ("status");
CREATE INDEX IF NOT EXISTS "rule_tenant_idx" ON "public"."rule" USING btree ("tenant_id");

-- ----------------------------
-- command 命令
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."command" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "command" varchar(200),
    "description" text,
    "type" varchar(50),
    "script" text,
    "parameters" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "command_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "command_name_idx" ON "public"."command" USING btree ("name");
CREATE INDEX IF NOT EXISTS "command_command_idx" ON "public"."command" USING btree ("command");
CREATE INDEX IF NOT EXISTS "command_type_idx" ON "public"."command" USING btree ("type");
CREATE INDEX IF NOT EXISTS "command_status_idx" ON "public"."command" USING btree ("status");
CREATE INDEX IF NOT EXISTS "command_tenant_idx" ON "public"."command" USING btree ("tenant_id");
