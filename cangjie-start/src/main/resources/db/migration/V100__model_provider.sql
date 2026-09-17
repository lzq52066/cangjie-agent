-- ----------------------------
-- 版本号从 V100 起跳：V1__init.sql 由历史 V1~V32 整合而成，存量库的
-- flyway_schema_history 已占用 1~32，复用低位版本号会被 Flyway 判定为已执行而跳过。
-- 后续新增迁移请从 V105 起递增，且不要再修改 V1__init.sql。
-- ----------------------------

-- ----------------------------
-- CangJie Agent V100: 厂商（Provider）配置
-- 将原先硬编码在前后端的「模型类型」升级为界面可维护的厂商记录：
-- 厂商统一持有 API Key / Base URL，模型通过 provider_id 关联厂商，
-- 同一厂商的凭证只需维护一份（凭证列的移除见 V101）。
-- ----------------------------

CREATE TABLE IF NOT EXISTS "public"."model_provider" (
    "id" varchar(50) NOT NULL,
    "name" varchar(100) NOT NULL,
    "code" varchar(20) NOT NULL,
    "base_url" varchar(500),
    "api_key" varchar(500),
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "description" text,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "model_provider_pkey" PRIMARY KEY ("id")
);
COMMENT ON COLUMN "public"."model_provider"."code" IS '厂商标识，与 model_config.model_type 对应，如 openai / deepseek / qwen';
COMMENT ON COLUMN "public"."model_provider"."base_url" IS '厂商 API Base URL，该厂商下所有模型共用';
COMMENT ON COLUMN "public"."model_provider"."api_key" IS '厂商 API Key（AES 密文），该厂商下所有模型共用';
COMMENT ON COLUMN "public"."model_provider"."status" IS '状态：active / inactive';
CREATE INDEX IF NOT EXISTS "model_provider_code_idx" ON "public"."model_provider" USING btree ("code");
CREATE INDEX IF NOT EXISTS "model_provider_status_idx" ON "public"."model_provider" USING btree ("status");

-- 模型关联厂商
ALTER TABLE "public"."model_config" ADD COLUMN IF NOT EXISTS "provider_id" varchar(50);
COMMENT ON COLUMN "public"."model_config"."provider_id" IS '关联厂商 ID，API Key / Base URL 全部来自厂商';
CREATE INDEX IF NOT EXISTS "model_config_provider_idx" ON "public"."model_config" USING btree ("provider_id");

-- ----------------------------
-- 内置厂商种子数据：凭证留空，由使用方在「模型管理 - 厂商管理」界面填写一次即可
-- ----------------------------
INSERT INTO "public"."model_provider"
    ("id", "name", "code", "base_url", "api_key", "status", "description", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES
    ('prov_openai',   'OpenAI',        'openai',   'https://api.openai.com/v1',                         NULL, 'active', '内置厂商', 'system', 'system', now(), now(), 0),
    ('prov_deepseek', 'DeepSeek',      'deepseek', 'https://api.deepseek.com/v1',                       NULL, 'active', '内置厂商', 'system', 'system', now(), now(), 0),
    ('prov_qwen',     '通义千问',       'qwen',     'https://dashscope.aliyuncs.com/compatible-mode/v1', NULL, 'active', '内置厂商', 'system', 'system', now(), now(), 0),
    ('prov_zhipu',    '智谱清言',       'zhipu',    'https://open.bigmodel.cn/api/paas/v4',              NULL, 'active', '内置厂商', 'system', 'system', now(), now(), 0),
    ('prov_wenxin',   '文心一言',       'wenxin',   'https://qianfan.baidubce.com/v2',                   NULL, 'active', '内置厂商', 'system', 'system', now(), now(), 0),
    ('prov_ollama',   'Ollama',        'ollama',   'http://localhost:11434/v1',                         NULL, 'active', '内置厂商', 'system', 'system', now(), now(), 0),
    ('prov_custom',   '自定义',         'custom',   NULL,                                                NULL, 'active', '内置厂商', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;
