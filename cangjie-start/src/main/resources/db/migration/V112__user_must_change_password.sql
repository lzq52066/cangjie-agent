-- 首次登录强制改密：为 user 增加标记列
-- 新建库时由 Java 默认管理员初始化逻辑把内置 admin 置为 true，
-- 用户完成一次自助改密（PUT /api/admin/profile/password）后清零。
-- 存量用户默认 false，升级后无需强制改密。
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS "must_change_password" bool NOT NULL DEFAULT false;

COMMENT ON COLUMN "user"."must_change_password" IS '是否为待修改的初始密码：true 时首次登录被强制要求修改密码';
