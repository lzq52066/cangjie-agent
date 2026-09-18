#!/usr/bin/env bash
# ============================================================
# 04 - 启动全部服务
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "[04] 未找到 .env，请先执行 ./02-init.sh" >&2
  exit 1
fi

echo "[04] 启动服务..."
docker compose up -d

echo "[04] 等待后端健康（最多约 3 分钟）..."
for i in $(seq 1 36); do
  status=$(docker inspect --format '{{.State.Health.Status}}' cangjie-agent 2>/dev/null || echo "starting")
  if [ "$status" = "healthy" ]; then
    echo "[04] 后端已就绪"
    break
  fi
  if [ "$status" = "unhealthy" ]; then
    echo "[04] 后端健康检查失败，请查看日志：./92-logs.sh" >&2
    exit 1
  fi
  printf '.'
  sleep 5
done
echo ""

WEB_PORT=$(grep -E '^WEB_PORT=' .env | cut -d= -f2)
echo "[04] 启动完成："
echo "     管理后台: http://<服务器IP>:${WEB_PORT:-80}/admin/"
echo "     对话端  : http://<服务器IP>:${WEB_PORT:-80}/chat/"
echo "     默认账号: admin（密码见 .env 的 SYSTEM_DEFAULT_PASSWORD）"
