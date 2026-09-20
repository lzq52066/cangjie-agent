-- ----------------------------
-- CangJie Agent V113: LLM 调用明细关联 Agent 执行
--
-- llm_trace 原先只能靠 trace_id 与 agent_run 粗略对齐：同一 trace 下的会话摘要、
-- 记忆合并等后台模型调用会混进来，无法还原"某次 Agent 执行到底调了哪几次模型"。
-- 这里补一个由 ChatTraceContext 透传下来的 run_id，使 Agent 执行详情页能精确取到
-- 自己名下的全部模型调用（含重试与供应商降级产生的多条）。
--
-- 注意：run_id 对非 Harness 场景（后台摘要、评估、工作流直调模型等）为空，
-- 这些行仍然只归属到 llm_trace 单表视角，不属于任何一次 Agent 执行。
-- ----------------------------

ALTER TABLE "public"."llm_trace" ADD COLUMN IF NOT EXISTS "run_id" varchar(50);

COMMENT ON COLUMN "public"."llm_trace"."run_id" IS '所属 Agent 执行 run ID；非 Harness 调用为空';

CREATE INDEX IF NOT EXISTS "llm_trace_run_id_idx" ON "public"."llm_trace" USING btree ("run_id", "start_time");
