-- ----------------------------
-- CangJie Agent V8: 渠道接入（trigger）模块表结构
-- ----------------------------

-- ----------------------------
-- channel 渠道配置
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."channel" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "type" varchar(20) NOT NULL,
    "application_id" varchar(50),
    "app_id" varchar(100),
    "app_secret" varchar(200),
    "token" varchar(200),
    "encoding_aes_key" varchar(100),
    "verify_token" varchar(200),
    "config" text,
    "status" varchar(20) NOT NULL DEFAULT 'inactive',
    "last_call_time" timestamp(6),
    "call_count" int4 NOT NULL DEFAULT 0,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "channel_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "channel_type_idx" ON "public"."channel" USING btree ("type");
CREATE INDEX IF NOT EXISTS "channel_application_id_idx" ON "public"."channel" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "channel_status_idx" ON "public"."channel" USING btree ("status");
CREATE INDEX IF NOT EXISTS "channel_tenant_idx" ON "public"."channel" USING btree ("tenant_id");

-- ----------------------------
-- channel_message 渠道消息记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."channel_message" (
    "id" varchar(50) NOT NULL,
    "channel_id" varchar(50) NOT NULL,
    "channel_type" varchar(20),
    "application_id" varchar(50),
    "open_id" varchar(100),
    "session_id" varchar(100),
    "msg_type" varchar(20),
    "content" text,
    "event_type" varchar(50),
    "reply_content" text,
    "status" varchar(20) NOT NULL DEFAULT 'processed',
    "error_message" text,
    "cost_time" int8,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "channel_message_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "channel_message_channel_id_idx" ON "public"."channel_message" USING btree ("channel_id");
CREATE INDEX IF NOT EXISTS "channel_message_open_id_idx" ON "public"."channel_message" USING btree ("open_id");
CREATE INDEX IF NOT EXISTS "channel_message_application_id_idx" ON "public"."channel_message" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "channel_message_session_id_idx" ON "public"."channel_message" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "channel_message_create_time_idx" ON "public"."channel_message" USING btree ("create_time");
CREATE INDEX IF NOT EXISTS "channel_message_tenant_idx" ON "public"."channel_message" USING btree ("tenant_id");
