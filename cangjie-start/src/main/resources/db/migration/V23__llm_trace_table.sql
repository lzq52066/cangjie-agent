-- ----------------------------
-- CangJie Agent V23: LLM 调用明细追踪表（可观测性）
-- ----------------------------

CREATE TABLE IF NOT EXISTS "public"."llm_trace" (
    "id" varchar(50) NOT NULL,
    "trace_id" varchar(64),
    "request_id" varchar(64),
    "app_id" varchar(50),
    "app_name" varchar(100),
    "session_id" varchar(50),
    "user_id" varchar(50),
    "model_id" varchar(50),
    "model_name" varchar(100),
    "prompt_content" text,
    "input_tokens" int8,
    "output_tokens" int8,
    "total_tokens" int8,
    "response_content" text,
    "finish_reason" varchar(20),
    "duration" int8,
    "status" varchar(20) NOT NULL DEFAULT 'success',
    "error_message" text,
    "start_time" timestamp(6),
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "llm_trace_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "llm_trace_trace_id_idx" ON "public"."llm_trace" USING btree ("trace_id");
CREATE INDEX IF NOT EXISTS "llm_trace_app_id_idx" ON "public"."llm_trace" USING btree ("app_id");
CREATE INDEX IF NOT EXISTS "llm_trace_model_name_idx" ON "public"."llm_trace" USING btree ("model_name");
CREATE INDEX IF NOT EXISTS "llm_trace_status_idx" ON "public"."llm_trace" USING btree ("status");
CREATE INDEX IF NOT EXISTS "llm_trace_start_time_idx" ON "public"."llm_trace" USING btree ("start_time" DESC);
CREATE INDEX IF NOT EXISTS "llm_trace_session_id_idx" ON "public"."llm_trace" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "llm_trace_tenant_idx" ON "public"."llm_trace" USING btree ("tenant_id");
