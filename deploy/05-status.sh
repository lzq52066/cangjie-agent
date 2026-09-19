#!/usr/bin/env bash
# ============================================================
# 05 - 查看服务状态、健康、访问地址与数据目录占用
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "[05] 未找到 .env，请先执行 ./02-init.sh" >&2
  exit 1
fi

env_get() {
  awk -F= -v k="$1" '$0 ~ "^" k "=" { sub("^" k "=", ""); print; exit }' .env
}

docker compose ps

echo ""
echo "---- 健康状态 ----"
for c in cangjie-postgres cangjie-redis cangjie-minio cangjie-backend cangjie-nginx; do
  s=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$c" 2>/dev/null || echo "未创建")
  printf "  %-22s %s\n" "$c" "$s"
done

echo ""
echo "---- 数据库扩展 ----"
docker exec cangjie-postgres psql -U "$(env_get POSTGRES_USER)" -d "$(env_get POSTGRES_DB)" \
  -tAc "SELECT extname||' '||extversion FROM pg_extension ORDER BY extname;" 2>/dev/null \
  | sed 's/^/  /' || echo "  （数据库未就绪）"

echo ""
echo "---- 访问地址 ----"
web_port=$(env_get WEB_PORT)
public_host=$(env_get MINIO_PUBLIC_ENDPOINT | sed -E 's|^https?://||; s|/.*$||; s|:[0-9]+$||')
[ -z "$public_host" ] && public_host=$(hostname -I 2>/dev/null | awk '{print $1}')
[ -z "$public_host" ] && public_host="<服务器IP>"
if [ "${web_port:-80}" = "80" ]; then
  web_base="http://${public_host}"
else
  web_base="http://${public_host}:${web_port}"
fi
echo "  管理后台    : ${web_base}/admin/"
echo "  对话端      : ${web_base}/chat/"
echo "  MinIO 控制台: ${web_base}/minio-console/"
echo "  对外端口    : 仅 ${web_port:-80}（PG/Redis/MinIO 仅容器内网可达）"

echo ""
echo "---- 数据目录（宿主机可直接备份） ----"
for d in postgres redis minio logs; do
  if [ -d "data/$d" ]; then
    printf "  %-16s %s\n" "data/$d" "$(du -sh "data/$d" 2>/dev/null | cut -f1)"
  fi
done
