-- ----------------------------
-- CangJie Agent V110: 修复系统设置/用户管理菜单挂载
-- V107/V108 将 menu_setting、menu_user_manage 的 parent_id 指向 menu_system，
-- 但 menu_system 早在 V18 已被软删除（deleted=1），后端不下发该目录，
-- 前端组装菜单树时找不到父节点，导致两个菜单以一级菜单形式展示。
-- 这里将其改挂到「系统运维」(grp_ops) 分组下，排在角色、菜单管理之后。
-- ----------------------------

UPDATE "public"."menu"
SET "parent_id" = 'grp_ops', "sort" = 3, "update_time" = now()
WHERE "id" = 'menu_setting';

UPDATE "public"."menu"
SET "parent_id" = 'grp_ops', "sort" = 4, "update_time" = now()
WHERE "id" = 'menu_user_manage';
