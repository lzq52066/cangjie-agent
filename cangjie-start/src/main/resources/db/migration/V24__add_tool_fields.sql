-- 新增 tool_type 字段到 tool 表（CUSTOM / HTTP / MCP / SKILL / PLUGIN）
ALTER TABLE "tool" ADD COLUMN IF NOT EXISTS "tool_type" VARCHAR(50);

-- 新增 tool_ids 字段到 application 表（工具 ID 列表 JSON）
ALTER TABLE "application" ADD COLUMN IF NOT EXISTS "tool_ids" TEXT;