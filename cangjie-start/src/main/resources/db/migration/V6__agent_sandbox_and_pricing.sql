-- ----------------------------
-- CangJie Agent V6: 沙箱执行档位与模型计价
-- sandbox_profile 定义代码/脚本节点的隔离执行档位；
-- sandbox_binding 把工具/技能/应用绑定到档位；
-- model_pricing 用于把 token 折算成成本，支撑预算治理。
-- ----------------------------

CREATE TABLE IF NOT EXISTS "public"."sandbox_profile" (
    "id" varchar(50) NOT NULL,
    "name" varchar(200) NOT NULL,
    "executor" varchar(20) NOT NULL DEFAULT 'local',
    "image" varchar(500),
    "cpu_cores" numeric(6,2),
    "memory_mb" int4,
    "timeout_seconds" int4 NOT NULL DEFAULT 30,
    "output_limit_chars" int4 NOT NULL DEFAULT 8000,
    "network_enabled" int4 NOT NULL DEFAULT 0,
    "filesystem_enabled" int4 NOT NULL DEFAULT 0,
    "work_dir" varchar(500),
    "env_vars" text,
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "description" varchar(500),
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "sandbox_profile_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "sandbox_profile_executor_idx" ON "public"."sandbox_profile" USING btree ("executor");
CREATE INDEX IF NOT EXISTS "sandbox_profile_status_idx" ON "public"."sandbox_profile" USING btree ("status");

COMMENT ON TABLE "public"."sandbox_profile" IS '沙箱执行档位';
COMMENT ON COLUMN "public"."sandbox_profile"."executor" IS '执行器：local（进程内受限）/ docker（容器隔离）/ remote（远程执行器）';
COMMENT ON COLUMN "public"."sandbox_profile"."network_enabled" IS '是否放行网络：0 禁止 / 1 允许';
COMMENT ON COLUMN "public"."sandbox_profile"."filesystem_enabled" IS '是否放行文件读写：0 禁止 / 1 允许';

CREATE TABLE IF NOT EXISTS "public"."sandbox_binding" (
    "id" varchar(50) NOT NULL,
    "binding_type" varchar(20) NOT NULL,
    "binding_id" varchar(50) NOT NULL,
    "profile_id" varchar(50) NOT NULL,
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "sandbox_binding_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "sandbox_binding_type_id_idx"
    ON "public"."sandbox_binding" USING btree ("binding_type", "binding_id");

COMMENT ON TABLE "public"."sandbox_binding" IS '沙箱档位绑定（工具/技能/应用 -> 档位）';
COMMENT ON COLUMN "public"."sandbox_binding"."binding_type" IS '绑定对象类型：tool / skill / application';
COMMENT ON COLUMN "public"."sandbox_binding"."binding_id" IS '绑定对象 ID';

CREATE TABLE IF NOT EXISTS "public"."model_pricing" (
    "id" varchar(50) NOT NULL,
    "model_id" varchar(50),
    "model_name" varchar(100) NOT NULL,
    "currency" varchar(10) NOT NULL DEFAULT 'CNY',
    "input_price" numeric(12,6) NOT NULL DEFAULT 0,
    "output_price" numeric(12,6) NOT NULL DEFAULT 0,
    "price_unit" varchar(20) NOT NULL DEFAULT 'per_1k_tokens',
    "effective_time" timestamp(6),
    "status" varchar(20) NOT NULL DEFAULT 'active',
    "create_by" varchar(50),
    "update_by" varchar(50),
    "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "model_pricing_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "model_pricing_model_name_idx" ON "public"."model_pricing" USING btree ("model_name");
CREATE INDEX IF NOT EXISTS "model_pricing_status_idx" ON "public"."model_pricing" USING btree ("status");

COMMENT ON TABLE "public"."model_pricing" IS '模型计价（用于 run 成本估算与预算治理）';
COMMENT ON COLUMN "public"."model_pricing"."price_unit" IS '计价单位：per_1k_tokens / per_1m_tokens';
