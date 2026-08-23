-- CangJie Agent V26: 评估体系菜单 + 按钮权限
-- 在"系统运维"分组下新增"评估体系"菜单，并分配管理员权限
-- 注意：本表结构无 tenant_id（项目不启用多租户），插入时不要引用该列

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
