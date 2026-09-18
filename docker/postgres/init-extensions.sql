-- 容器首次初始化时由 docker-entrypoint 以超级用户（POSTGRES_USER）连接到 POSTGRES_DB 执行。
-- 预先创建扩展，后续 Flyway 迁移中的 CREATE EXTENSION IF NOT EXISTS 即可幂等通过。
CREATE EXTENSION IF NOT EXISTS "vector";
CREATE EXTENSION IF NOT EXISTS "pgroonga";
