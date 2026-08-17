-- ----------------------------
-- CangJie Agent V2: 知识库模块表结构
-- ----------------------------

-- ----------------------------
-- knowledge_base 知识库
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."knowledge_base" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "split_strategy" varchar(20) NOT NULL DEFAULT 'sentence',
    "chunk_size" int4 NOT NULL DEFAULT 500,
    "chunk_overlap" int4 NOT NULL DEFAULT 50,
    "embedding_model_id" varchar(100),
    "embedding_dimension" int4 DEFAULT 1536,
    "document_count" int4 NOT NULL DEFAULT 0,
    "paragraph_count" int4 NOT NULL DEFAULT 0,
    "directory" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "knowledge_base_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "knowledge_base_name_idx" ON "public"."knowledge_base" USING btree ("name");
CREATE INDEX IF NOT EXISTS "knowledge_base_tenant_idx" ON "public"."knowledge_base" USING btree ("tenant_id");

-- ----------------------------
-- knowledge_document 知识库文档
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."knowledge_document" (
    "id" varchar(50) NOT NULL,
    "knowledge_base_id" varchar(50) NOT NULL,
    "name" varchar(500) NOT NULL,
    "file_type" varchar(20),
    "file_size" int8,
    "file_path" varchar(1000),
    "file_md5" varchar(64),
    "status" varchar(20) NOT NULL DEFAULT 'pending',
    "process_message" text,
    "paragraph_count" int4 DEFAULT 0,
    "token_count" int4 DEFAULT 0,
    "title" varchar(500),
    "directory_path" varchar(500),
    "metadata" text,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "knowledge_document_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "knowledge_document_kb_idx" ON "public"."knowledge_document" USING btree ("knowledge_base_id");
CREATE INDEX IF NOT EXISTS "knowledge_document_md5_idx" ON "public"."knowledge_document" USING btree ("knowledge_base_id", "file_md5");

-- ----------------------------
-- knowledge_paragraph 知识库段落（含向量列 + 全文检索列）
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."knowledge_paragraph" (
    "id" varchar(50) NOT NULL,
    "knowledge_base_id" varchar(50) NOT NULL,
    "document_id" varchar(50) NOT NULL,
    "title" varchar(500),
    "content" text NOT NULL,
    "chunk_index" int4 NOT NULL DEFAULT 0,
    "page_number" int4,
    "token_count" int4 DEFAULT 0,
    "char_count" int4 DEFAULT 0,
    "vector_status" varchar(20) NOT NULL DEFAULT 'pending',
    "metadata" text,
    "embedding" vector,
    "ts_vector" tsvector,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "knowledge_paragraph_pkey" PRIMARY KEY ("id")
);

-- 向量索引（IVFFlat，适合中等规模数据；大数据量可换 HNSW）
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_embedding_idx"
    ON "public"."knowledge_paragraph" USING ivfflat ("embedding" vector_cosine_ops) WITH (lists = 100);

-- 全文检索索引
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_ts_vector_idx"
    ON "public"."knowledge_paragraph" USING gin ("ts_vector");

-- 普通索引
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_kb_idx" ON "public"."knowledge_paragraph" USING btree ("knowledge_base_id");
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_doc_idx" ON "public"."knowledge_paragraph" USING btree ("document_id");
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_status_idx" ON "public"."knowledge_paragraph" USING btree ("vector_status");
