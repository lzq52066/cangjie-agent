-- ----------------------------
-- CangJie Agent V108: 用户管理菜单
-- user 表此前仅有登录/下拉选项接口，后台缺少用户管理入口；
-- 在「系统设置」目录下新增「用户管理」子菜单并授予管理员。
-- ----------------------------

INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT 'menu_user_manage', '用户管理', 'user_manage', '/system/user', 'system/UserView', 'User', 'menu_system', 1, 'menu', 'active', 'system', 'system', now(), now(), 0
WHERE NOT EXISTS (SELECT 1 FROM "public"."menu" WHERE "id" = 'menu_user_manage');

INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', 'menu_user_manage', 'system', 'system', now(), now(), 0
WHERE NOT EXISTS (SELECT 1 FROM "public"."role_menu" WHERE "role_id" = 'role_admin' AND "menu_id" = 'menu_user_manage');
