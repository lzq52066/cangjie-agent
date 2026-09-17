-- ----------------------------
-- CangJie Agent V4: Agent Harness 运行留痕
-- agent_run 记录一次 Agent 执行的汇总状态（含断点续跑所需的检查点），
-- agent_run_step 记录逐步明细（llm / tool / context / hook / error）。
-- 注意：step 只存"增量"而非全量 prompt，避免多轮上下文写放大。
-- ----------------------------

CREATE TABLE IF NOT EXISTS "public"."agent_run" (
    "id" varchar(50) NOT NULL,
    "trace_id" varchar(64),
    "session_id" varchar(50),
    "app_id" varchar(50),
    "app_name" varchar(100),
    "user_id" varchar(50),
    "model_id" varchar(50),
    "model_name" varchar(100),
    "harness_type" varchar(20) NOT NULL DEFAULT 'chat',
    "parent_run_id" varchar(50),
    "depth" int4 NOT NULL DEFAULT 0,
    "status" varchar(20) NOT NULL DEFAULT 'running',
    "rounds" int4 NOT NULL DEFAULT 0,
    "tool_call_count" int4 NOT NULL DEFAULT 0,
    "input_tokens" int8 NOT NULL DEFAULT 0,
    "output_tokens" int8 NOT NULL DEFAULT 0,
    "total_tokens" int8 NOT NULL DEFAULT 0,
    "token_budget" int8,
    "estimated_cost" numeric(12,6),
    "finish_reason" varchar(32),
    "final_text" text,
    "error_message" text,
    "context_snapshot" text,
    "context_usage" text,
    "resume_token" varchar(64),
    "start_time" timestamp(6),
    "end_time" timestamp(6),
    "duration" int8,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "agent_run_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "agent_run_trace_id_idx" ON "public"."agent_run" USING btree ("trace_id");
CREATE INDEX IF NOT EXISTS "agent_run_session_id_idx" ON "public"."agent_run" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "agent_run_app_id_idx" ON "public"."agent_run" USING btree ("app_id");
CREATE INDEX IF NOT EXISTS "agent_run_status_idx" ON "public"."agent_run" USING btree ("status");
CREATE INDEX IF NOT EXISTS "agent_run_parent_run_id_idx" ON "public"."agent_run" USING btree ("parent_run_id");
CREATE INDEX IF NOT EXISTS "agent_run_start_time_idx" ON "public"."agent_run" USING btree ("start_time" DESC);

COMMENT ON TABLE "public"."agent_run" IS 'Agent 执行主记录（一次 Harness run）';
COMMENT ON COLUMN "public"."agent_run"."harness_type" IS '执行形态：chat / workflow / subagent / debug';
COMMENT ON COLUMN "public"."agent_run"."parent_run_id" IS '父 run ID，子 Agent 与工作流内嵌 Agent 场景使用';
COMMENT ON COLUMN "public"."agent_run"."depth" IS '嵌套深度，顶层为 0';
COMMENT ON COLUMN "public"."agent_run"."status" IS '状态：running / completed / failed / cancelled / waiting_approval';
COMMENT ON COLUMN "public"."agent_run"."token_budget" IS '本次 run 的 token 预算，NULL 表示不限制';
COMMENT ON COLUMN "public"."agent_run"."estimated_cost" IS '按 model_pricing 折算的估算成本';
COMMENT ON COLUMN "public"."agent_run"."context_snapshot" IS '断点快照（ResumeState JSON），用于审批后恢复执行';
COMMENT ON COLUMN "public"."agent_run"."context_usage" IS '上下文装配各槽位占用（ContextFragment 摘要 JSON）';
COMMENT ON COLUMN "public"."agent_run"."resume_token" IS '一次性恢复令牌，恢复或重新挂起后即失效';

CREATE TABLE IF NOT EXISTS "public"."agent_run_step" (
    "id" varchar(50) NOT NULL,
    "run_id" varchar(50) NOT NULL,
    "step_no" int4 NOT NULL DEFAULT 0,
    "round" int4 NOT NULL DEFAULT 0,
    "type" varchar(20) NOT NULL,
    "name" varchar(200),
    "status" varchar(20),
    "input" text,
    "output" text,
    "error_message" text,
    "input_tokens" int8,
    "output_tokens" int8,
    "duration" int8,
    "truncated" int4 NOT NULL DEFAULT 0,
    "start_time" timestamp(6),
    "end_time" timestamp(6),
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "agent_run_step_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "agent_run_step_run_id_idx" ON "public"."agent_run_step" USING btree ("run_id", "step_no");
CREATE INDEX IF NOT EXISTS "agent_run_step_type_idx" ON "public"."agent_run_step" USING btree ("type");
CREATE INDEX IF NOT EXISTS "agent_run_step_create_time_idx" ON "public"."agent_run_step" USING btree ("create_time" DESC);

COMMENT ON TABLE "public"."agent_run_step" IS 'Agent 执行步骤明细（增量留痕）';
COMMENT ON COLUMN "public"."agent_run_step"."type" IS '步骤类型：llm / tool / context / hook / error';
COMMENT ON COLUMN "public"."agent_run_step"."input" IS '入参；llm 类型只记录本轮新增消息';
COMMENT ON COLUMN "public"."agent_run_step"."truncated" IS '输出是否被截断：0 否 / 1 是';
