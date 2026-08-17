-- ----------------------------
-- CangJie Agent V3: 模型配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."model_config" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "model_type" varchar(20) NOT NULL,
    "api_key" varchar(500),
    "base_url" varchar(500),
    "model_name" varchar(200) NOT NULL,
    "temperature" float8 DEFAULT 0.7,
    "max_tokens" int4 DEFAULT 4096,
    "top_p" float8 DEFAULT 1.0,
    "is_default" bool NOT NULL DEFAULT false,
    "support_embedding" bool NOT NULL DEFAULT false,
    "embedding_dimension" int4,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "description" text,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "model_config_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "model_config_type_idx" ON "public"."model_config" USING btree ("model_type");
CREATE INDEX IF NOT EXISTS "model_config_default_idx" ON "public"."model_config" USING btree ("is_default");
CREATE INDEX IF NOT EXISTS "model_config_status_idx" ON "public"."model_config" USING btree ("status");
