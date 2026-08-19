-- 应用版本管理表
CREATE TABLE IF NOT EXISTS "application_version" (
    "id" VARCHAR(36) NOT NULL,
    "application_id" VARCHAR(36) NOT NULL,
    "version" INT NOT NULL DEFAULT 1,
    "name" VARCHAR(255),
    "description" TEXT,
    "type" VARCHAR(32),
    "model_id" VARCHAR(36),
    "knowledge_base_ids" TEXT,
    "prompt_template_id" VARCHAR(36),
    "skill_ids" TEXT,
    "rule_ids" TEXT,
    "memory_enabled" BOOLEAN DEFAULT FALSE,
    "max_turns" INT DEFAULT 20,
    "temperature" DOUBLE PRECISION DEFAULT 0.7,
    "config" TEXT,
    "suggestions" TEXT,
    "icon" VARCHAR(255),
    "snapshot" TEXT NOT NULL,
    "publish_log" TEXT,
    "publish_by" VARCHAR(255),
    "tenant_id" VARCHAR(50) NOT NULL DEFAULT 'default',
    "create_by" VARCHAR(50),
    "update_by" VARCHAR(50),
    "create_time" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" INT DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "idx_app_version_app_id" ON "application_version" ("application_id");
CREATE INDEX IF NOT EXISTS "idx_app_version_create_time" ON "application_version" ("create_time");

COMMENT ON TABLE "application_version" IS '应用版本快照（支持版本回滚）';
COMMENT ON COLUMN "application_version"."version" IS '版本号，从1递增';
COMMENT ON COLUMN "application_version"."snapshot" IS '发布时完整应用配置快照（JSON）';
COMMENT ON COLUMN "application_version"."publish_log" IS '发布说明';
COMMENT ON COLUMN "application_version"."publish_by" IS '发布人';