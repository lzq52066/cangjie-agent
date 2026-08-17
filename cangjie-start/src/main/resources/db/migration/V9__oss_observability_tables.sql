-- ----------------------------
-- CangJie Agent V9: OSS 文件存储 + 可观测性模块表结构
-- ----------------------------

-- ----------------------------
-- file_record 文件记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."file_record" (
    "id" varchar(50) NOT NULL,
    "file_name" varchar(500),
    "storage_type" varchar(20) NOT NULL DEFAULT 'local',
    "file_path" varchar(1000),
    "file_size" int8,
    "content_type" varchar(200),
    "md5" varchar(64),
    "url" varchar(1000),
    "category" varchar(50) NOT NULL DEFAULT 'other',
    "uploader_id" varchar(50),
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "file_record_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "file_record_category_idx" ON "public"."file_record" USING btree ("category");
CREATE INDEX IF NOT EXISTS "file_record_storage_type_idx" ON "public"."file_record" USING btree ("storage_type");
CREATE INDEX IF NOT EXISTS "file_record_uploader_id_idx" ON "public"."file_record" USING btree ("uploader_id");
CREATE INDEX IF NOT EXISTS "file_record_status_idx" ON "public"."file_record" USING btree ("status");
CREATE INDEX IF NOT EXISTS "file_record_create_time_idx" ON "public"."file_record" USING btree ("create_time" DESC);
CREATE INDEX IF NOT EXISTS "file_record_tenant_idx" ON "public"."file_record" USING btree ("tenant_id");

-- ----------------------------
-- operation_log 操作日志
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."operation_log" (
    "id" varchar(50) NOT NULL,
    "module" varchar(100),
    "action" varchar(200),
    "method" varchar(20),
    "uri" varchar(500),
    "params" text,
    "result" text,
    "trace_id" varchar(64),
    "ip" varchar(64),
    "user_id" varchar(50),
    "username" varchar(100),
    "duration" int8,
    "status" varchar(20) NOT NULL DEFAULT 'success',
    "error_message" text,
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "operation_log_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "operation_log_module_idx" ON "public"."operation_log" USING btree ("module");
CREATE INDEX IF NOT EXISTS "operation_log_action_idx" ON "public"."operation_log" USING btree ("action");
CREATE INDEX IF NOT EXISTS "operation_log_trace_id_idx" ON "public"."operation_log" USING btree ("trace_id");
CREATE INDEX IF NOT EXISTS "operation_log_user_id_idx" ON "public"."operation_log" USING btree ("user_id");
CREATE INDEX IF NOT EXISTS "operation_log_status_idx" ON "public"."operation_log" USING btree ("status");
CREATE INDEX IF NOT EXISTS "operation_log_create_time_idx" ON "public"."operation_log" USING btree ("create_time" DESC);
CREATE INDEX IF NOT EXISTS "operation_log_tenant_idx" ON "public"."operation_log" USING btree ("tenant_id");

-- ----------------------------
-- system_metric 系统指标
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."system_metric" (
    "id" varchar(50) NOT NULL,
    "metric_type" varchar(20),
    "metric_name" varchar(200),
    "metric_value" float8,
    "unit" varchar(50),
    "host" varchar(100),
    "collect_time" timestamp(6),
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "system_metric_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "system_metric_type_idx" ON "public"."system_metric" USING btree ("metric_type");
CREATE INDEX IF NOT EXISTS "system_metric_name_idx" ON "public"."system_metric" USING btree ("metric_name");
CREATE INDEX IF NOT EXISTS "system_metric_host_idx" ON "public"."system_metric" USING btree ("host");
CREATE INDEX IF NOT EXISTS "system_metric_collect_time_idx" ON "public"."system_metric" USING btree ("collect_time" DESC);
CREATE INDEX IF NOT EXISTS "system_metric_tenant_idx" ON "public"."system_metric" USING btree ("tenant_id");

-- ----------------------------
-- trace_record 调用追踪
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."trace_record" (
    "id" varchar(50) NOT NULL,
    "trace_id" varchar(64),
    "module" varchar(100),
    "action" varchar(200),
    "span_id" varchar(64),
    "parent_span_id" varchar(64),
    "duration" int8,
    "status" varchar(20) NOT NULL DEFAULT 'success',
    "message" text,
    "service_name" varchar(100),
    "start_time" timestamp(6),
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "trace_record_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "trace_record_trace_id_idx" ON "public"."trace_record" USING btree ("trace_id");
CREATE INDEX IF NOT EXISTS "trace_record_module_idx" ON "public"."trace_record" USING btree ("module");
CREATE INDEX IF NOT EXISTS "trace_record_action_idx" ON "public"."trace_record" USING btree ("action");
CREATE INDEX IF NOT EXISTS "trace_record_span_id_idx" ON "public"."trace_record" USING btree ("span_id");
CREATE INDEX IF NOT EXISTS "trace_record_status_idx" ON "public"."trace_record" USING btree ("status");
CREATE INDEX IF NOT EXISTS "trace_record_start_time_idx" ON "public"."trace_record" USING btree ("start_time" DESC);
CREATE INDEX IF NOT EXISTS "trace_record_tenant_idx" ON "public"."trace_record" USING btree ("tenant_id");
