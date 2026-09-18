-- 长期记忆语义去重/合并：为 memory 增加向量列
-- 使用不带维度的 vector 类型，兼容不同 embedding 模型维度；查询时按 embedding_dim 过滤同维度向量。
-- 记忆单用户数据量小（容量上限默认 100 条/应用），向量近邻走 user_id 过滤后顺序计算，不建 ANN 索引。
-- 存量记忆不回填（embedding 为 NULL，不参与语义查重），仅对新写入的记忆生效。
ALTER TABLE "memory" ADD COLUMN IF NOT EXISTS "embedding" vector;
ALTER TABLE "memory" ADD COLUMN IF NOT EXISTS "embedding_dim" int4;

COMMENT ON COLUMN "memory"."embedding" IS '记忆内容向量（语义去重/合并用，存量数据可能为空）';
COMMENT ON COLUMN "memory"."embedding_dim" IS '向量维度，不同维度向量不可比较，查询时按此过滤';
