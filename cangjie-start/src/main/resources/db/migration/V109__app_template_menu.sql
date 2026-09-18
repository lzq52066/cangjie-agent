-- ----------------------------
-- CangJie Agent V109: 应用模板市场菜单
-- application_template 的后端接口与前端 api 封装此前已存在，但后台没有任何页面入口；
-- 在「应用流程」分组下新增「应用模板」菜单并授予管理员。
-- ----------------------------

INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT 'menu_app_template', '应用模板', 'app_template', '/template', 'TemplateView', 'Files', 'grp_app', 9, 'menu', 'active', 'system', 'system', now(), now(), 0
WHERE NOT EXISTS (SELECT 1 FROM "public"."menu" WHERE "id" = 'menu_app_template');

INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', 'menu_app_template', 'system', 'system', now(), now(), 0
WHERE NOT EXISTS (SELECT 1 FROM "public"."role_menu" WHERE "role_id" = 'role_admin' AND "menu_id" = 'menu_app_template');
