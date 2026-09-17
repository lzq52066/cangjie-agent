-- ----------------------------
-- CangJie Agent V103: 工具审批与工具风险策略
-- agent_approval 承载"人工在环"审批单；工具表增加风险与执行约束列。
-- 审批采用挂起（checkpoint）+ 恢复模型，不占用线程等待用户。
-- ----------------------------

CREATE TABLE IF NOT EXISTS "public"."agent_approval" (
    "id" varchar(50) NOT NULL,
    "run_id" varchar(50) NOT NULL,
    "step_id" varchar(50),
    "session_id" varchar(50),
    "app_id" varchar(50),
    "user_id" varchar(50),
    "tool_name" varchar(200),
    "tool_type" varchar(50),
    "arguments" text,
    "reason" varchar(500),
    "risk_level" varchar(20),
    "status" varchar(20) NOT NULL DEFAULT 'pending',
    "resume_token" varchar(64),
    "decided_by" varchar(50),
    "decide_remark" varchar(500),
    "decide_time" timestamp(6),
    "expire_time" timestamp(6),
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "agent_approval_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "agent_approval_run_id_idx" ON "public"."agent_approval" USING btree ("run_id");
CREATE INDEX IF NOT EXISTS "agent_approval_status_idx" ON "public"."agent_approval" USING btree ("status");
CREATE INDEX IF NOT EXISTS "agent_approval_expire_time_idx" ON "public"."agent_approval" USING btree ("expire_time");
CREATE INDEX IF NOT EXISTS "agent_approval_create_time_idx" ON "public"."agent_approval" USING btree ("create_time" DESC);

COMMENT ON TABLE "public"."agent_approval" IS 'Agent 工具调用审批单';
COMMENT ON COLUMN "public"."agent_approval"."status" IS '状态：pending / approved / rejected / expired / cancelled';
COMMENT ON COLUMN "public"."agent_approval"."resume_token" IS '一次性恢复令牌，需与 agent_run.resume_token 同时匹配';
COMMENT ON COLUMN "public"."agent_approval"."expire_time" IS '过期时间，超时由扫描任务按默认决策落库';

-- 工具风险与执行约束
ALTER TABLE "public"."tool"
    ADD COLUMN IF NOT EXISTS "risk_level" VARCHAR(20) NOT NULL DEFAULT 'low';
ALTER TABLE "public"."tool"
    ADD COLUMN IF NOT EXISTS "require_approval" INT4 NOT NULL DEFAULT 0;
ALTER TABLE "public"."tool"
    ADD COLUMN IF NOT EXISTS "timeout_seconds" INT4;
ALTER TABLE "public"."tool"
    ADD COLUMN IF NOT EXISTS "max_output_chars" INT4;

COMMENT ON COLUMN "public"."tool"."risk_level" IS '风险等级：low / medium / high';
COMMENT ON COLUMN "public"."tool"."require_approval" IS '调用前是否必须人工审批：0 否 / 1 是';
COMMENT ON COLUMN "public"."tool"."timeout_seconds" IS '单次执行超时（秒），NULL 用全局默认';
COMMENT ON COLUMN "public"."tool"."max_output_chars" IS '输出回喂模型前的最大字符数，NULL 用全局默认';
