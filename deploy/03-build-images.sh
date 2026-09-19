#!/usr/bin/env bash
# ============================================================
# 03 - 构建镜像（PostgreSQL 双扩展 / 后端 / 前端）
# 说明：PostgreSQL 镜像以 groonga/pgroonga 为基础镜像，构建时只需联网拉基础镜像
#       与从 PGDG 源补装 pgvector；后端与前端镜像只打包已有制品，不编译源码
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "[03] 未找到 .env，请先执行 ./02-init.sh" >&2
  exit 1
fi

echo "[03] 开始构建镜像..."
if ! docker compose build; then
  echo "[03] 构建失败。若为拉取基础镜像超时（dial tcp ...: i/o timeout），请检查镜像加速是否生效：" >&2
  echo "     docker info | grep -A3 'Registry Mirrors'" >&2
  echo "     未生效可重跑 ./01-install-docker.sh（会探测可用加速地址并重启 Docker）" >&2
  exit 1
fi

echo "[03] 构建完成，镜像列表："
docker images | grep -E 'cangjie|REPOSITORY' || true
