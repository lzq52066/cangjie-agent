-- ----------------------------
-- CangJie Agent V107: 系统设置页菜单入口
-- SystemView（/system 基础/邮件/对话设置）此前只能手敲 URL 访问；
-- menu_system 是目录容器，其下仅有角色、菜单两个子项，这里补「系统设置」子菜单。
-- ----------------------------

INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT 'menu_setting', '系统设置', 'setting', '/system/setting', 'system/SystemView', 'Tools', 'menu_system', 0, 'menu', 'active', 'system', 'system', now(), now(), 0
WHERE NOT EXISTS (SELECT 1 FROM "public"."menu" WHERE "id" = 'menu_setting');

INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', 'menu_setting', 'system', 'system', now(), now(), 0
WHERE NOT EXISTS (SELECT 1 FROM "public"."role_menu" WHERE "role_id" = 'role_admin' AND "menu_id" = 'menu_setting');
