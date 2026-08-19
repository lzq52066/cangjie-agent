-- 补齐 V19/V20 迁移后修改中新增的 BaseEntity 字段列
-- 如果数据库是先建表后才补充的 DDL 列定义，Flyway 不会重跑旧迁移，需本次追加

-- application_version 补齐
ALTER TABLE "application_version" ADD COLUMN IF NOT EXISTS "create_by"  VARCHAR(50);
ALTER TABLE "application_version" ADD COLUMN IF NOT EXISTS "update_by"  VARCHAR(50);

-- long_term_memory 补齐
ALTER TABLE "long_term_memory" ADD COLUMN IF NOT EXISTS "create_by"  VARCHAR(50);
ALTER TABLE "long_term_memory" ADD COLUMN IF NOT EXISTS "update_by"  VARCHAR(50);