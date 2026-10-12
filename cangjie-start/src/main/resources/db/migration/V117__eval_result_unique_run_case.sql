-- ----------------------------
-- CangJie Agent V117: 评估明细去重 + 同 run 同用例唯一约束
--
-- eval_result 是一条 case 一行的明细表，取消后重跑、写失败重试都可能让同一
-- (run_id, case_id) 落进多行。读取侧（indexByCase）取的是「最后一行」，报告列表却不按这个
-- 口径去重，于是同一个用例在报告里出现两次、均值与明细行数也对不上。
-- 这里先清历史重复，再加唯一约束，把口径钉在库里而不是靠调用方自觉。
--
-- 只建普通 btree 唯一索引：这张表由评测异步任务写，量级是「一次运行 × 用例数」，
-- 不存在线上主链路的写入锁问题，无需 CONCURRENTLY（Flyway 事务内也不允许）。
-- ----------------------------

-- 保留同 (run_id, case_id) 里最新的一行（create_time 最大，并列时取 id 大的），其余删除
-- 只比未软删的行：软删行已经退出约束范围，跟着一起删会把「删过的明细」也清掉
DELETE FROM "public"."eval_result" a
    USING "public"."eval_result" b
    WHERE a."run_id" = b."run_id"
      AND a."case_id" = b."case_id"
      AND a."deleted" = 0
      AND b."deleted" = 0
      AND (a."create_time" < b."create_time"
           OR (a."create_time" = b."create_time" AND a."id" < b."id"));

-- 同一次运行的一条用例只能有一行读数（软删行不占用约束，删除后可重新写入）
CREATE UNIQUE INDEX IF NOT EXISTS "eval_result_run_case_uq"
    ON "public"."eval_result" USING btree ("run_id", "case_id")
    WHERE "deleted" = 0;

COMMENT ON INDEX "public"."eval_result_run_case_uq" IS '一次运行的一条用例只留一行读数；软删行不受约束，避免报告出现同用例重复行';

-- 已有的 (run_id, case_id) 复合索引现在只是范围扫描入口，唯一索引接管等值查询后它仍服务
-- 「按 run 取全部明细并按 case 排序」的读取路径，保留。
