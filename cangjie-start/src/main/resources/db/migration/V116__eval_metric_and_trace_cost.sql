-- ----------------------------
-- CangJie Agent V116: RAGAS 指标地基 + 坏 case 回流 + 成本/首字延迟可观测
--
-- 这轮迁移支撑三件事，都是「指标能算出来」的前置条件：
--
-- 1) eval_case 补 reference_chunks（片段级 ground truth）。
--    此前只有 reference_docs（文档级），文档命中但片段没命中也算满分，把 chunk 切分
--    与 topK 的问题全都掩盖掉了；context precision / context recall（片段口径）
--    没有 reference_chunks 就根本算不出来。顺带补 tags / difficulty / source /
--    source_message_id：坏 case 从对话回流进数据集时要靠 source_message_id 幂等去重，
--    靠 source 区分手工用例与回流用例。
--
-- 2) llm_trace 补 ttft / cost / evaluated / eval_scores。
--    duration 在流式场景是「整段生成时间」，而用户体感是首字延迟，必须单列；
--    token→钱 的归集靠 model_config 单价（第 3 点）算出 cost 落在这里；
--    evaluated / eval_scores 给在线评测回填，采样打分结果不另起一张表，
--    按 traceId 就地更新，趋势查询直接聚合本表。
--
-- 3) model_config 补 input_price / output_price（元/千 token）。
--    没有单价，看板上的 token 数就只是数字，回答不了「这次评测/这轮对话花了多少钱」。
--
-- 另外：promptKeyword / responseKeyword 是 LIKE '%…%' 全表扫描，需要 pg_trgm GIN 索引。
-- 这里只补扩展、不建索引：迁移跑在事务里，而事务内不允许 CREATE INDEX CONCURRENTLY；
-- 不带 CONCURRENTLY 建则持 SHARE 锁，大表上等于在升级期间把对话写入整段停掉。
-- 索引由 observability 的 LlmTraceIndexBootstrap 在启动完成后的后台线程 CONCURRENTLY 建，
-- 建不上也只影响关键词检索速度，不影响功能。
-- ----------------------------

-- ==================== 1. 用例：片段级 ground truth 与来源 =====

ALTER TABLE "public"."eval_case" ADD COLUMN IF NOT EXISTS "reference_chunks" text;
ALTER TABLE "public"."eval_case" ADD COLUMN IF NOT EXISTS "tags" varchar(255);
ALTER TABLE "public"."eval_case" ADD COLUMN IF NOT EXISTS "difficulty" varchar(20);
ALTER TABLE "public"."eval_case" ADD COLUMN IF NOT EXISTS "source" varchar(20) NOT NULL DEFAULT 'manual';
ALTER TABLE "public"."eval_case" ADD COLUMN IF NOT EXISTS "source_message_id" varchar(50);

COMMENT ON COLUMN "public"."eval_case"."reference_chunks" IS '参考片段 ID 列表（JSON 数组）：片段级 ground truth，context precision / 片段口径 recall 靠它';
COMMENT ON COLUMN "public"."eval_case"."tags" IS '标签（逗号分隔），用于按主题切片看指标';
COMMENT ON COLUMN "public"."eval_case"."difficulty" IS '难度：easy / medium / hard，可空';
COMMENT ON COLUMN "public"."eval_case"."source" IS '用例来源：manual 手工 / trace 从对话回流 / synthetic 合成';
COMMENT ON COLUMN "public"."eval_case"."source_message_id" IS '回流来源消息 ID：同一条 AI 消息重复点「加入数据集」时靠它幂等跳过';

-- 回流去重与「按来源筛用例」
CREATE INDEX IF NOT EXISTS "eval_case_source_msg_idx" ON "public"."eval_case" USING btree ("source_message_id");

-- ==================== 2. 模型调用：首字延迟 / 成本 / 在线评测回填 ====================

ALTER TABLE "public"."llm_trace" ADD COLUMN IF NOT EXISTS "ttft" int8;
ALTER TABLE "public"."llm_trace" ADD COLUMN IF NOT EXISTS "cost" numeric(12,6);
ALTER TABLE "public"."llm_trace" ADD COLUMN IF NOT EXISTS "evaluated" bool NOT NULL DEFAULT false;
ALTER TABLE "public"."llm_trace" ADD COLUMN IF NOT EXISTS "eval_scores" text;

COMMENT ON COLUMN "public"."llm_trace"."ttft" IS '首字延迟（毫秒）：流式场景 duration 是整段生成时间，体感看的是这个';
COMMENT ON COLUMN "public"."llm_trace"."cost" IS '本次调用成本（元），按 model_config 单价 × token 数算；缺单价则为空';
COMMENT ON COLUMN "public"."llm_trace"."evaluated" IS '是否已被在线评测打过标';
COMMENT ON COLUMN "public"."llm_trace"."eval_scores" IS '在线评测得分（JSON：faithfulness / answerRelevancy ...），趋势按此列聚合';

-- 在线评测任务捞「未打分样本」时的过滤路径
CREATE INDEX IF NOT EXISTS "llm_trace_evaluated_idx" ON "public"."llm_trace" USING btree ("evaluated", "start_time" DESC);

-- ==================== 3. 模型单价 ====================

ALTER TABLE "public"."model_config" ADD COLUMN IF NOT EXISTS "input_price" numeric(12,6);
ALTER TABLE "public"."model_config" ADD COLUMN IF NOT EXISTS "output_price" numeric(12,6);

COMMENT ON COLUMN "public"."model_config"."input_price" IS '输入单价（元 / 千 token），未填则成本不估算';
COMMENT ON COLUMN "public"."model_config"."output_price" IS '输出单价（元 / 千 token），未填则成本不估算';

-- ==================== 4. 正文关键词检索：只补 pg_trgm 扩展 ====================

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION WHEN OTHERS THEN
    -- 精简镜像里没有 pg_trgm 共享库：跳过，SQL 本身仍可跑（只是回到顺序扫描）
    RAISE NOTICE 'pg_trgm 扩展跳过: %', SQLERRM;
END $$;

-- GIN 索引不在这里建，见文件头说明（事务内无法 CONCURRENTLY，普通建法会锁写）。
-- 已被更早版本建过索引的库不受影响：bootstrap 用的是 IF NOT EXISTS。
