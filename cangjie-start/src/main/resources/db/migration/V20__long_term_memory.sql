-- 长期记忆表
CREATE TABLE IF NOT EXISTS "long_term_memory" (
    "id" VARCHAR(36) NOT NULL,
    "user_id" VARCHAR(255) NOT NULL,
    "application_id" VARCHAR(36) NOT NULL,
    "dimension" VARCHAR(32) NOT NULL,
    "content" TEXT NOT NULL,
    "confidence" DOUBLE PRECISION DEFAULT 0.8,
    "source" VARCHAR(64) DEFAULT 'inferred',
    "trigger_count" INT DEFAULT 0,
    "last_triggered_at" TIMESTAMP,
    "is_active" BOOLEAN DEFAULT TRUE,
    "tenant_id" VARCHAR(50) NOT NULL DEFAULT 'default',
    "create_by" VARCHAR(50),
    "update_by" VARCHAR(50),
    "create_time" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" INT DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "idx_ltm_user_app" ON "long_term_memory" ("user_id", "application_id");
CREATE INDEX IF NOT EXISTS "idx_ltm_dimension" ON "long_term_memory" ("dimension");
CREATE INDEX IF NOT EXISTS "idx_ltm_is_active" ON "long_term_memory" ("is_active");

COMMENT ON TABLE "long_term_memory" IS '长期记忆（基于用户+应用维度的画像）';
COMMENT ON COLUMN "long_term_memory"."dimension" IS '记忆维度：preference/background/convention/goal';
COMMENT ON COLUMN "long_term_memory"."source" IS '记忆来源：inferred 推断 / explicit 显式 / import 导入';