-- ----------------------------
-- CangJie Agent V114: 会话记录并入可观测性
-- chat_session / chat_message 的后台审计与链路追踪、系统指标是同一批排障数据，
-- 拆成两个菜单入口后用户不知道该看哪个，现收为可观测性的「会话记录」tab。
-- 顺序必须是先迁授权再删菜单：只被授过「会话记录」的角色不能因此失去入口。
-- 沿用 V1 里 menu_system 的下线方式（deleted=1 + status=disabled），不物理删除。
-- ----------------------------

-- 1. 持有会话记录菜单的角色，补上可观测性授权（已有则跳过）
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT md5(random()::text || clock_timestamp()::text), rm.role_id, 'menu_observability', 'system', 'system', now(), now(), 0
FROM "public"."role_menu" rm
WHERE rm.menu_id = 'menu_session' AND rm.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM "public"."role_menu" x
                  WHERE x.role_id = rm.role_id AND x.menu_id = 'menu_observability' AND x.deleted = 0);

-- 2. 摘掉指向会话记录菜单的授权
UPDATE "public"."role_menu" SET "deleted" = 1, "update_by" = 'system', "update_time" = now()
WHERE "menu_id" = 'menu_session' AND "deleted" = 0;

-- 3. 下线菜单本身
UPDATE "public"."menu" SET "deleted" = 1, "status" = 'disabled', "update_by" = 'system', "update_time" = now()
WHERE "id" = 'menu_session' AND "deleted" = 0;
