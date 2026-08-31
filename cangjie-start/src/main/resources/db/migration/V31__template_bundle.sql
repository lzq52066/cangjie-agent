-- ----------------------------
-- CangJie Agent V31: 模板体系升级（Bundle 套件模板 + 官方内置模板）
-- ----------------------------

ALTER TABLE "public"."application_template" ADD COLUMN IF NOT EXISTS "template_key" varchar(100);
ALTER TABLE "public"."application_template" ADD COLUMN IF NOT EXISTS "version" int4 NOT NULL DEFAULT 1;
ALTER TABLE "public"."application_template" ADD COLUMN IF NOT EXISTS "builtin" bool NOT NULL DEFAULT false;
COMMENT ON COLUMN "public"."application_template"."template_key" IS '模板唯一键（官方模板幂等导入用）';
COMMENT ON COLUMN "public"."application_template"."version" IS '模板版本';
COMMENT ON COLUMN "public"."application_template"."builtin" IS '是否官方内置模板';

CREATE UNIQUE INDEX IF NOT EXISTS "app_template_key_uk"
    ON "public"."application_template" ("template_key")
    WHERE "template_key" IS NOT NULL AND "deleted" = 0;
