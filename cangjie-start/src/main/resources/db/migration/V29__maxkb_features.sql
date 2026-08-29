-- ----------------------------
-- CangJie Agent V29: 企业级能力补全（参考 MaxKB）
-- 1. 对话消息反馈与人工标注
-- 2. 知识库问题管理与问题-段落关联
-- ----------------------------

-- 1. 对话消息：反馈（点赞/点踩）与人工标注（修正答案）
ALTER TABLE "public"."chat_message" ADD COLUMN IF NOT EXISTS "feedback" varchar(20) NOT NULL DEFAULT 'none';
ALTER TABLE "public"."chat_message" ADD COLUMN IF NOT EXISTS "annotation" text;
ALTER TABLE "public"."chat_message" ADD COLUMN IF NOT EXISTS "annotate_by" varchar(50);
ALTER TABLE "public"."chat_message" ADD COLUMN IF NOT EXISTS "annotate_time" timestamp(6);
COMMENT ON COLUMN "public"."chat_message"."feedback" IS '用户反馈：none / like / dislike';
COMMENT ON COLUMN "public"."chat_message"."annotation" IS '人工标注的修正答案';

-- 2. 知识库问题：一个段落可关联多个常见问题，检索时走问题路召回
CREATE TABLE IF NOT EXISTS "public"."knowledge_problem" (
    "id" varchar(50) NOT NULL,
    "knowledge_base_id" varchar(50) NOT NULL,
    "content" varchar(1000) NOT NULL,
    "source" varchar(20) NOT NULL DEFAULT 'manual',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "knowledge_problem_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "knowledge_problem_kb_idx" ON "public"."knowledge_problem" USING btree ("knowledge_base_id");

-- 3. 问题-段落关联
CREATE TABLE IF NOT EXISTS "public"."problem_paragraph" (
    "id" varchar(50) NOT NULL,
    "problem_id" varchar(50) NOT NULL,
    "paragraph_id" varchar(50) NOT NULL,
    "knowledge_base_id" varchar(50) NOT NULL,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "problem_paragraph_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "problem_paragraph_problem_idx" ON "public"."problem_paragraph" USING btree ("problem_id");
CREATE INDEX IF NOT EXISTS "problem_paragraph_paragraph_idx" ON "public"."problem_paragraph" USING btree ("paragraph_id");
CREATE INDEX IF NOT EXISTS "problem_paragraph_kb_idx" ON "public"."problem_paragraph" USING btree ("knowledge_base_id");
