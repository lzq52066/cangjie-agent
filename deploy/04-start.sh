#!/usr/bin/env bash
# ============================================================
# 04 - 启动全部服务并等待就绪
#      1) 幂等准备宿主机数据目录
#      2) 启动前检查端口占用（避免与已有 Nginx / MySQL 等冲突）
#      3) 启动容器并等待后端健康，超时即判失败并打印日志
#      4) 打印可直接访问的地址与初始账号
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "[04] 未找到 .env，请先执行 ./02-init.sh" >&2
  exit 1
fi

env_get() {
  awk -F= -v k="$1" '$0 ~ "^" k "=" { sub("^" k "=", ""); print; exit }' .env
}

# ---------- 1) 数据目录（与 02-init.sh 保持一致，重复执行无副作用） ----------
mkdir -p data/postgres data/redis data/minio data/logs
if [ "$(id -u)" -eq 0 ]; then
  chown -R 999:999 data/postgres data/redis 2>/dev/null || true
  chmod 700 data/postgres data/redis 2>/dev/null || true
  chown -R 1000:1000 data/minio data/logs 2>/dev/null || true
fi

# ---------- 2) 端口占用检查 ----------
port_in_use() {
  local p="$1"
  if command -v ss >/dev/null 2>&1; then
    [ -n "$(ss -ltnH "sport = :$p" 2>/dev/null)" ]
  elif command -v netstat >/dev/null 2>&1; then
    netstat -ltn 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$p\$"
  else
    return 1
  fi
}

# 已有本项目的容器在跑时端口本来就是占用的，跳过检查
if [ -z "$(docker compose ps -q 2>/dev/null)" ]; then
  echo "[04] 端口占用检查（仅 Web 端口对外）"
  p=$(env_get WEB_PORT)
  if [ -n "$p" ] && port_in_use "$p"; then
    echo "     警告：端口 $p（WEB_PORT）已被占用，容器可能启动失败" >&2
    command -v ss >/dev/null 2>&1 && ss -ltnp "sport = :$p" 2>/dev/null | sed 's/^/       /' >&2 || true
  else
    echo "     ${p:-80}（WEB_PORT）可用"
  fi
fi

# ---------- 3) 启动 ----------
echo "[04] 启动服务..."
docker compose up -d

echo "[04] 等待后端就绪（最多 5 分钟）..."
status="unknown"
ready=0
for _ in $(seq 1 60); do
  status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' cangjie-backend 2>/dev/null || echo "missing")
  if [ "$status" = "healthy" ]; then
    ready=1
    break
  fi
  printf '.'
  sleep 5
done
echo ""

if [ "$ready" -ne 1 ]; then
  echo ""
  echo "!!! 后端在 5 分钟内未就绪（当前状态：$status）"
  echo "---- cangjie-backend 日志尾部 ----"
  docker logs --tail 100 cangjie-backend 2>&1 || true
  echo "---- 全部容器状态 ----"
  docker compose ps || true
  exit 1
fi
echo "[04] 后端已就绪"

# ---------- 4) 访问信息 ----------
web_port=$(env_get WEB_PORT)
public_host=$(env_get MINIO_PUBLIC_ENDPOINT | sed -E 's|^https?://||; s|/.*$||; s|:[0-9]+$||')
[ -z "$public_host" ] && public_host=$(hostname -I 2>/dev/null | awk '{print $1}')
[ -z "$public_host" ] && public_host="<服务器IP>"
if [ "${web_port:-80}" = "80" ]; then
  web_base="http://${public_host}"
else
  web_base="http://${public_host}:${web_port}"
fi

echo ""
echo "[04] 启动完成"
echo "     管理后台 : ${web_base}/admin/"
echo "     对话端   : ${web_base}/chat/   （需带 ?app=<应用ID>）"
echo "     MinIO    : 控制台 ${web_base}/minio-console/（文件经 ${web_base}/<桶名>/ 访问）"
echo "     默认账号 : admin / $(env_get SYSTEM_DEFAULT_PASSWORD)"
echo "     数据目录 : $(pwd)/data"
echo "     端口说明 : 对外仅 ${web_port:-80}；PostgreSQL/Redis/MinIO 仅容器内网可达"
echo ""
echo "     查看状态：./05-status.sh      查看日志：./92-logs.sh"
