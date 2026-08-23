-- ----------------------------
-- V27: 全文检索引擎从 tsvector 迁移到 pgroonga
-- 前置条件：DBA 已安装并初始化 pgroonga 扩展
-- ----------------------------

-- 1. 启用 pgroonga 扩展
CREATE EXTENSION IF NOT EXISTS pgroonga;

-- 2. 在 content 列上创建 pgroonga 索引（替代原来的 ts_vector GIN 索引）
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_content_pgroonga_idx"
    ON "public"."knowledge_paragraph" USING pgroonga ("content");

-- 3. 删除旧的 tsvector GIN 索引
DROP INDEX IF EXISTS "public"."knowledge_paragraph_ts_vector_idx";

-- 4. 删除 ts_vector 列（pgroonga 直接索引 content，不再需要此列）
ALTER TABLE "public"."knowledge_paragraph" DROP COLUMN IF EXISTS "ts_vector";