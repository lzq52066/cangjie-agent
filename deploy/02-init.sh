#!/usr/bin/env bash
# ============================================================
# 02 - 初始化：生成 .env 并校验制品是否存在
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

# 1. 生成 .env（不覆盖已有配置）
if [ ! -f .env ]; then
  cp .env.example .env
  echo "[02] 已生成 .env（首次初始化，请按需修改密钥与 MINIO_PUBLIC_ENDPOINT）"
else
  echo "[02] 已存在 .env，跳过生成"
fi

# 2. 校验后端制品
if [ ! -f backend/cangjie-start.jar ]; then
  echo "[02] 错误：未找到 backend/cangjie-start.jar" >&2
  echo "     请用本地 pack.ps1 / pack.sh 打包，或手动拷贝 cangjie-start/target/cangjie-start.jar 到 backend/ 目录" >&2
  exit 1
fi
echo "[02] 后端制品: backend/cangjie-start.jar ($(du -h backend/cangjie-start.jar | cut -f1))"

# 3. 校验前端制品
if [ ! -d frontend/dist/admin ] || [ ! -d frontend/dist/chat ]; then
  echo "[02] 错误：未找到 frontend/dist/admin 或 frontend/dist/chat" >&2
  echo "     请执行前端构建（npm run build）后，将 dist 目录内容拷贝到 frontend/dist/" >&2
  exit 1
fi
echo "[02] 前端制品: frontend/dist（admin + chat）"

echo "[02] 完成。请确认 .env 中的 MINIO_PUBLIC_ENDPOINT 已改为本机可访问地址"
