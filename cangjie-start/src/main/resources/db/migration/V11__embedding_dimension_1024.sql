-- 将 embedding 列维度从 1536 改为 1024（适配 BAAI/bge-m3 模型）
-- 先删除旧索引，修改列类型，再重建索引
DROP INDEX IF EXISTS "knowledge_paragraph_embedding_idx";

ALTER TABLE "knowledge_paragraph" ALTER COLUMN "embedding" TYPE vector(1024);

-- 重建向量索引
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_embedding_idx"
    ON "public"."knowledge_paragraph" USING ivfflat ("embedding" vector_cosine_ops) WITH (lists = 100);
