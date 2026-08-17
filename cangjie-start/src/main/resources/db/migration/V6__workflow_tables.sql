-- ----------------------------
-- CangJie Agent V6: 工作流引擎模块表结构
-- ----------------------------

-- ----------------------------
-- workflow 工作流定义
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."workflow" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "nodes" text,
    "edges" text,
    "variables" text,
    "version" int4 NOT NULL DEFAULT 1,
    "status" varchar(20) NOT NULL DEFAULT 'draft',
    "application_id" varchar(50),
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "workflow_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "workflow_name_idx" ON "public"."workflow" USING btree ("name");
CREATE INDEX IF NOT EXISTS "workflow_status_idx" ON "public"."workflow" USING btree ("status");
CREATE INDEX IF NOT EXISTS "workflow_application_id_idx" ON "public"."workflow" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "workflow_tenant_idx" ON "public"."workflow" USING btree ("tenant_id");

-- ----------------------------
-- workflow_execution 工作流执行记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."workflow_execution" (
    "id" varchar(50) NOT NULL,
    "workflow_id" varchar(50) NOT NULL,
    "application_id" varchar(50),
    "session_id" varchar(50),
    "status" varchar(20) NOT NULL DEFAULT 'pending',
    "inputs" text,
    "outputs" text,
    "current_node" varchar(100),
    "error_message" text,
    "duration" int8,
    "start_time" timestamp(6),
    "end_time" timestamp(6),
    "tenant_id" varchar(50) NOT NULL DEFAULT 'default',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "workflow_execution_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "workflow_execution_workflow_id_idx" ON "public"."workflow_execution" USING btree ("workflow_id");
CREATE INDEX IF NOT EXISTS "workflow_execution_status_idx" ON "public"."workflow_execution" USING btree ("status");
CREATE INDEX IF NOT EXISTS "workflow_execution_session_id_idx" ON "public"."workflow_execution" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "workflow_execution_start_time_idx" ON "public"."workflow_execution" USING btree ("start_time" DESC);
CREATE INDEX IF NOT EXISTS "workflow_execution_tenant_idx" ON "public"."workflow_execution" USING btree ("tenant_id");
