-- 智能应用：新增建议问题配置（chat 入口欢迎页展示）
ALTER TABLE "public"."application" ADD COLUMN IF NOT EXISTS "suggestions" text;
