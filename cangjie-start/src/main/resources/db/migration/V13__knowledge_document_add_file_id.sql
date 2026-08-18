-- ----------------------------
-- V13: knowledge_document 增加 file_id 列，关联 file_record
-- ----------------------------
ALTER TABLE "public"."knowledge_document"
    ADD COLUMN IF NOT EXISTS "file_id" varchar(50);

COMMENT ON COLUMN "public"."knowledge_document"."file_id" IS '关联的文件管理记录 ID（file_record.id）';