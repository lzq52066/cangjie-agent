-- ----------------------------
-- CangJie Agent V115: 检索载荷留痕 + 模型参数留痕 + 评估结果拆表
--
-- 三件事都为「RAG 问题可诊断、评估指标可对比」服务：
--
-- 1) trace_record 补载荷列。检索 span 原先只有一句「知识库检索: 5 条结果」，
--    用的什么 query、改写成什么、召回了哪些片段、各通道分数多少、重排前后名次如何排名，
--    全都查不到 —— 出问题时无法复现，等于没有留痕。这里补 input/output/metadata 三列，
--    把一次检索变成自证往返的完整证据（参照 LLM 调用链路的业界常见做法）。
--
-- 2) llm_trace 补 model_params。同一条 trace 里看不出这次调用用的是多少 temperature / topP /
--    maxTokens，调参与效果对不上号。
--
-- 3) 新建 eval_result 明细表。逐 case 结果此前整包塞进 eval_run.results 一个 text 字段，
--    每跑完一个 case 都要重写整行（越大越慢，呈平方级膨胀），且跨 run 的指标趋势 /
--    同用例回归对比都得先把 JSON 捞回内存再拆。改为一条 case 一行、只 insert 不 update，
--    已知指标（recall / correctness）用真实数值列便于 SQL 聚合，
--    后续新增指标先落 extra_scores，稳定后再提升为列。
-- ----------------------------

-- ==================== 1. span 载荷 ====================

ALTER TABLE "public"."trace_record" ADD COLUMN IF NOT EXISTS "input_payload" text;
ALTER TABLE "public"."trace_record" ADD COLUMN IF NOT EXISTS "output_payload" text;
ALTER TABLE "public"."trace_record" ADD COLUMN IF NOT EXISTS "metadata" text;

COMMENT ON COLUMN "public"."trace_record"."input_payload" IS '节点输入载荷（JSON）：如检索的 query / 改写词 / topK / 检索模式';
COMMENT ON COLUMN "public"."trace_record"."output_payload" IS '节点输出载荷（JSON）：如召回片段的各通道分数与重排前后名次';
COMMENT ON COLUMN "public"."trace_record"."metadata" IS '节点附加维度（JSON）：如候选数、命中数、耗时分解';

-- ==================== 2. 模型调用参数 ====================

ALTER TABLE "public"."llm_trace" ADD COLUMN IF NOT EXISTS "model_params" text;

COMMENT ON COLUMN "public"."llm_trace"."model_params" IS '本次调用参数（JSON：temperature / topP / maxTokens）';

-- ==================== 3. 评估结果明细 ====================

CREATE TABLE IF NOT EXISTS "public"."eval_result" (
    "id" varchar(50) NOT NULL,
    "run_id" varchar(50) NOT NULL,
    "case_id" varchar(50) NOT NULL,
    "dataset_id" varchar(50),
    "question" text,
    "answer" text,
    "contexts" text,
    "recall" numeric(6,4),
    "correctness" numeric(6,4),
    "extra_scores" text,
    "detail" text,
    "latency" int8,
    "input_tokens" int8,
    "output_tokens" int8,
    "total_tokens" int8,
    "status" varchar(20) NOT NULL DEFAULT 'success',
    "error" text,
    "trace_id" varchar(64),
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "eval_result_pkey" PRIMARY KEY ("id")
);

COMMENT ON COLUMN "public"."eval_result"."run_id" IS '所属评估运行 ID';
COMMENT ON COLUMN "public"."eval_result"."case_id" IS '评估用例 ID；跨 run 的同用例回归对比靠它对齐';
COMMENT ON COLUMN "public"."eval_result"."contexts" IS '本次检索片段（JSON：paragraphId / documentId / 各通道分数 / 名次）';
COMMENT ON COLUMN "public"."eval_result"."recall" IS '召回率；null 表示该指标本次未评测，不能当 0 计入均值';
COMMENT ON COLUMN "public"."eval_result"."correctness" IS 'LLM 判定正确性 0~1；null 表示未评测或判定失败';
COMMENT ON COLUMN "public"."eval_result"."extra_scores" IS '其余指标分值（JSON map），新增指标无需改表';
COMMENT ON COLUMN "public"."eval_result"."detail" IS '判定依据与中间量（JSON：judge 理由 / 命中文档 / 缺失文档）';
COMMENT ON COLUMN "public"."eval_result"."trace_id" IS '本 case 模型调用的链路 ID，报告可一键跳到链路视图';
COMMENT ON COLUMN "public"."eval_result"."status" IS 'success / partial（部分指标未出分）/ fail';

-- 一次运行的全部结果（报告页主查询路径）
CREATE INDEX IF NOT EXISTS "eval_result_run_idx" ON "public"."eval_result" USING btree ("run_id", "case_id");
-- 跨 run 看同一用例的指标走势
CREATE INDEX IF NOT EXISTS "eval_result_case_idx" ON "public"."eval_result" USING btree ("case_id");
-- 按数据集聚合趋势时先裁剪扫描量
CREATE INDEX IF NOT EXISTS "eval_result_dataset_idx" ON "public"."eval_result" USING btree ("dataset_id", "create_time" DESC);
