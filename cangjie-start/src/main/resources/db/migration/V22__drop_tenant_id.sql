-- 移除所有表中的多租户标识字段 tenant_id 及其索引
-- 本系统为单租户架构，不再需要 tenant_id 列

-- ============== 删除 tenant_id 索引 ==============
DROP INDEX IF EXISTS "user_tenant_id_idx";
DROP INDEX IF EXISTS "idx_knowledge_base_tenant";
DROP INDEX IF EXISTS "idx_prompt_template_tenant";
DROP INDEX IF EXISTS "idx_skill_tenant";
DROP INDEX IF EXISTS "idx_memory_tenant";
DROP INDEX IF EXISTS "idx_rule_tenant";
DROP INDEX IF EXISTS "idx_command_tenant";
DROP INDEX IF EXISTS "idx_tool_tenant";
DROP INDEX IF EXISTS "idx_plugin_instance_tenant";
DROP INDEX IF EXISTS "idx_workflow_tenant";
DROP INDEX IF EXISTS "idx_workflow_execution_tenant";
DROP INDEX IF EXISTS "idx_application_tenant";
DROP INDEX IF EXISTS "idx_chat_session_tenant";
DROP INDEX IF EXISTS "idx_chat_message_tenant";
DROP INDEX IF EXISTS "idx_channel_tenant";
DROP INDEX IF EXISTS "idx_channel_message_tenant";
DROP INDEX IF EXISTS "idx_file_record_tenant";
DROP INDEX IF EXISTS "idx_operation_log_tenant";
DROP INDEX IF EXISTS "idx_system_metric_tenant";
DROP INDEX IF EXISTS "idx_trace_record_tenant";
DROP INDEX IF EXISTS "idx_role_tenant";
DROP INDEX IF EXISTS "idx_menu_tenant";
DROP INDEX IF EXISTS "idx_user_role_tenant";
DROP INDEX IF EXISTS "idx_role_menu_tenant";

-- ============== 删除 tenant_id 列 ==============
ALTER TABLE "user"                 DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "system_setting"       DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "knowledge_base"       DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "knowledge_document"   DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "knowledge_paragraph"  DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "model_config"         DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "prompt_template"      DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "skill"                DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "memory"               DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "rule"                 DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "command"              DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "tool"                 DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "plugin_instance"      DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "workflow"             DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "workflow_execution"   DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "application"          DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "chat_session"         DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "chat_message"         DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "channel"              DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "channel_message"      DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "file_record"          DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "operation_log"        DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "system_metric"        DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "trace_record"         DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "role"                 DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "menu"                 DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "user_role"            DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "role_menu"            DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "application_version"  DROP COLUMN IF EXISTS "tenant_id";
ALTER TABLE "long_term_memory"     DROP COLUMN IF EXISTS "tenant_id";