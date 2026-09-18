#!/usr/bin/env bash
# ============================================================
# CangJie Agent 一键部署脚本（Linux + Docker Compose）
# 用法：
#   chmod +x deploy.sh
#   ./deploy.sh up        构建并启动全部服务
#   ./deploy.sh down      停止并移除容器（保留数据卷）
#   ./deploy.sh logs      跟踪后端日志
#   ./deploy.sh restart   重启
#   ./deploy.sh purge     停止并【删除数据卷】（谨慎！数据全清）
# ============================================================
set -euo pipefail

cd "$(dirname "$0")"

# 若未安装 Docker，可取消下面注释（Debian/Ubuntu 一键安装）
# if ! command -v docker >/dev/null 2>&1; then
#   curl -fsSL https://get.docker.com | sh
#   systemctl enable --now docker
# fi

if ! docker compose version >/dev/null 2>&1; then
  echo "未检测到 docker compose（Docker Compose v2），请先安装 Docker。" >&2
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo "已根据 .env.example 生成 .env，请按需修改（至少设置 MINIO_PUBLIC_ENDPOINT 与各类密钥）后重新执行。"
fi

cmd="${1:-up}"
case "$cmd" in
  up)
    docker compose build
    docker compose up -d
    echo "---- 服务状态 ----"
    docker compose ps
    cat <<'TIP'

部署完成：
  管理后台 : http://<服务器IP>/admin/   （默认账号 admin，密码见 .env 的 SYSTEM_DEFAULT_PASSWORD）
  对话端   : http://<服务器IP>/chat/
  MinIO控制台: http://<服务器IP>:9001
  健康检查 : docker compose logs -f backend（出现 Started CangJieAgentApplication 即成功）
注意：请将 .env 中 MINIO_PUBLIC_ENDPOINT 改为本机可访问地址，否则文件下载链接无效。
TIP
    ;;
  down)   docker compose down ;;
  restart) docker compose restart ;;
  logs)   docker compose logs -f backend ;;
  purge)
    read -p "将删除所有数据卷（数据库/文件/缓存全部清空），确认输入 yes：" confirm
    [ "$confirm" = "yes" ] && docker compose down -v
    ;;
  *) echo "未知命令: $cmd（支持 up/down/restart/logs/purge）" >&2; exit 1 ;;
esac
