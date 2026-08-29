-- ----------------------------
-- CangJie Agent V30: 记忆体系增强
-- 1. 会话记忆：会话滚动摘要
-- 2. 场景记忆：长期记忆区分用户画像/场景事实，场景记忆绑定会话
-- ----------------------------

-- 1. 会话摘要（滚动更新，历史加载时注入）
ALTER TABLE "public"."chat_session" ADD COLUMN IF NOT EXISTS "summary" text;
ALTER TABLE "public"."chat_session" ADD COLUMN IF NOT EXISTS "summary_msg_count" int4 NOT NULL DEFAULT 0;
COMMENT ON COLUMN "public"."chat_session"."summary" IS '会话滚动摘要（覆盖已摘要的历史消息）';
COMMENT ON COLUMN "public"."chat_session"."summary_msg_count" IS '已纳入摘要的消息数';

-- 2. 长期记忆扩展：记忆类型 + 场景（会话）绑定
ALTER TABLE "public"."long_term_memory" ADD COLUMN IF NOT EXISTS "memory_type" varchar(20) NOT NULL DEFAULT 'user';
ALTER TABLE "public"."long_term_memory" ADD COLUMN IF NOT EXISTS "session_id" varchar(50);
COMMENT ON COLUMN "public"."long_term_memory"."memory_type" IS '记忆类型：user 用户画像（跨会话）/ scene 场景事实（会话内）';
COMMENT ON COLUMN "public"."long_term_memory"."session_id" IS '场景记忆绑定的会话 ID';
CREATE INDEX IF NOT EXISTS "idx_ltm_session" ON "public"."long_term_memory" ("session_id");
CREATE INDEX IF NOT EXISTS "idx_ltm_type" ON "public"."long_term_memory" ("memory_type");
