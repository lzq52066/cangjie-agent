#!/usr/bin/env bash
# ============================================================
# 05 - 查看服务状态与端口
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

docker compose ps

echo ""
echo "---- 健康状态 ----"
for c in cangjie-postgres cangjie-redis cangjie-minio cangjie-agent; do
  s=$(docker inspect --format '{{.State.Health.Status}}' "$c" 2>/dev/null || echo "n/a")
  printf "%-20s %s\n" "$c" "$s"
done
