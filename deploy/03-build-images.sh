#!/usr/bin/env bash
# ============================================================
# 03 - 构建镜像（PostgreSQL 双扩展 / 后端 / 前端）
# 说明：PostgreSQL 镜像构建需联网（Groonga apt 源 + PGDG 源）
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "[03] 未找到 .env，请先执行 ./02-init.sh" >&2
  exit 1
fi

echo "[03] 开始构建镜像..."
docker compose build

echo "[03] 构建完成，镜像列表："
docker images | grep -E 'cangjie|REPOSITORY' || true
