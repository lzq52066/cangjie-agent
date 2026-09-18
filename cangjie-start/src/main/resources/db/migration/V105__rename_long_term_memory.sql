-- ----------------------------
-- CangJie Agent V105: 记忆体系收敛
-- 1. 删除早期无人消费的旧 memory 表（仅 /prompt/memory 通用 CRUD 使用，对话链路从不读取）；
-- 2. 将真正生效的 long_term_memory 重命名为 memory（数据、索引一并保留）。
-- 用 to_regclass 判断保证全新库（V1 建表）与已部署库均可安全执行。
-- ----------------------------

-- 1. 删除旧 memory 表
DROP TABLE IF EXISTS "public"."memory" CASCADE;

-- 2. long_term_memory -> memory
DO $$
BEGIN
    IF to_regclass('public.long_term_memory') IS NOT NULL
       AND to_regclass('public.memory') IS NULL THEN
        ALTER TABLE "public"."long_term_memory" RENAME TO "memory";

        -- 索引随表保留，仅重命名为 memory 语义，避免遗留 ltm 前缀造成困惑
        ALTER INDEX IF EXISTS "idx_ltm_user_app" RENAME TO "idx_memory_user_app";
        ALTER INDEX IF EXISTS "idx_ltm_dimension" RENAME TO "idx_memory_dimension";
        ALTER INDEX IF EXISTS "idx_ltm_is_active" RENAME TO "idx_memory_is_active";
        ALTER INDEX IF EXISTS "idx_ltm_session" RENAME TO "idx_memory_session";
        ALTER INDEX IF EXISTS "idx_ltm_type" RENAME TO "idx_memory_type";

        COMMENT ON TABLE "public"."memory" IS '长期记忆（自动提取/手工录入，按用户+应用维度的画像与会话事实）';
    END IF;
END $$;
