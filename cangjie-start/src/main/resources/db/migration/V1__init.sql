-- ============================================================
-- CangJie Agent 数据库初始化脚本（合并自 V1~V31 历史迁移）
-- ============================================================

-- ########## 来源迁移: V1__init_tables.sql ##########

-- ----------------------------
-- CangJie Agent V1 init tables
-- ----------------------------
CREATE EXTENSION IF NOT EXISTS "vector";

-- ----------------------------
-- system_setting
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."system_setting" (
    "type" int4 NOT NULL,
    "meta" jsonb NOT NULL DEFAULT '{}'::jsonb,
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY ("type")
);

-- ----------------------------
-- user
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."user" (
    "id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "email" varchar(254) COLLATE "pg_catalog"."default",
    "phone" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
    "nickname" varchar(150) COLLATE "pg_catalog"."default" NOT NULL,
    "username" varchar(150) COLLATE "pg_catalog"."default" NOT NULL,
    "password" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
    "role" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "is_active" bool NOT NULL DEFAULT true,
    "source" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
    "language" varchar(10) COLLATE "pg_catalog"."default" NOT NULL,
    "avatar" varchar(512) COLLATE "pg_catalog"."default",
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6),
    "update_time" timestamp(6),
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "user_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "user_username_key" UNIQUE ("username")
);

CREATE INDEX IF NOT EXISTS "user_email_idx" ON "public"."user" USING btree ("email");

-- ----------------------------
-- user_resource_permission
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."user_resource_permission" (
    "id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "workspace_id" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
    "auth_target_type" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
    "target_id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "auth_type" varchar COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'ROLE',
    "permission_list" varchar(256)[] COLLATE "pg_catalog"."default" NOT NULL DEFAULT '{}',
    "user_id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "user_resource_permission_pkey" PRIMARY KEY ("id")
);

-- ----------------------------
-- audit_log 审计日志
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."audit_log" (
    "id" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
    "trace_id" varchar(64) COLLATE "pg_catalog"."default",
    "user_id" varchar(50) COLLATE "pg_catalog"."default",
    "username" varchar(150) COLLATE "pg_catalog"."default",
    "operation" varchar(64) COLLATE "pg_catalog"."default",
    "resource_type" varchar(64) COLLATE "pg_catalog"."default",
    "resource_id" varchar(50) COLLATE "pg_catalog"."default",
    "request_method" varchar(16) COLLATE "pg_catalog"."default",
    "request_uri" varchar(512) COLLATE "pg_catalog"."default",
    "client_ip" varchar(64) COLLATE "pg_catalog"."default",
    "request_params" text,
    "response_code" int4,
    "cost_ms" int8,
    "is_success" bool DEFAULT true,
    "error_msg" text,
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "audit_log_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "audit_log_user_id_idx" ON "public"."audit_log" USING btree ("user_id");
CREATE INDEX IF NOT EXISTS "audit_log_create_time_idx" ON "public"."audit_log" USING btree ("create_time" DESC);


-- ########## 来源迁移: V2__knowledge_tables.sql ##########

-- ----------------------------
-- CangJie Agent V2: 知识库模块表结构
-- ----------------------------

-- ----------------------------
-- knowledge_base 知识库
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."knowledge_base" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "split_strategy" varchar(20) NOT NULL DEFAULT 'smart',
    "chunk_size" int4 NOT NULL DEFAULT 500,
    "separators" text,
    "embedding_model_id" varchar(100),
    "embedding_dimension" int4 DEFAULT 1536,
    "document_count" int4 NOT NULL DEFAULT 0,
    "paragraph_count" int4 NOT NULL DEFAULT 0,
    "directory" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "knowledge_base_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "knowledge_base_name_idx" ON "public"."knowledge_base" USING btree ("name");

-- ----------------------------
-- knowledge_document 知识库文档
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."knowledge_document" (
    "id" varchar(50) NOT NULL,
    "knowledge_base_id" varchar(50) NOT NULL,
    "name" varchar(500) NOT NULL,
    "file_type" varchar(20),
    "file_size" int8,
    "file_path" varchar(1000),
    "file_md5" varchar(64),
    "status" varchar(20) NOT NULL DEFAULT 'pending',
    "process_message" text,
    "paragraph_count" int4 DEFAULT 0,
    "token_count" int4 DEFAULT 0,
    "title" varchar(500),
    "directory_path" varchar(500),
    "metadata" text,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "knowledge_document_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "knowledge_document_kb_idx" ON "public"."knowledge_document" USING btree ("knowledge_base_id");
CREATE INDEX IF NOT EXISTS "knowledge_document_md5_idx" ON "public"."knowledge_document" USING btree ("knowledge_base_id", "file_md5");

-- ----------------------------
-- knowledge_paragraph 知识库段落（含向量列 + 全文检索列）
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."knowledge_paragraph" (
    "id" varchar(50) NOT NULL,
    "knowledge_base_id" varchar(50) NOT NULL,
    "document_id" varchar(50) NOT NULL,
    "title" varchar(500),
    "content" text NOT NULL,
    "chunk_index" int4 NOT NULL DEFAULT 0,
    "page_number" int4,
    "token_count" int4 DEFAULT 0,
    "char_count" int4 DEFAULT 0,
    "vector_status" varchar(20) NOT NULL DEFAULT 'pending',
    "metadata" text,
    "embedding" vector(1024),
    "ts_vector" tsvector,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "knowledge_paragraph_pkey" PRIMARY KEY ("id")
);

-- 向量索引（IVFFlat，适合中等规模数据；大数据量可换 HNSW）
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_embedding_idx"
    ON "public"."knowledge_paragraph" USING ivfflat ("embedding" vector_cosine_ops) WITH (lists = 100);

-- 全文检索索引
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_ts_vector_idx"
    ON "public"."knowledge_paragraph" USING gin ("ts_vector");

-- 普通索引
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_kb_idx" ON "public"."knowledge_paragraph" USING btree ("knowledge_base_id");
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_doc_idx" ON "public"."knowledge_paragraph" USING btree ("document_id");
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_status_idx" ON "public"."knowledge_paragraph" USING btree ("vector_status");


-- ########## 来源迁移: V3__model_tables.sql ##########

-- ----------------------------
-- CangJie Agent V3: 模型配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."model_config" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "model_type" varchar(20) NOT NULL,
    "api_key" varchar(500),
    "base_url" varchar(500),
    "model_name" varchar(200) NOT NULL,
    "temperature" float8 DEFAULT 0.7,
    "max_tokens" int4 DEFAULT 4096,
    "top_p" float8 DEFAULT 1.0,
    "is_default" bool NOT NULL DEFAULT false,
    "support_embedding" bool NOT NULL DEFAULT false,
    "embedding_dimension" int4,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "description" text,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "model_config_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "model_config_type_idx" ON "public"."model_config" USING btree ("model_type");
CREATE INDEX IF NOT EXISTS "model_config_default_idx" ON "public"."model_config" USING btree ("is_default");
CREATE INDEX IF NOT EXISTS "model_config_status_idx" ON "public"."model_config" USING btree ("status");


-- ########## 来源迁移: V4__prompt_tables.sql ##########

-- ----------------------------
-- CangJie Agent V4: 提示词/Skill/记忆/规则/命令模块表结构
-- ----------------------------

-- ----------------------------
-- prompt_template 提示词模板
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."prompt_template" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "category" varchar(100),
    "content" text,
    "description" text,
    "variables" text,
    "is_default" bool NOT NULL DEFAULT false,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "prompt_template_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "prompt_template_name_idx" ON "public"."prompt_template" USING btree ("name");
CREATE INDEX IF NOT EXISTS "prompt_template_category_idx" ON "public"."prompt_template" USING btree ("category");
CREATE INDEX IF NOT EXISTS "prompt_template_default_idx" ON "public"."prompt_template" USING btree ("is_default");
CREATE INDEX IF NOT EXISTS "prompt_template_status_idx" ON "public"."prompt_template" USING btree ("status");

-- ----------------------------
-- skill Skill技能
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."skill" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "type" varchar(50),
    "content" text,
    "function_name" varchar(200),
    "parameters" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "skill_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "skill_name_idx" ON "public"."skill" USING btree ("name");
CREATE INDEX IF NOT EXISTS "skill_type_idx" ON "public"."skill" USING btree ("type");
CREATE INDEX IF NOT EXISTS "skill_function_name_idx" ON "public"."skill" USING btree ("function_name");
CREATE INDEX IF NOT EXISTS "skill_status_idx" ON "public"."skill" USING btree ("status");

-- ----------------------------
-- memory 记忆
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."memory" (
    "id" varchar(50) NOT NULL,
    "application_id" varchar(50),
    "session_id" varchar(50),
    "role" varchar(50),
    "content" text,
    "summary" text,
    "importance" int4 DEFAULT 0,
    "expire_time" timestamp(6),
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "memory_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "memory_application_idx" ON "public"."memory" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "memory_session_idx" ON "public"."memory" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "memory_importance_idx" ON "public"."memory" USING btree ("importance");
CREATE INDEX IF NOT EXISTS "memory_status_idx" ON "public"."memory" USING btree ("status");

-- ----------------------------
-- rule 规则
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."rule" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "type" varchar(50),
    "condition" text,
    "action" text,
    "priority" int4 DEFAULT 0,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "rule_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "rule_name_idx" ON "public"."rule" USING btree ("name");
CREATE INDEX IF NOT EXISTS "rule_type_idx" ON "public"."rule" USING btree ("type");
CREATE INDEX IF NOT EXISTS "rule_priority_idx" ON "public"."rule" USING btree ("priority");
CREATE INDEX IF NOT EXISTS "rule_status_idx" ON "public"."rule" USING btree ("status");

-- ----------------------------
-- command 命令
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."command" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "command" varchar(200),
    "description" text,
    "type" varchar(50),
    "script" text,
    "parameters" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "command_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "command_name_idx" ON "public"."command" USING btree ("name");
CREATE INDEX IF NOT EXISTS "command_command_idx" ON "public"."command" USING btree ("command");
CREATE INDEX IF NOT EXISTS "command_type_idx" ON "public"."command" USING btree ("type");
CREATE INDEX IF NOT EXISTS "command_status_idx" ON "public"."command" USING btree ("status");


-- ########## 来源迁移: V5__tool_tables.sql ##########

-- ----------------------------
-- CangJie Agent V5: 工具/插件系统模块表结构（万物即插件）
-- ----------------------------

-- ----------------------------
-- tool 工具配置
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."tool" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "type" varchar(50),
    "function_name" varchar(200),
    "parameters" text,
    "implementation" varchar(500),
    "config" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "icon" varchar(200),
    "category" varchar(100),
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "tool_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "tool_name_idx" ON "public"."tool" USING btree ("name");
CREATE INDEX IF NOT EXISTS "tool_type_idx" ON "public"."tool" USING btree ("type");
CREATE INDEX IF NOT EXISTS "tool_function_name_idx" ON "public"."tool" USING btree ("function_name");
CREATE INDEX IF NOT EXISTS "tool_status_idx" ON "public"."tool" USING btree ("status");
CREATE INDEX IF NOT EXISTS "tool_category_idx" ON "public"."tool" USING btree ("category");

-- ----------------------------
-- plugin_instance 插件实例
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."plugin_instance" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "type" varchar(50),
    "description" text,
    "class_name" varchar(500),
    "version" varchar(50),
    "config" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "loaded" bool NOT NULL DEFAULT false,
    "load_error" text,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "plugin_instance_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "plugin_instance_name_idx" ON "public"."plugin_instance" USING btree ("name");
CREATE INDEX IF NOT EXISTS "plugin_instance_type_idx" ON "public"."plugin_instance" USING btree ("type");
CREATE INDEX IF NOT EXISTS "plugin_instance_class_name_idx" ON "public"."plugin_instance" USING btree ("class_name");
CREATE INDEX IF NOT EXISTS "plugin_instance_status_idx" ON "public"."plugin_instance" USING btree ("status");
CREATE INDEX IF NOT EXISTS "plugin_instance_loaded_idx" ON "public"."plugin_instance" USING btree ("loaded");


-- ########## 来源迁移: V6__workflow_tables.sql ##########

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


-- ########## 来源迁移: V7__application_chat_tables.sql ##########

-- ----------------------------
-- CangJie Agent V7: 智能应用 + 对话模块表结构
-- ----------------------------

-- ----------------------------
-- application 智能应用
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."application" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "type" varchar(20) NOT NULL DEFAULT 'chat',
    "model_id" varchar(50),
    "knowledge_base_ids" text,
    "prompt_template_id" varchar(50),
    "skill_ids" text,
    "rule_ids" text,
    "memory_enabled" bool NOT NULL DEFAULT false,
    "max_turns" int4 NOT NULL DEFAULT 20,
    "temperature" numeric(3,2) DEFAULT 0.70,
    "config" text,
    "icon" varchar(500),
    "status" varchar(20) NOT NULL DEFAULT 'draft',
    "apikey" varchar(100),
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "application_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "application_name_idx" ON "public"."application" USING btree ("name");
CREATE INDEX IF NOT EXISTS "application_type_idx" ON "public"."application" USING btree ("type");
CREATE INDEX IF NOT EXISTS "application_status_idx" ON "public"."application" USING btree ("status");
CREATE INDEX IF NOT EXISTS "application_apikey_idx" ON "public"."application" USING btree ("apikey");
CREATE INDEX IF NOT EXISTS "application_model_id_idx" ON "public"."application" USING btree ("model_id");

-- ----------------------------
-- chat_session 对话会话
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."chat_session" (
    "id" varchar(50) NOT NULL,
    "application_id" varchar(50),
    "session_id" varchar(100) NOT NULL,
    "title" varchar(500),
    "user_id" varchar(50),
    "source" varchar(20) DEFAULT 'api',
    "model_id" varchar(50),
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "message_count" int4 NOT NULL DEFAULT 0,
    "tokens_used" int4 NOT NULL DEFAULT 0,
    "metadata" text,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "chat_session_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "chat_session_application_id_idx" ON "public"."chat_session" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "chat_session_session_id_idx" ON "public"."chat_session" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "chat_session_user_id_idx" ON "public"."chat_session" USING btree ("user_id");
CREATE INDEX IF NOT EXISTS "chat_session_status_idx" ON "public"."chat_session" USING btree ("status");
CREATE INDEX IF NOT EXISTS "chat_session_create_time_idx" ON "public"."chat_session" USING btree ("create_time" DESC);

-- ----------------------------
-- chat_message 对话消息
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."chat_message" (
    "id" varchar(50) NOT NULL,
    "session_id" varchar(100) NOT NULL,
    "application_id" varchar(50),
    "role" varchar(20) NOT NULL,
    "content" text,
    "tokens" int4,
    "retrieval_sources" text,
    "metadata" text,
    "duration" int8,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "chat_message_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "chat_message_session_id_idx" ON "public"."chat_message" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "chat_message_application_id_idx" ON "public"."chat_message" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "chat_message_role_idx" ON "public"."chat_message" USING btree ("role");
CREATE INDEX IF NOT EXISTS "chat_message_create_time_idx" ON "public"."chat_message" USING btree ("create_time");


-- ########## 来源迁移: V8__trigger_tables.sql ##########

-- ----------------------------
-- CangJie Agent V8: 渠道接入（trigger）模块表结构
-- ----------------------------

-- ----------------------------
-- channel 渠道配置
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."channel" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "type" varchar(20) NOT NULL,
    "application_id" varchar(50),
    "app_id" varchar(100),
    "app_secret" varchar(200),
    "token" varchar(200),
    "encoding_aes_key" varchar(100),
    "verify_token" varchar(200),
    "config" text,
    "status" varchar(20) NOT NULL DEFAULT 'inactive',
    "last_call_time" timestamp(6),
    "call_count" int4 NOT NULL DEFAULT 0,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "channel_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "channel_type_idx" ON "public"."channel" USING btree ("type");
CREATE INDEX IF NOT EXISTS "channel_application_id_idx" ON "public"."channel" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "channel_status_idx" ON "public"."channel" USING btree ("status");

-- ----------------------------
-- channel_message 渠道消息记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."channel_message" (
    "id" varchar(50) NOT NULL,
    "channel_id" varchar(50) NOT NULL,
    "channel_type" varchar(20),
    "application_id" varchar(50),
    "open_id" varchar(100),
    "session_id" varchar(100),
    "msg_type" varchar(20),
    "content" text,
    "event_type" varchar(50),
    "reply_content" text,
    "status" varchar(20) NOT NULL DEFAULT 'processed',
    "error_message" text,
    "cost_time" int8,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "channel_message_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "channel_message_channel_id_idx" ON "public"."channel_message" USING btree ("channel_id");
CREATE INDEX IF NOT EXISTS "channel_message_open_id_idx" ON "public"."channel_message" USING btree ("open_id");
CREATE INDEX IF NOT EXISTS "channel_message_application_id_idx" ON "public"."channel_message" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "channel_message_session_id_idx" ON "public"."channel_message" USING btree ("session_id");
CREATE INDEX IF NOT EXISTS "channel_message_create_time_idx" ON "public"."channel_message" USING btree ("create_time");


-- ########## 来源迁移: V9__oss_observability_tables.sql ##########

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


-- ########## 来源迁移: V10__knowledge_split_upgrade.sql ##########

-- 知识库切片智能化改造：移除 chunk_overlap，新增 separators
ALTER TABLE "public"."knowledge_base"
    DROP COLUMN IF EXISTS "chunk_overlap",
    ADD COLUMN IF NOT EXISTS "separators" text;

-- 默认策略改为 smart
UPDATE "public"."knowledge_base" SET "split_strategy" = 'smart' WHERE "split_strategy" IN ('sentence', 'structural', 'token');
ALTER TABLE "public"."knowledge_base" ALTER COLUMN "split_strategy" SET DEFAULT 'smart';


-- ########## 来源迁移: V11__embedding_dimension_1024.sql ##########

-- 将 embedding 列维度从 1536 改为 1024（适配 BAAI/bge-m3 模型）
-- 先删除旧索引，修改列类型，再重建索引
DROP INDEX IF EXISTS "knowledge_paragraph_embedding_idx";

ALTER TABLE "knowledge_paragraph" ALTER COLUMN "embedding" TYPE vector(1024);

-- 重建向量索引
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_embedding_idx"
    ON "public"."knowledge_paragraph" USING ivfflat ("embedding" vector_cosine_ops) WITH (lists = 100);


-- ########## 来源迁移: V12__application_suggestions.sql ##########

-- 智能应用：新增建议问题配置（chat 入口欢迎页展示）
ALTER TABLE "public"."application" ADD COLUMN IF NOT EXISTS "suggestions" text;


-- ########## 来源迁移: V13__knowledge_document_add_file_id.sql ##########

-- ----------------------------
-- V13: knowledge_document 增加 file_id 列，关联 file_record
-- ----------------------------
ALTER TABLE "public"."knowledge_document"
    ADD COLUMN IF NOT EXISTS "file_id" varchar(50);

COMMENT ON COLUMN "public"."knowledge_document"."file_id" IS '关联的文件管理记录 ID（file_record.id）';

-- ########## 来源迁移: V14__rbac_auth_tables.sql ##########

-- ----------------------------
-- CangJie Agent V12: RBAC 权限管理模块表结构
-- ----------------------------

-- ----------------------------
-- role 角色表
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."role" (
    "id" varchar(50) NOT NULL,
    "name" varchar(100) NOT NULL,
    "code" varchar(100) NOT NULL,
    "description" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "role_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "role_code_idx" ON "public"."role" USING btree ("code");
CREATE INDEX IF NOT EXISTS "role_status_idx" ON "public"."role" USING btree ("status");

-- ----------------------------
-- menu 菜单/权限表
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."menu" (
    "id" varchar(50) NOT NULL,
    "name" varchar(100) NOT NULL,
    "code" varchar(100) NOT NULL,
    "path" varchar(200),
    "component" varchar(200),
    "icon" varchar(100),
    "parent_id" varchar(50),
    "sort" int4 NOT NULL DEFAULT 0,
    "type" varchar(20) NOT NULL DEFAULT 'menu',
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "menu_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "menu_parent_id_idx" ON "public"."menu" USING btree ("parent_id");
CREATE INDEX IF NOT EXISTS "menu_type_idx" ON "public"."menu" USING btree ("type");
CREATE INDEX IF NOT EXISTS "menu_status_idx" ON "public"."menu" USING btree ("status");

-- ----------------------------
-- user_role 用户角色关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."user_role" (
    "id" varchar(50) NOT NULL,
    "user_id" varchar(50) NOT NULL,
    "role_id" varchar(50) NOT NULL,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "user_role_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "user_role_user_id_idx" ON "public"."user_role" USING btree ("user_id");
CREATE INDEX IF NOT EXISTS "user_role_role_id_idx" ON "public"."user_role" USING btree ("role_id");

-- ----------------------------
-- role_menu 角色菜单关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."role_menu" (
    "id" varchar(50) NOT NULL,
    "role_id" varchar(50) NOT NULL,
    "menu_id" varchar(50) NOT NULL,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "role_menu_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "role_menu_role_id_idx" ON "public"."role_menu" USING btree ("role_id");
CREATE INDEX IF NOT EXISTS "role_menu_menu_id_idx" ON "public"."role_menu" USING btree ("menu_id");

-- ########## 来源迁移: V15__rbac_seed_data.sql ##########

-- ----------------------------
-- CangJie Agent V15: RBAC 种子数据
-- 初始化全量系统菜单树、按钮权限、内置角色、角色菜单授权
-- ----------------------------

-- ============================================================
-- 一级菜单（对应前端 MainLayout 中 getDefaultMenus 的菜单项）
-- ============================================================

-- 1. 工作台
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_dashboard', '工作台', 'dashboard', '/dashboard', 'DashboardView', 'DataBoard', NULL, 1, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 2. 模型管理
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_model', '模型管理', 'model', '/model', 'ModelView', 'Connection', NULL, 2, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 3. 知识库
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_knowledge', '知识库', 'knowledge', '/knowledge', 'KnowledgeView', 'Files', NULL, 3, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 4. 工具插件
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_tool', '工具插件', 'tool', '/tool', 'ToolView', 'Tools', NULL, 4, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 5. 提示词/Skill
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_prompt', '提示词/Skill', 'prompt', '/prompt', 'PromptView', 'EditPen', NULL, 5, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 6. 工作流
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_workflow', '工作流', 'workflow', '/workflow', 'WorkflowView', 'Share', NULL, 6, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 7. 智能应用
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_application', '智能应用', 'application', '/application', 'ApplicationView', 'VideoPlay', NULL, 7, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 8. 渠道接入
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_channel', '渠道接入', 'channel', '/channel', 'ChannelView', 'DataLine', NULL, 8, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 9. 可观测性
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_observability', '可观测性', 'observability', '/observability', 'ObservabilityView', 'Histogram', NULL, 9, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 10. 文件管理
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_file', '文件管理', 'file', '/file', 'FileView', 'Folder', NULL, 10, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 系统管理（目录 + 子菜单）
-- ============================================================

-- 11. 系统管理目录
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_system', '系统设置', 'system', '/system', NULL, 'Setting', NULL, 100, 'directory', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 系统管理子菜单：角色管理
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_role', '角色管理', 'system:role', '/system/role', 'system/RoleView', 'UserFilled', 'menu_system', 1, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 系统管理子菜单：菜单管理
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_menu', '菜单管理', 'system:menu', '/system/menu', 'system/MenuView', 'Menu', 'menu_system', 2, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：业务模块（标准 CRUD）
-- ============================================================

-- 模型管理按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_model_query',  '查询模型', 'model:query',  NULL, NULL, NULL, 'menu_model', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_model_add',    '新增模型', 'model:add',    NULL, NULL, NULL, 'menu_model', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_model_update', '修改模型', 'model:update', NULL, NULL, NULL, 'menu_model', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_model_delete', '删除模型', 'model:delete', NULL, NULL, NULL, 'menu_model', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 知识库按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_knowledge_query',  '查询知识库', 'knowledge:query',  NULL, NULL, NULL, 'menu_knowledge', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_knowledge_add',    '新增知识库', 'knowledge:add',    NULL, NULL, NULL, 'menu_knowledge', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_knowledge_update', '修改知识库', 'knowledge:update', NULL, NULL, NULL, 'menu_knowledge', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_knowledge_delete', '删除知识库', 'knowledge:delete', NULL, NULL, NULL, 'menu_knowledge', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 工具插件按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_tool_query',  '查询工具', 'tool:query',  NULL, NULL, NULL, 'menu_tool', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_tool_add',    '新增工具', 'tool:add',    NULL, NULL, NULL, 'menu_tool', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_tool_update', '修改工具', 'tool:update', NULL, NULL, NULL, 'menu_tool', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_tool_delete', '删除工具', 'tool:delete', NULL, NULL, NULL, 'menu_tool', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 提示词/Skill 按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_prompt_query',  '查询提示词', 'prompt:query',  NULL, NULL, NULL, 'menu_prompt', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_prompt_add',    '新增提示词', 'prompt:add',    NULL, NULL, NULL, 'menu_prompt', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_prompt_update', '修改提示词', 'prompt:update', NULL, NULL, NULL, 'menu_prompt', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_prompt_delete', '删除提示词', 'prompt:delete', NULL, NULL, NULL, 'menu_prompt', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 工作流按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_workflow_query',  '查询工作流', 'workflow:query',  NULL, NULL, NULL, 'menu_workflow', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_workflow_add',    '新增工作流', 'workflow:add',    NULL, NULL, NULL, 'menu_workflow', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_workflow_update', '修改工作流', 'workflow:update', NULL, NULL, NULL, 'menu_workflow', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_workflow_delete', '删除工作流', 'workflow:delete', NULL, NULL, NULL, 'menu_workflow', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 智能应用按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_application_query',  '查询应用', 'application:query',  NULL, NULL, NULL, 'menu_application', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_application_add',    '新增应用', 'application:add',    NULL, NULL, NULL, 'menu_application', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_application_update', '修改应用', 'application:update', NULL, NULL, NULL, 'menu_application', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_application_delete', '删除应用', 'application:delete', NULL, NULL, NULL, 'menu_application', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 渠道接入按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_channel_query',  '查询渠道', 'channel:query',  NULL, NULL, NULL, 'menu_channel', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_channel_add',    '新增渠道', 'channel:add',    NULL, NULL, NULL, 'menu_channel', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_channel_update', '修改渠道', 'channel:update', NULL, NULL, NULL, 'menu_channel', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_channel_delete', '删除渠道', 'channel:delete', NULL, NULL, NULL, 'menu_channel', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 可观测性按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_observability_query',  '查询监控', 'observability:query',  NULL, NULL, NULL, 'menu_observability', 1, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 文件管理按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_file_query',  '查询文件', 'file:query',  NULL, NULL, NULL, 'menu_file', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_file_upload', '上传文件', 'file:upload', NULL, NULL, NULL, 'menu_file', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_file_delete', '删除文件', 'file:delete', NULL, NULL, NULL, 'menu_file', 3, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：系统管理模块
-- ============================================================

-- 角色管理按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_role_add',    '新增角色', 'system:role:add',    NULL, NULL, NULL, 'menu_role', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_role_update', '修改角色', 'system:role:update', NULL, NULL, NULL, 'menu_role', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_role_delete', '删除角色', 'system:role:delete', NULL, NULL, NULL, 'menu_role', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_role_query',  '查询角色', 'system:role:query',  NULL, NULL, NULL, 'menu_role', 4, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_role_assign', '分配用户', 'system:role:assign', NULL, NULL, NULL, 'menu_role', 5, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 菜单管理按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_menu_add',    '新增菜单', 'system:menu:add',    NULL, NULL, NULL, 'menu_menu', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_menu_update', '修改菜单', 'system:menu:update', NULL, NULL, NULL, 'menu_menu', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_menu_delete', '删除菜单', 'system:menu:delete', NULL, NULL, NULL, 'menu_menu', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_menu_query',  '查询菜单', 'system:menu:query',  NULL, NULL, NULL, 'menu_menu', 4, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_menu_assign', '分配菜单', 'system:menu:assign', NULL, NULL, NULL, 'menu_menu', 5, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 内置角色
-- ============================================================
INSERT INTO "public"."role" ("id", "name", "code", "description", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('role_admin', '超级管理员', 'ADMIN', '系统内置超级管理员，拥有全部权限', 'active', 'system', 'system', now(), now(), 0),
('role_user',  '普通用户',   'USER',  '系统内置普通用户角色',            'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 超级管理员角色关联所有菜单/按钮权限
-- ============================================================
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', m."id", 'system', 'system', now(), now(), 0
FROM "public"."menu" m
WHERE m."deleted" = 0
  AND NOT EXISTS (SELECT 1 FROM "public"."role_menu" rm WHERE rm."role_id" = 'role_admin' AND rm."menu_id" = m."id");

-- ============================================================
-- 为已存在的 admin 用户补绑 ADMIN 角色（新建库由 Java 登录初始化时绑定）
-- ============================================================
DELETE FROM "public"."user_role" ur
USING "public"."user" u
WHERE ur."user_id" = u."id" AND u."username" = 'admin';

INSERT INTO "public"."user_role" ("id", "user_id", "role_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), u."id", 'role_admin', 'system', 'system', now(), now(), 0
FROM "public"."user" u
WHERE u."username" = 'admin';

-- ########## 来源迁移: V16__rbac_seed_data_full.sql ##########

-- ----------------------------
-- V16: 补全前端全量业务菜单 + 按钮权限
-- V15 已插入系统管理菜单（menu_system/menu_role/menu_menu）及角色，此处补全其余 10 个一级菜单 + 按钮权限
-- ----------------------------

-- ============================================================
-- 一级菜单（对应前端 MainLayout.getDefaultMenus() 的菜单项）
-- ============================================================

INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('menu_dashboard', '工作台', 'dashboard', '/dashboard', 'DashboardView', 'DataBoard', NULL, 1, 'menu', 'active', 'system', 'system', now(), now(), 0),
('menu_model', '模型管理', 'model', '/model', 'ModelView', 'Connection', NULL, 2, 'menu', 'active', 'system', 'system', now(), now(), 0),
('menu_knowledge', '知识库', 'knowledge', '/knowledge', 'KnowledgeView', 'Files', NULL, 3, 'menu', 'active', 'system', 'system', now(), now(), 0),
('menu_tool', '工具插件', 'tool', '/tool', 'ToolView', 'Tools', NULL, 4, 'menu', 'active', 'system', 'system', now(), now(), 0),
('menu_prompt', '提示词/Skill', 'prompt', '/prompt', 'PromptView', 'EditPen', NULL, 5, 'menu', 'active', 'system', 'system', now(), now(), 0),
('menu_workflow', '工作流', 'workflow', '/workflow', 'WorkflowView', 'Share', NULL, 6, 'menu', 'active', 'system', 'system', now(), now(), 0),
('menu_application', '智能应用', 'application', '/application', 'ApplicationView', 'VideoPlay', NULL, 7, 'menu', 'active', 'system', 'system', now(), now(), 0),
('menu_channel', '渠道接入', 'channel', '/channel', 'ChannelView', 'DataLine', NULL, 8, 'menu', 'active', 'system', 'system', now(), now(), 0),
('menu_observability', '可观测性', 'observability', '/observability', 'ObservabilityView', 'Histogram', NULL, 9, 'menu', 'active', 'system', 'system', now(), now(), 0),
('menu_file', '文件管理', 'file', '/file', 'FileView', 'Folder', NULL, 10, 'menu', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：模型管理
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_model_query',  '查询模型', 'model:query',  NULL, NULL, NULL, 'menu_model', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_model_add',    '新增模型', 'model:add',    NULL, NULL, NULL, 'menu_model', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_model_update', '修改模型', 'model:update', NULL, NULL, NULL, 'menu_model', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_model_delete', '删除模型', 'model:delete', NULL, NULL, NULL, 'menu_model', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：知识库
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_knowledge_query',  '查询知识库', 'knowledge:query',  NULL, NULL, NULL, 'menu_knowledge', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_knowledge_add',    '新增知识库', 'knowledge:add',    NULL, NULL, NULL, 'menu_knowledge', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_knowledge_update', '修改知识库', 'knowledge:update', NULL, NULL, NULL, 'menu_knowledge', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_knowledge_delete', '删除知识库', 'knowledge:delete', NULL, NULL, NULL, 'menu_knowledge', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：工具插件
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_tool_query',  '查询工具', 'tool:query',  NULL, NULL, NULL, 'menu_tool', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_tool_add',    '新增工具', 'tool:add',    NULL, NULL, NULL, 'menu_tool', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_tool_update', '修改工具', 'tool:update', NULL, NULL, NULL, 'menu_tool', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_tool_delete', '删除工具', 'tool:delete', NULL, NULL, NULL, 'menu_tool', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：提示词/Skill
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_prompt_query',  '查询提示词', 'prompt:query',  NULL, NULL, NULL, 'menu_prompt', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_prompt_add',    '新增提示词', 'prompt:add',    NULL, NULL, NULL, 'menu_prompt', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_prompt_update', '修改提示词', 'prompt:update', NULL, NULL, NULL, 'menu_prompt', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_prompt_delete', '删除提示词', 'prompt:delete', NULL, NULL, NULL, 'menu_prompt', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：工作流
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_workflow_query',  '查询工作流', 'workflow:query',  NULL, NULL, NULL, 'menu_workflow', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_workflow_add',    '新增工作流', 'workflow:add',    NULL, NULL, NULL, 'menu_workflow', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_workflow_update', '修改工作流', 'workflow:update', NULL, NULL, NULL, 'menu_workflow', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_workflow_delete', '删除工作流', 'workflow:delete', NULL, NULL, NULL, 'menu_workflow', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：智能应用
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_application_query',  '查询应用', 'application:query',  NULL, NULL, NULL, 'menu_application', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_application_add',    '新增应用', 'application:add',    NULL, NULL, NULL, 'menu_application', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_application_update', '修改应用', 'application:update', NULL, NULL, NULL, 'menu_application', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_application_delete', '删除应用', 'application:delete', NULL, NULL, NULL, 'menu_application', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：渠道接入
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_channel_query',  '查询渠道', 'channel:query',  NULL, NULL, NULL, 'menu_channel', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_channel_add',    '新增渠道', 'channel:add',    NULL, NULL, NULL, 'menu_channel', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_channel_update', '修改渠道', 'channel:update', NULL, NULL, NULL, 'menu_channel', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_channel_delete', '删除渠道', 'channel:delete', NULL, NULL, NULL, 'menu_channel', 4, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：可观测性
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_observability_query', '查询监控', 'observability:query', NULL, NULL, NULL, 'menu_observability', 1, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ===========================================================
-- 按钮权限：文件管理
-- ===========================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_file_query',  '查询文件', 'file:query',  NULL, NULL, NULL, 'menu_file', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_file_upload', '上传文件', 'file:upload', NULL, NULL, NULL, 'menu_file', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_file_delete', '删除文件', 'file:delete', NULL, NULL, NULL, 'menu_file', 3, 'button', 'active', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ===========================================================
-- 管理员角色关联所有新增的菜单/按钮权限
-- ===========================================================
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', m."id", 'system', 'system', now(), now(), 0
FROM "public"."menu" m
WHERE m."deleted" = 0
  AND NOT EXISTS (SELECT 1 FROM "public"."role_menu" rm WHERE rm."role_id" = 'role_admin' AND rm."menu_id" = m."id");

-- ########## 来源迁移: V17__menu_grouping.sql ##########

-- 菜单归类：新增一级分组目录，现有一级菜单挂载为二级菜单
-- 分组：模型与知识 / 应用与流程 / 系统与运维（工作台保持一级独立）
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('grp_ai',   '模型知识', 'group:ai',   NULL, NULL, 'Cpu',       NULL, 2, 'directory', 'active', 'system', 'system', now(), now(), 0),
('grp_app',  '应用流程', 'group:app',  NULL, NULL, 'Promotion', NULL, 3, 'directory', 'active', 'system', 'system', now(), now(), 0),
('grp_ops',  '系统运维', 'group:ops',  NULL, NULL, 'Setting',   NULL, 4, 'directory', 'active', 'system', 'system', now(), now(), 0);

-- 模型与知识：模型管理 / 知识库 / 工具插件 / 提示词与 Skill
UPDATE "public"."menu" SET "parent_id" = 'grp_ai'  WHERE "id" IN ('menu_model', 'menu_knowledge', 'menu_tool', 'menu_prompt');
-- 应用与流程：工作流 / 智能应用 / 渠道接入
UPDATE "public"."menu" SET "parent_id" = 'grp_app' WHERE "id" IN ('menu_workflow', 'menu_application', 'menu_channel');
-- 系统与运维：可观测性 / 文件管理 / 系统设置
UPDATE "public"."menu" SET "parent_id" = 'grp_ops' WHERE "id" IN ('menu_observability', 'menu_file', 'menu_system');

-- 将新分组目录分配给管理员角色
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', m."id", 'system', 'system', now(), now(), 0
FROM "public"."menu" m
WHERE m."id" IN ('grp_ai', 'grp_app', 'grp_ops')
  AND NOT EXISTS (SELECT 1 FROM "public"."role_menu" rm WHERE rm."role_id" = 'role_admin' AND rm."menu_id" = m."id");


-- ########## 来源迁移: V18__remove_system_menu_dir.sql ##########

-- 移除系统设置目录：角色管理 / 菜单管理直接挂到「系统与运维」分组下
UPDATE "public"."menu" SET "parent_id" = 'grp_ops' WHERE "id" IN ('menu_role', 'menu_menu');
UPDATE "public"."menu" SET "deleted" = 1, "status" = 'disabled' WHERE "id" = 'menu_system';


-- ########## 来源迁移: V19__application_version.sql ##########

-- 应用版本管理表
CREATE TABLE IF NOT EXISTS "application_version" (
    "id" VARCHAR(36) NOT NULL,
    "application_id" VARCHAR(36) NOT NULL,
    "version" INT NOT NULL DEFAULT 1,
    "name" VARCHAR(255),
    "description" TEXT,
    "type" VARCHAR(32),
    "model_id" VARCHAR(36),
    "knowledge_base_ids" TEXT,
    "prompt_template_id" VARCHAR(36),
    "skill_ids" TEXT,
    "rule_ids" TEXT,
    "memory_enabled" BOOLEAN DEFAULT FALSE,
    "max_turns" INT DEFAULT 20,
    "temperature" DOUBLE PRECISION DEFAULT 0.7,
    "config" TEXT,
    "suggestions" TEXT,
    "icon" VARCHAR(255),
    "snapshot" TEXT NOT NULL,
    "publish_log" TEXT,
    "publish_by" VARCHAR(255),
    "create_by" VARCHAR(50),
    "update_by" VARCHAR(50),
    "create_time" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" INT DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "idx_app_version_app_id" ON "application_version" ("application_id");
CREATE INDEX IF NOT EXISTS "idx_app_version_create_time" ON "application_version" ("create_time");

COMMENT ON TABLE "application_version" IS '应用版本快照（支持版本回滚）';
COMMENT ON COLUMN "application_version"."version" IS '版本号，从1递增';
COMMENT ON COLUMN "application_version"."snapshot" IS '发布时完整应用配置快照（JSON）';
COMMENT ON COLUMN "application_version"."publish_log" IS '发布说明';
COMMENT ON COLUMN "application_version"."publish_by" IS '发布人';

-- ########## 来源迁移: V20__long_term_memory.sql ##########

-- 长期记忆表
CREATE TABLE IF NOT EXISTS "long_term_memory" (
    "id" VARCHAR(36) NOT NULL,
    "user_id" VARCHAR(255) NOT NULL,
    "application_id" VARCHAR(36) NOT NULL,
    "dimension" VARCHAR(32) NOT NULL,
    "content" TEXT NOT NULL,
    "confidence" DOUBLE PRECISION DEFAULT 0.8,
    "source" VARCHAR(64) DEFAULT 'inferred',
    "trigger_count" INT DEFAULT 0,
    "last_triggered_at" TIMESTAMP,
    "is_active" BOOLEAN DEFAULT TRUE,
    "create_by" VARCHAR(50),
    "update_by" VARCHAR(50),
    "create_time" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" INT DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "idx_ltm_user_app" ON "long_term_memory" ("user_id", "application_id");
CREATE INDEX IF NOT EXISTS "idx_ltm_dimension" ON "long_term_memory" ("dimension");
CREATE INDEX IF NOT EXISTS "idx_ltm_is_active" ON "long_term_memory" ("is_active");

COMMENT ON TABLE "long_term_memory" IS '长期记忆（基于用户+应用维度的画像）';
COMMENT ON COLUMN "long_term_memory"."dimension" IS '记忆维度：preference/background/convention/goal';
COMMENT ON COLUMN "long_term_memory"."source" IS '记忆来源：inferred 推断 / explicit 显式 / import 导入';

-- ########## 来源迁移: V21__add_missing_columns.sql ##########

-- 补齐 V19/V20 迁移后修改中新增的 BaseEntity 字段列
-- 如果数据库是先建表后才补充的 DDL 列定义，Flyway 不会重跑旧迁移，需本次追加

-- application_version 补齐
ALTER TABLE "application_version" ADD COLUMN IF NOT EXISTS "create_by"  VARCHAR(50);
ALTER TABLE "application_version" ADD COLUMN IF NOT EXISTS "update_by"  VARCHAR(50);

-- long_term_memory 补齐
ALTER TABLE "long_term_memory" ADD COLUMN IF NOT EXISTS "create_by"  VARCHAR(50);
ALTER TABLE "long_term_memory" ADD COLUMN IF NOT EXISTS "update_by"  VARCHAR(50);

-- ########## 来源迁移: V23__llm_trace_table.sql ##########

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


-- ########## 来源迁移: V24__add_tool_fields.sql ##########

-- 新增 tool_type 字段到 tool 表（CUSTOM / HTTP / MCP / SKILL / PLUGIN）
ALTER TABLE "tool" ADD COLUMN IF NOT EXISTS "tool_type" VARCHAR(50);

-- 新增 tool_ids 字段到 application 表（工具 ID 列表 JSON）
ALTER TABLE "application" ADD COLUMN IF NOT EXISTS "tool_ids" TEXT;

-- ########## 来源迁移: V25__eval_and_index_enhance.sql ##########

-- ----------------------------
-- V25: 评估体系 + 多粒度索引 + Agentic RAG
-- ----------------------------

-- ==================== 多粒度索引 ====================

-- knowledge_document 增加文档摘要及摘要向量列
ALTER TABLE "public"."knowledge_document"
    ADD COLUMN IF NOT EXISTS "summary" TEXT;
ALTER TABLE "public"."knowledge_document"
    ADD COLUMN IF NOT EXISTS "summary_embedding" vector(1024);

COMMENT ON COLUMN "public"."knowledge_document"."summary" IS '文档摘要（LLM 生成，用于 two-stage 检索第一阶段）';
COMMENT ON COLUMN "public"."knowledge_document"."summary_embedding" IS '文档摘要的向量表示';

-- knowledge_document 摘要向量索引
CREATE INDEX IF NOT EXISTS "knowledge_document_summary_embedding_idx"
    ON "public"."knowledge_document" USING ivfflat ("summary_embedding" vector_cosine_ops) WITH (lists = 50);

-- knowledge_base 增加检索模式
ALTER TABLE "public"."knowledge_base"
    ADD COLUMN IF NOT EXISTS "search_mode" VARCHAR(20) NOT NULL DEFAULT 'simple';

COMMENT ON COLUMN "public"."knowledge_base"."search_mode" IS '检索模式：simple（段落级）/ two_stage（摘要→段落两级）';

-- ==================== Agentic RAG ====================

-- application 增加 RAG 模式
ALTER TABLE "public"."application"
    ADD COLUMN IF NOT EXISTS "rag_mode" VARCHAR(20) NOT NULL DEFAULT 'simple';

COMMENT ON COLUMN "public"."application"."rag_mode" IS 'RAG 模式：simple（预处理注入）/ agentic（LLM 自主检索）';

-- ==================== 评估体系 ====================

-- eval_dataset 评估数据集
CREATE TABLE IF NOT EXISTS "public"."eval_dataset" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "description" text,
    "knowledge_base_id" varchar(50),
    "case_count" int4 NOT NULL DEFAULT 0,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "eval_dataset_pkey" PRIMARY KEY ("id")
);

-- eval_case 评估用例
CREATE TABLE IF NOT EXISTS "public"."eval_case" (
    "id" varchar(50) NOT NULL,
    "dataset_id" varchar(50) NOT NULL,
    "question" text NOT NULL,
    "expected_answer" text,
    "reference_docs" text,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "eval_case_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "eval_case_dataset_idx" ON "public"."eval_case" USING btree ("dataset_id");

-- eval_run 评估运行记录
CREATE TABLE IF NOT EXISTS "public"."eval_run" (
    "id" varchar(50) NOT NULL,
    "dataset_id" varchar(50) NOT NULL,
    "config_snapshot" text,
    "status" varchar(20) NOT NULL DEFAULT 'pending',
    "start_time" timestamp(6),
    "end_time" timestamp(6),
    "summary" text,
    "results" text,
    "error_message" text,
    "progress" int4 NOT NULL DEFAULT 0,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "eval_run_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "eval_run_dataset_idx" ON "public"."eval_run" USING btree ("dataset_id");

-- ########## 来源迁移: V26__eval_menu.sql ##########

-- CangJie Agent V26: 评估体系菜单 + 按钮权限
-- 在"系统运维"分组下新增"评估体系"菜单，并分配管理员权限

INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('menu_eval', '评估体系', 'eval', '/observability/eval', 'EvalView', 'TrendCharts', 'grp_ops', 9, 'menu', 'active', 'system', 'system', now(), now(), 0);

-- 按钮权限
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_eval_query',  '查询评估', 'eval:query',  NULL, NULL, NULL, 'menu_eval', 1, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_eval_create', '创建评估', 'eval:create', NULL, NULL, NULL, 'menu_eval', 2, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_eval_run',    '运行评估', 'eval:run',    NULL, NULL, NULL, 'menu_eval', 3, 'button', 'active', 'system', 'system', now(), now(), 0),
('btn_eval_delete', '删除评估', 'eval:delete', NULL, NULL, NULL, 'menu_eval', 4, 'button', 'active', 'system', 'system', now(), now(), 0);

-- 将菜单与按钮分配给管理员角色
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', m."id", 'system', 'system', now(), now(), 0
FROM "public"."menu" m
WHERE m."id" IN ('menu_eval', 'btn_eval_query', 'btn_eval_create', 'btn_eval_run', 'btn_eval_delete')
  AND NOT EXISTS (SELECT 1 FROM "public"."role_menu" rm WHERE rm."role_id" = 'role_admin' AND rm."menu_id" = m."id");


-- ########## 来源迁移: V27__pgroonga_fulltext.sql ##########

-- ----------------------------
-- V27: 全文检索引擎从 tsvector 迁移到 pgroonga
-- 前置条件：DBA 已安装并初始化 pgroonga 扩展
-- ----------------------------

-- 1. 启用 pgroonga 扩展
CREATE EXTENSION IF NOT EXISTS pgroonga;

-- 2. 在 content 列上创建 pgroonga 索引（替代原来的 ts_vector GIN 索引）
CREATE INDEX IF NOT EXISTS "knowledge_paragraph_content_pgroonga_idx"
    ON "public"."knowledge_paragraph" USING pgroonga ("content");

-- 3. 删除旧的 tsvector GIN 索引
DROP INDEX IF EXISTS "public"."knowledge_paragraph_ts_vector_idx";

-- 4. 删除 ts_vector 列（pgroonga 直接索引 content，不再需要此列）
ALTER TABLE "public"."knowledge_paragraph" DROP COLUMN IF EXISTS "ts_vector";

-- ########## 来源迁移: V28__platform_enhance.sql ##########

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


-- ########## 来源迁移: V29__maxkb_features.sql ##########

-- ----------------------------
-- CangJie Agent V29: 企业级能力补全（参考 MaxKB）
-- 1. 对话消息反馈与人工标注
-- 2. 知识库问题管理与问题-段落关联
-- ----------------------------

-- 1. 对话消息：反馈（点赞/点踩）与人工标注（修正答案）
ALTER TABLE "public"."chat_message" ADD COLUMN IF NOT EXISTS "feedback" varchar(20) NOT NULL DEFAULT 'none';
ALTER TABLE "public"."chat_message" ADD COLUMN IF NOT EXISTS "annotation" text;
ALTER TABLE "public"."chat_message" ADD COLUMN IF NOT EXISTS "annotate_by" varchar(50);
ALTER TABLE "public"."chat_message" ADD COLUMN IF NOT EXISTS "annotate_time" timestamp(6);
COMMENT ON COLUMN "public"."chat_message"."feedback" IS '用户反馈：none / like / dislike';
COMMENT ON COLUMN "public"."chat_message"."annotation" IS '人工标注的修正答案';

-- 2. 知识库问题：一个段落可关联多个常见问题，检索时走问题路召回
CREATE TABLE IF NOT EXISTS "public"."knowledge_problem" (
    "id" varchar(50) NOT NULL,
    "knowledge_base_id" varchar(50) NOT NULL,
    "content" varchar(1000) NOT NULL,
    "source" varchar(20) NOT NULL DEFAULT 'manual',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "knowledge_problem_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "knowledge_problem_kb_idx" ON "public"."knowledge_problem" USING btree ("knowledge_base_id");

-- 3. 问题-段落关联
CREATE TABLE IF NOT EXISTS "public"."problem_paragraph" (
    "id" varchar(50) NOT NULL,
    "problem_id" varchar(50) NOT NULL,
    "paragraph_id" varchar(50) NOT NULL,
    "knowledge_base_id" varchar(50) NOT NULL,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "problem_paragraph_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "problem_paragraph_problem_idx" ON "public"."problem_paragraph" USING btree ("problem_id");
CREATE INDEX IF NOT EXISTS "problem_paragraph_paragraph_idx" ON "public"."problem_paragraph" USING btree ("paragraph_id");
CREATE INDEX IF NOT EXISTS "problem_paragraph_kb_idx" ON "public"."problem_paragraph" USING btree ("knowledge_base_id");


-- ########## 来源迁移: V30__memory_enhance.sql ##########

-- ----------------------------
-- CangJie Agent V30: 记忆体系增强
-- 1. 会话记忆：会话滚动摘要
-- 2. 场景记忆：长期记忆区分用户画像/场景事实，场景记忆绑定会话
-- ----------------------------

-- 1. 会话摘要（滚动更新，历史加载时注入）
ALTER TABLE "public"."chat_session" ADD COLUMN IF NOT EXISTS "summary" text;
ALTER TABLE "public"."chat_session" ADD COLUMN IF NOT EXISTS "summary_msg_count" int4 NOT NULL DEFAULT 0;
COMMENT ON COLUMN "public"."chat_session"."summary" IS '会话滚动摘要（覆盖已摘要的历史消息）';
COMMENT ON COLUMN "public"."chat_session"."summary_msg_count" IS '已纳入摘要的消息数';

-- 2. 长期记忆扩展：记忆类型 + 场景（会话）绑定
ALTER TABLE "public"."long_term_memory" ADD COLUMN IF NOT EXISTS "memory_type" varchar(20) NOT NULL DEFAULT 'user';
ALTER TABLE "public"."long_term_memory" ADD COLUMN IF NOT EXISTS "session_id" varchar(50);
COMMENT ON COLUMN "public"."long_term_memory"."memory_type" IS '记忆类型：user 用户画像（跨会话）/ scene 场景事实（会话内）';
COMMENT ON COLUMN "public"."long_term_memory"."session_id" IS '场景记忆绑定的会话 ID';
CREATE INDEX IF NOT EXISTS "idx_ltm_session" ON "public"."long_term_memory" ("session_id");
CREATE INDEX IF NOT EXISTS "idx_ltm_type" ON "public"."long_term_memory" ("memory_type");


-- ########## 来源迁移: V31__template_bundle.sql ##########

-- ----------------------------
-- CangJie Agent V31: 模板体系升级（Bundle 套件模板 + 官方内置模板）
-- ----------------------------

ALTER TABLE "public"."application_template" ADD COLUMN IF NOT EXISTS "template_key" varchar(100);
ALTER TABLE "public"."application_template" ADD COLUMN IF NOT EXISTS "version" int4 NOT NULL DEFAULT 1;
ALTER TABLE "public"."application_template" ADD COLUMN IF NOT EXISTS "builtin" bool NOT NULL DEFAULT false;
COMMENT ON COLUMN "public"."application_template"."template_key" IS '模板唯一键（官方模板幂等导入用）';
COMMENT ON COLUMN "public"."application_template"."version" IS '模板版本';
COMMENT ON COLUMN "public"."application_template"."builtin" IS '是否官方内置模板';

CREATE UNIQUE INDEX IF NOT EXISTS "app_template_key_uk"
    ON "public"."application_template" ("template_key")
    WHERE "template_key" IS NOT NULL AND "deleted" = 0;

