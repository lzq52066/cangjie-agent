-- ----------------------------
-- CangJie Agent V15: RBAC 种子数据
-- 初始化全量系统菜单树、按钮权限、内置角色、角色菜单授权
-- ----------------------------

-- ============================================================
-- 一级菜单（对应前端 MainLayout 中 getDefaultMenus 的菜单项）
-- ============================================================

-- 1. 工作台
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_dashboard', '工作台', 'dashboard', '/dashboard', 'DashboardView', 'DataBoard', NULL, 1, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 2. 模型管理
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_model', '模型管理', 'model', '/model', 'ModelView', 'Connection', NULL, 2, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 3. 知识库
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_knowledge', '知识库', 'knowledge', '/knowledge', 'KnowledgeView', 'Files', NULL, 3, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 4. 工具插件
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_tool', '工具插件', 'tool', '/tool', 'ToolView', 'Tools', NULL, 4, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 5. 提示词/Skill
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_prompt', '提示词/Skill', 'prompt', '/prompt', 'PromptView', 'EditPen', NULL, 5, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 6. 工作流
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_workflow', '工作流', 'workflow', '/workflow', 'WorkflowView', 'Share', NULL, 6, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 7. 智能应用
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_application', '智能应用', 'application', '/application', 'ApplicationView', 'VideoPlay', NULL, 7, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 8. 渠道接入
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_channel', '渠道接入', 'channel', '/channel', 'ChannelView', 'DataLine', NULL, 8, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 9. 可观测性
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_observability', '可观测性', 'observability', '/observability', 'ObservabilityView', 'Histogram', NULL, 9, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 10. 文件管理
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_file', '文件管理', 'file', '/file', 'FileView', 'Folder', NULL, 10, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 系统管理（目录 + 子菜单）
-- ============================================================

-- 11. 系统管理目录
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_system', '系统设置', 'system', '/system', NULL, 'Setting', NULL, 100, 'directory', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 系统管理子菜单：角色管理
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_role', '角色管理', 'system:role', '/system/role', 'system/RoleView', 'UserFilled', 'menu_system', 1, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 系统管理子菜单：菜单管理
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
VALUES ('menu_menu', '菜单管理', 'system:menu', '/system/menu', 'system/MenuView', 'Menu', 'menu_system', 2, 'menu', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：业务模块（标准 CRUD）
-- ============================================================

-- 模型管理按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_model_query',  '查询模型', 'model:query',  NULL, NULL, NULL, 'menu_model', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_model_add',    '新增模型', 'model:add',    NULL, NULL, NULL, 'menu_model', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_model_update', '修改模型', 'model:update', NULL, NULL, NULL, 'menu_model', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_model_delete', '删除模型', 'model:delete', NULL, NULL, NULL, 'menu_model', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 知识库按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_knowledge_query',  '查询知识库', 'knowledge:query',  NULL, NULL, NULL, 'menu_knowledge', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_knowledge_add',    '新增知识库', 'knowledge:add',    NULL, NULL, NULL, 'menu_knowledge', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_knowledge_update', '修改知识库', 'knowledge:update', NULL, NULL, NULL, 'menu_knowledge', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_knowledge_delete', '删除知识库', 'knowledge:delete', NULL, NULL, NULL, 'menu_knowledge', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 工具插件按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_tool_query',  '查询工具', 'tool:query',  NULL, NULL, NULL, 'menu_tool', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_tool_add',    '新增工具', 'tool:add',    NULL, NULL, NULL, 'menu_tool', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_tool_update', '修改工具', 'tool:update', NULL, NULL, NULL, 'menu_tool', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_tool_delete', '删除工具', 'tool:delete', NULL, NULL, NULL, 'menu_tool', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 提示词/Skill 按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_prompt_query',  '查询提示词', 'prompt:query',  NULL, NULL, NULL, 'menu_prompt', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_prompt_add',    '新增提示词', 'prompt:add',    NULL, NULL, NULL, 'menu_prompt', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_prompt_update', '修改提示词', 'prompt:update', NULL, NULL, NULL, 'menu_prompt', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_prompt_delete', '删除提示词', 'prompt:delete', NULL, NULL, NULL, 'menu_prompt', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 工作流按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_workflow_query',  '查询工作流', 'workflow:query',  NULL, NULL, NULL, 'menu_workflow', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_workflow_add',    '新增工作流', 'workflow:add',    NULL, NULL, NULL, 'menu_workflow', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_workflow_update', '修改工作流', 'workflow:update', NULL, NULL, NULL, 'menu_workflow', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_workflow_delete', '删除工作流', 'workflow:delete', NULL, NULL, NULL, 'menu_workflow', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 智能应用按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_application_query',  '查询应用', 'application:query',  NULL, NULL, NULL, 'menu_application', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_application_add',    '新增应用', 'application:add',    NULL, NULL, NULL, 'menu_application', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_application_update', '修改应用', 'application:update', NULL, NULL, NULL, 'menu_application', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_application_delete', '删除应用', 'application:delete', NULL, NULL, NULL, 'menu_application', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 渠道接入按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_channel_query',  '查询渠道', 'channel:query',  NULL, NULL, NULL, 'menu_channel', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_channel_add',    '新增渠道', 'channel:add',    NULL, NULL, NULL, 'menu_channel', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_channel_update', '修改渠道', 'channel:update', NULL, NULL, NULL, 'menu_channel', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_channel_delete', '删除渠道', 'channel:delete', NULL, NULL, NULL, 'menu_channel', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 可观测性按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_observability_query',  '查询监控', 'observability:query',  NULL, NULL, NULL, 'menu_observability', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 文件管理按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_file_query',  '查询文件', 'file:query',  NULL, NULL, NULL, 'menu_file', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_file_upload', '上传文件', 'file:upload', NULL, NULL, NULL, 'menu_file', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_file_delete', '删除文件', 'file:delete', NULL, NULL, NULL, 'menu_file', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 按钮权限：系统管理模块
-- ============================================================

-- 角色管理按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_role_add',    '新增角色', 'system:role:add',    NULL, NULL, NULL, 'menu_role', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_role_update', '修改角色', 'system:role:update', NULL, NULL, NULL, 'menu_role', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_role_delete', '删除角色', 'system:role:delete', NULL, NULL, NULL, 'menu_role', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_role_query',  '查询角色', 'system:role:query',  NULL, NULL, NULL, 'menu_role', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_role_assign', '分配用户', 'system:role:assign', NULL, NULL, NULL, 'menu_role', 5, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- 菜单管理按钮
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('btn_menu_add',    '新增菜单', 'system:menu:add',    NULL, NULL, NULL, 'menu_menu', 1, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_menu_update', '修改菜单', 'system:menu:update', NULL, NULL, NULL, 'menu_menu', 2, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_menu_delete', '删除菜单', 'system:menu:delete', NULL, NULL, NULL, 'menu_menu', 3, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_menu_query',  '查询菜单', 'system:menu:query',  NULL, NULL, NULL, 'menu_menu', 4, 'button', 'active', 'default', 'system', 'system', now(), now(), 0),
('btn_menu_assign', '分配菜单', 'system:menu:assign', NULL, NULL, NULL, 'menu_menu', 5, 'button', 'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 内置角色
-- ============================================================
INSERT INTO "public"."role" ("id", "name", "code", "description", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('role_admin', '超级管理员', 'ADMIN', '系统内置超级管理员，拥有全部权限', 'active', 'default', 'system', 'system', now(), now(), 0),
('role_user',  '普通用户',   'USER',  '系统内置普通用户角色',            'active', 'default', 'system', 'system', now(), now(), 0)
ON CONFLICT ("id") DO NOTHING;

-- ============================================================
-- 超级管理员角色关联所有菜单/按钮权限
-- ============================================================
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', m."id", 'default', 'system', 'system', now(), now(), 0
FROM "public"."menu" m
WHERE m."deleted" = 0
  AND NOT EXISTS (SELECT 1 FROM "public"."role_menu" rm WHERE rm."role_id" = 'role_admin' AND rm."menu_id" = m."id");

-- ============================================================
-- 为已存在的 admin 用户补绑 ADMIN 角色（新建库由 Java 登录初始化时绑定）
-- ============================================================
DELETE FROM "public"."user_role" ur
USING "public"."user" u
WHERE ur."user_id" = u."id" AND u."username" = 'admin';

INSERT INTO "public"."user_role" ("id", "user_id", "role_id", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), u."id", 'role_admin', 'default', 'system', 'system', now(), now(), 0
FROM "public"."user" u
WHERE u."username" = 'admin';