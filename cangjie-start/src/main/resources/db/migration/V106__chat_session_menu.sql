-- ----------------------------
-- CangJie Agent V106: 会话记录菜单
-- chat_session / chat_message 此前只有对话侧读写，管理后台缺少审计入口；
-- 在「系统运维」分组下新增「会话记录」菜单并授予管理员。
-- ----------------------------

INSERT INTO "public"."menu" ("id", "name", "code", "path", "component", "icon", "parent_id", "sort", "type", "status", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT 'menu_session', '会话记录', 'session', '/session', 'SessionView', 'ChatDotRound', 'grp_ops', 8, 'menu', 'active', 'system', 'system', now(), now(), 0
WHERE NOT EXISTS (SELECT 1 FROM "public"."menu" WHERE "id" = 'menu_session');

INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), 'role_admin', 'menu_session', 'system', 'system', now(), now(), 0
WHERE NOT EXISTS (SELECT 1 FROM "public"."role_menu" WHERE "role_id" = 'role_admin' AND "menu_id" = 'menu_session');
