-- ----------------------------
-- CangJie Agent V3: 模型与厂商彻底分离
-- API Key / Base URL 只由厂商（model_provider）持有，
-- model_config 不再存储任何凭证，只通过 provider_id 关联厂商。
-- 本脚本把存量模型上的凭证归并到对应厂商，再删除模型上的凭证列。
-- ----------------------------

-- 1) 存量模型凭证归并到厂商（密文原样搬迁，厂商已有值时不覆盖）
UPDATE "public"."model_provider" p
SET "api_key"  = COALESCE(p."api_key",
        (SELECT c."api_key" FROM "public"."model_config" c
          WHERE c."deleted" = 0 AND c."model_type" = p."code" AND c."api_key" IS NOT NULL
          ORDER BY c."update_time" DESC LIMIT 1)),
    "base_url" = COALESCE(p."base_url",
        (SELECT c."base_url" FROM "public"."model_config" c
          WHERE c."deleted" = 0 AND c."model_type" = p."code"
            AND c."base_url" IS NOT NULL AND c."base_url" <> ''
          ORDER BY c."update_time" DESC LIMIT 1))
WHERE p."deleted" = 0;

-- 2) 为厂商表中不存在的存量类型补建厂商（历史自定义类型兜底）
INSERT INTO "public"."model_provider"
    ("id", "name", "code", "base_url", "api_key", "status", "description",
     "create_by", "update_by", "create_time", "update_time", "deleted")
SELECT 'prov_' || t."model_type", t."model_type", t."model_type",
       (SELECT c."base_url" FROM "public"."model_config" c
         WHERE c."deleted" = 0 AND c."model_type" = t."model_type"
           AND c."base_url" IS NOT NULL AND c."base_url" <> ''
         ORDER BY c."update_time" DESC LIMIT 1),
       (SELECT c."api_key" FROM "public"."model_config" c
         WHERE c."deleted" = 0 AND c."model_type" = t."model_type" AND c."api_key" IS NOT NULL
         ORDER BY c."update_time" DESC LIMIT 1),
       'active', '由模型存量凭证归并生成', 'system', 'system', now(), now(), 0
FROM (SELECT DISTINCT "model_type" FROM "public"."model_config" WHERE "model_type" IS NOT NULL) t
ON CONFLICT ("id") DO NOTHING;

-- 3) 存量模型关联到同类型的厂商，保证凭证可用
UPDATE "public"."model_config" c
SET "provider_id" = p."id"
FROM "public"."model_provider" p
WHERE c."provider_id" IS NULL AND p."deleted" = 0 AND p."code" = c."model_type";

-- 4) 删除模型上的凭证列：厂商与模型彻底分离
ALTER TABLE "public"."model_config" DROP COLUMN IF EXISTS "api_key";
ALTER TABLE "public"."model_config" DROP COLUMN IF EXISTS "base_url";

COMMENT ON COLUMN "public"."model_config"."provider_id" IS '关联厂商 ID，API Key / Base URL 全部来自厂商';
COMMENT ON COLUMN "public"."model_provider"."base_url" IS '厂商 API Base URL，该厂商下所有模型共用';
COMMENT ON COLUMN "public"."model_provider"."api_key" IS '厂商 API Key（AES 密文），该厂商下所有模型共用';
