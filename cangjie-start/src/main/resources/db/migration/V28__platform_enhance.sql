-- ----------------------------
-- CangJie Agent V28: 平台能力增强（P1/P2）
-- 1. 应用 token 配额
-- 2. 知识库可见性（数据权限）
-- 3. 工作流节点级执行事件
-- 4. MCP 服务注册表（工具市场）
-- 5. 应用模板
-- ----------------------------

-- 1. 应用 token 配额：0 表示不限制
ALTER TABLE "public"."application" ADD COLUMN IF NOT EXISTS "token_quota" int8 NOT NULL DEFAULT 0;
ALTER TABLE "public"."application" ADD COLUMN IF NOT EXISTS "tokens_used" int8 NOT NULL DEFAULT 0;
COMMENT ON COLUMN "public"."application"."token_quota" IS 'token 配额，0 表示不限制';
COMMENT ON COLUMN "public"."application"."tokens_used" IS '已消耗 token 累计';

-- 2. 知识库可见性：public 所有人可见 / private 仅创建者与管理端可见
ALTER TABLE "public"."knowledge_base" ADD COLUMN IF NOT EXISTS "visibility" varchar(20) NOT NULL DEFAULT 'public';
COMMENT ON COLUMN "public"."knowledge_base"."visibility" IS '可见性：public / private';

-- 3. 工作流节点级执行事件（支撑执行历史与断点续跑）
CREATE TABLE IF NOT EXISTS "public"."workflow_execution_event" (
    "id" varchar(50) NOT NULL,
    "execution_id" varchar(50) NOT NULL,
    "node_id" varchar(100) NOT NULL,
    "node_type" varchar(50),
    "status" varchar(20) NOT NULL DEFAULT 'running',
    "inputs" text,
    "outputs" text,
    "error_message" text,
    "duration" int8,
    "start_time" timestamp(6),
    "end_time" timestamp(6),
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "workflow_execution_event_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "wf_event_execution_id_idx" ON "public"."workflow_execution_event" USING btree ("execution_id");
CREATE INDEX IF NOT EXISTS "wf_event_status_idx" ON "public"."workflow_execution_event" USING btree ("status");

-- 4. MCP 服务注册表（工具市场）
CREATE TABLE IF NOT EXISTS "public"."mcp_server" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "server_url" varchar(500) NOT NULL,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "last_check_time" timestamp(6),
    "last_check_result" varchar(20),
    "tool_count" int4 NOT NULL DEFAULT 0,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "mcp_server_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "mcp_server_status_idx" ON "public"."mcp_server" USING btree ("status");

-- 5. 应用模板（从现有应用沉淀，可一键创建新应用）
CREATE TABLE IF NOT EXISTS "public"."application_template" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "icon" varchar(255),
    "category" varchar(100),
    "app_type" varchar(50),
    "snapshot" text NOT NULL,
    "use_count" int4 NOT NULL DEFAULT 0,
    "status" varchar(20) NOT NULL DEFAULT 'published',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "application_template_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "app_template_category_idx" ON "public"."application_template" USING btree ("category");
CREATE INDEX IF NOT EXISTS "app_template_status_idx" ON "public"."application_template" USING btree ("status");
