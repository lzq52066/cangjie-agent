#!/usr/bin/env bash
# ============================================================
# 92 - 查看日志（默认后端；可传服务名，如：./92-logs.sh postgres）
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

svc="${1:-backend}"
docker compose logs -f --tail=200 "$svc"
