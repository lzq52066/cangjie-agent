-- ----------------------------
-- CangJie Agent V7: 智能应用 + 对话模块表结构
-- ----------------------------

-- ----------------------------
-- application 智能应用
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."application" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "type" varchar(20) NOT NULL DEFAULT 'chat',
    "model_id" varchar(50),
    "knowledge_base_ids" text,
    "prompt_template_id" varchar(50),
    "skill_ids" text,
    "rule_ids" text,
    "memory_enabled" bool NOT NULL DEFAULT false,
    "max_turns" int4 NOT NULL DEFAULT 20,
    "temperature" numeric(3,2) DEFAULT 0.70,
    "config" text,
    "icon" varchar(500),
    "status" varchar(20) NOT NULL DEFAULT 'draft',
    "apikey" varchar(100),
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "application_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "application_name_idx" ON "public"."application" USING btree ("name");
CREATE INDEX IF NOT EXISTS "application_type_idx" ON "public"."application" USING btree ("type");
CREATE INDEX IF NOT EXISTS "application_status_idx" ON "public"."application" USING btree ("status");
CREATE INDEX IF NOT EXISTS "application_apikey_idx" ON "public"."application" USING btree ("apikey");
CREATE INDEX IF NOT EXISTS "application_model_id_idx" ON "public"."application" USING btree ("model_id");
CREATE INDEX IF NOT EXISTS "application_tenant_idx" ON "public"."application" USING btree ("tenant_id");

-- ----------------------------
-- chat_session 对话会话
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."chat_session" (
    "id" varchar(50) NOT NULL,
    "application_id" varchar(50),
    "session_id" varchar(100) NOT NULL,
    "title" varchar(500),
    "user_id" varchar(50),
    "source" varchar(20) DEFAULT 'api',
    "model_id" varchar(50),
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "message_count" int4 NOT NULL DEFAULT 0,
    "tokens_used" int4 NOT NULL DEFAULT 0,
    "metadata" text,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "chat_session_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "chat_session_application_id_idx" ON "public"."chat_session" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "chat_session_session_id_idx" ON "public"."chat_session" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "chat_session_user_id_idx" ON "public"."chat_session" USING btree ("user_id");
CREATE INDEX IF NOT EXISTS "chat_session_status_idx" ON "public"."chat_session" USING btree ("status");
CREATE INDEX IF NOT EXISTS "chat_session_create_time_idx" ON "public"."chat_session" USING btree ("create_time" DESC);
CREATE INDEX IF NOT EXISTS "chat_session_tenant_idx" ON "public"."chat_session" USING btree ("tenant_id");

-- ----------------------------
-- chat_message 对话消息
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."chat_message" (
    "id" varchar(50) NOT NULL,
    "session_id" varchar(100) NOT NULL,
    "application_id" varchar(50),
    "role" varchar(20) NOT NULL,
    "content" text,
    "tokens" int4,
    "retrieval_sources" text,
    "metadata" text,
    "duration" int8,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "chat_message_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "chat_message_session_id_idx" ON "public"."chat_message" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "chat_message_application_id_idx" ON "public"."chat_message" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "chat_message_role_idx" ON "public"."chat_message" USING btree ("role");
CREATE INDEX IF NOT EXISTS "chat_message_create_time_idx" ON "public"."chat_message" USING btree ("create_time");
CREATE INDEX IF NOT EXISTS "chat_message_tenant_idx" ON "public"."chat_message" USING btree ("tenant_id");
