-- V118: 评测用例携带「这一轮之前的对话」
-- 线上回流的问答对只是会话的最后一轮：多轮追问里单独一句「那第二个呢」既读不出语境，
-- 也没法在离线评测里重现当时的回答。补一列上文，跑评测时连同历史一起喂给模型。
ALTER TABLE "public"."eval_case" ADD COLUMN IF NOT EXISTS "conversation" text;

COMMENT ON COLUMN "public"."eval_case"."conversation" IS '本轮之前的对话（JSON 数组 [{"role":"user","content":"..."}]），多轮追问的上文';
