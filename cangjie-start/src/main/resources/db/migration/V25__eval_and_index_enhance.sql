-- ----------------------------
-- V25: 评估体系 + 多粒度索引 + Agentic RAG
-- ----------------------------

-- ==================== 多粒度索引 ====================

-- knowledge_document 增加文档摘要及摘要向量列
ALTER TABLE "public"."knowledge_document"
    ADD COLUMN IF NOT EXISTS "summary" TEXT;
ALTER TABLE "public"."knowledge_document"
    ADD COLUMN IF NOT EXISTS "summary_embedding" vector(1024);

COMMENT ON COLUMN "public"."knowledge_document"."summary" IS '文档摘要（LLM 生成，用于 two-stage 检索第一阶段）';
COMMENT ON COLUMN "public"."knowledge_document"."summary_embedding" IS '文档摘要的向量表示';

-- knowledge_document 摘要向量索引
CREATE INDEX IF NOT EXISTS "knowledge_document_summary_embedding_idx"
    ON "public"."knowledge_document" USING ivfflat ("summary_embedding" vector_cosine_ops) WITH (lists = 50);

-- knowledge_base 增加检索模式
ALTER TABLE "public"."knowledge_base"
    ADD COLUMN IF NOT EXISTS "search_mode" VARCHAR(20) NOT NULL DEFAULT 'simple';

COMMENT ON COLUMN "public"."knowledge_base"."search_mode" IS '检索模式：simple（段落级）/ two_stage（摘要→段落两级）';

-- ==================== Agentic RAG ====================

-- application 增加 RAG 模式
ALTER TABLE "public"."application"
    ADD COLUMN IF NOT EXISTS "rag_mode" VARCHAR(20) NOT NULL DEFAULT 'simple';

COMMENT ON COLUMN "public"."application"."rag_mode" IS 'RAG 模式：simple（预处理注入）/ agentic（LLM 自主检索）';

-- ==================== 评估体系 ====================

-- eval_dataset 评估数据集
CREATE TABLE IF NOT EXISTS "public"."eval_dataset" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "knowledge_base_id" varchar(50),
    "case_count" int4 NOT NULL DEFAULT 0,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "eval_dataset_pkey" PRIMARY KEY ("id")
);

-- eval_case 评估用例
CREATE TABLE IF NOT EXISTS "public"."eval_case" (
    "id" varchar(50) NOT NULL,
    "dataset_id" varchar(50) NOT NULL,
    "question" text NOT NULL,
    "expected_answer" text,
    "reference_docs" text,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "eval_case_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "eval_case_dataset_idx" ON "public"."eval_case" USING btree ("dataset_id");

-- eval_run 评估运行记录
CREATE TABLE IF NOT EXISTS "public"."eval_run" (
    "id" varchar(50) NOT NULL,
    "dataset_id" varchar(50) NOT NULL,
    "config_snapshot" text,
    "status" varchar(20) NOT NULL DEFAULT 'pending',
    "start_time" timestamp(6),
    "end_time" timestamp(6),
    "summary" text,
    "results" text,
    "error_message" text,
    "progress" int4 NOT NULL DEFAULT 0,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "eval_run_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "eval_run_dataset_idx" ON "public"."eval_run" USING btree ("dataset_id");