-- 移除系统设置目录：角色管理 / 菜单管理直接挂到「系统与运维」分组下
UPDATE "public"."menu" SET "parent_id" = 'grp_ops' WHERE "id" IN ('menu_role', 'menu_menu');
UPDATE "public"."menu" SET "deleted" = 1, "status" = 'disabled' WHERE "id" = 'menu_system';
