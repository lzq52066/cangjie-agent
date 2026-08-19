-- 菜单归类：新增一级分组目录，现有一级菜单挂载为二级菜单
-- 分组：模型与知识 / 应用与流程 / 系统与运维（工作台保持一级独立）
INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted") VALUES
('grp_ai',   '模型知识', 'group:ai',   NULL, NULL, 'Cpu',       NULL, 2, 'directory', 'active', 'default', 'system', 'system', now(), now(), 0),
('grp_app',  '应用流程', 'group:app',  NULL, NULL, 'Promotion', NULL, 3, 'directory', 'active', 'default', 'system', 'system', now(), now(), 0),
('grp_ops',  '系统运维', 'group:ops',  NULL, NULL, 'Setting',   NULL, 4, 'directory', 'active', 'default', 'system', 'system', now(), now(), 0);

-- 模型与知识：模型管理 / 知识库 / 工具插件 / 提示词与 Skill
UPDATE "public"."menu" SET "parent_id" = 'grp_ai'  WHERE "id" IN ('menu_model', 'menu_knowledge', 'menu_tool', 'menu_prompt');
-- 应用与流程：工作流 / 智能应用 / 渠道接入
UPDATE "public"."menu" SET "parent_id" = 'grp_app' WHERE "id" IN ('menu_workflow', 'menu_application', 'menu_channel');
-- 系统与运维：可观测性 / 文件管理 / 系统设置
UPDATE "public"."menu" SET "parent_id" = 'grp_ops' WHERE "id" IN ('menu_observability', 'menu_file', 'menu_system');

-- 将新分组目录分配给管理员角色
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "tenant_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', m."id", 'default', 'system', 'system', now(), now(), 0
FROM "public"."menu" m
WHERE m."id" IN ('grp_ai', 'grp_app', 'grp_ops')
  AND NOT EXISTS (SELECT 1 FROM "public"."role_menu" rm WHERE rm."role_id" = 'role_admin' AND rm."menu_id" = m."id");
