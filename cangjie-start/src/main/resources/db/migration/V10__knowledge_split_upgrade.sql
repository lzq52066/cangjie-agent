-- 知识库切片智能化改造：移除 chunk_overlap，新增 separators
ALTER TABLE "public"."knowledge_base"
    DROP COLUMN IF EXISTS "chunk_overlap",
    ADD COLUMN IF NOT EXISTS "separators" text;

-- 默认策略改为 smart
UPDATE "public"."knowledge_base" SET "split_strategy" = 'smart' WHERE "split_strategy" IN ('sentence', 'structural', 'token');
ALTER TABLE "public"."knowledge_base" ALTER COLUMN "split_strategy" SET DEFAULT 'smart';
