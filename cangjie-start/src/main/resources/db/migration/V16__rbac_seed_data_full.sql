-- ----------------------------
-- V16: 补全前端全量业务菜单 + 按钮权限
-- V15 已插入系统管理菜单（menu_system/menu_role/menu_menu）及角色，此处补全其余 10 个一级菜单 + 按钮权限
-- ----------------------------

-- ============================================================
-- 一级菜单（对应前端 MainLayout.getDefaultMenus() 的菜单项）
-- ============================================================

INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('menu_dashboard', '工作台', 'dashboard', '/dashboard', 'DashboardView', 'DataBoard', NULL, 1, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0),
('menu_model', '模型管理', 'model', '/model', 'ModelView', 'Connection', NULL, 2, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0),
('menu_knowledge', '知识库', 'knowledge', '/knowledge', 'KnowledgeView', 'Files', NULL, 3, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0),
('menu_tool', '工具插件', 'tool', '/tool', 'ToolView', 'Tools', NULL, 4, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0),
('menu_prompt', '提示词/Skill', 'prompt', '/prompt', 'PromptView', 'EditPen', NULL, 5, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0),
('menu_workflow', '工作流', 'workflow', '/workflow', 'WorkflowView', 'Share', NULL, 6, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0),
('menu_application', '智能应用', 'application', '/application', 'ApplicationView', 'VideoPlay', NULL, 7, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0),
('menu_channel', '渠道接入', 'channel', '/channel', 'ChannelView', 'DataLine', NULL, 8, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0),
('menu_observability', '可观测性', 'observability', '/observability', 'ObservabilityView', 'Histogram', NULL, 9, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0),
('menu_file', '文件管理', 'file', '/file', 'FileView', 'Folder', NULL, 10, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：模型管理
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_model_query',  '查询模型', 'model:query',  NULL, NULL, NULL, 'menu_model', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_model_add',    '新增模型', 'model:add',    NULL, NULL, NULL, 'menu_model', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_model_update', '修改模型', 'model:update', NULL, NULL, NULL, 'menu_model', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_model_delete', '删除模型', 'model:delete', NULL, NULL, NULL, 'menu_model', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：知识库
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_knowledge_query',  '查询知识库', 'knowledge:query',  NULL, NULL, NULL, 'menu_knowledge', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_knowledge_add',    '新增知识库', 'knowledge:add',    NULL, NULL, NULL, 'menu_knowledge', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_knowledge_update', '修改知识库', 'knowledge:update', NULL, NULL, NULL, 'menu_knowledge', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_knowledge_delete', '删除知识库', 'knowledge:delete', NULL, NULL, NULL, 'menu_knowledge', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：工具插件
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_tool_query',  '查询工具', 'tool:query',  NULL, NULL, NULL, 'menu_tool', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_tool_add',    '新增工具', 'tool:add',    NULL, NULL, NULL, 'menu_tool', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_tool_update', '修改工具', 'tool:update', NULL, NULL, NULL, 'menu_tool', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_tool_delete', '删除工具', 'tool:delete', NULL, NULL, NULL, 'menu_tool', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：提示词/Skill
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_prompt_query',  '查询提示词', 'prompt:query',  NULL, NULL, NULL, 'menu_prompt', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_prompt_add',    '新增提示词', 'prompt:add',    NULL, NULL, NULL, 'menu_prompt', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_prompt_update', '修改提示词', 'prompt:update', NULL, NULL, NULL, 'menu_prompt', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_prompt_delete', '删除提示词', 'prompt:delete', NULL, NULL, NULL, 'menu_prompt', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：工作流
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_workflow_query',  '查询工作流', 'workflow:query',  NULL, NULL, NULL, 'menu_workflow', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_workflow_add',    '新增工作流', 'workflow:add',    NULL, NULL, NULL, 'menu_workflow', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_workflow_update', '修改工作流', 'workflow:update', NULL, NULL, NULL, 'menu_workflow', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_workflow_delete', '删除工作流', 'workflow:delete', NULL, NULL, NULL, 'menu_workflow', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：智能应用
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_application_query',  '查询应用', 'application:query',  NULL, NULL, NULL, 'menu_application', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_application_add',    '新增应用', 'application:add',    NULL, NULL, NULL, 'menu_application', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_application_update', '修改应用', 'application:update', NULL, NULL, NULL, 'menu_application', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_application_delete', '删除应用', 'application:delete', NULL, NULL, NULL, 'menu_application', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：渠道接入
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_channel_query',  '查询渠道', 'channel:query',  NULL, NULL, NULL, 'menu_channel', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_channel_add',    '新增渠道', 'channel:add',    NULL, NULL, NULL, 'menu_channel', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_channel_update', '修改渠道', 'channel:update', NULL, NULL, NULL, 'menu_channel', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_channel_delete', '删除渠道', 'channel:delete', NULL, NULL, NULL, 'menu_channel', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：可观测性
-- ============================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_observability_query', '查询监控', 'observability:query', NULL, NULL, NULL, 'menu_observability', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ===========================================================
-- 按钮权限：文件管理
-- ===========================================================
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_file_query',  '查询文件', 'file:query',  NULL, NULL, NULL, 'menu_file', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_file_upload', '上传文件', 'file:upload', NULL, NULL, NULL, 'menu_file', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_file_delete', '删除文件', 'file:delete', NULL, NULL, NULL, 'menu_file', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ===========================================================
-- 管理员角色关联所有新增的菜单/按钮权限
-- ===========================================================
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', m."id", 'default', 'system', 'system', now(), now(), 0
FROM "public"."menu" m
WHERE m."deleted" = 0
  AND NOT EXISTS (SELECT 1 FROM "public"."role_menu" rm WHERE rm."role_id" = 'role_admin' AND rm."menu_id" = m."id");