-- 数据卷首次初始化时预装扩展，后续 Flyway 的 CREATE EXTENSION IF NOT EXISTS 幂等通过
CREATE EXTENSION IF NOT EXISTS "vector";
CREATE EXTENSION IF NOT EXISTS "pgroonga";
-- trace 正文关键词检索走 pg_trgm GIN（V116），数据卷初始化时先装好
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
