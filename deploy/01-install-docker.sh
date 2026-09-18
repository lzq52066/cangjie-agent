#!/usr/bin/env bash
# ============================================================
# 01 - 安装 Docker Engine + Compose v2（已安装则自动跳过）
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

# 本脚本仅用于 Linux 服务器（依赖 systemctl / usermod 等）
if [ "$(uname -s)" = "Darwin" ]; then
  echo "[01] 当前为 macOS，跳过 Docker 安装：请改为安装 Docker Desktop"
  echo "     https://www.docker.com/products/docker-desktop/"
  exit 0
fi

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  echo "[01] Docker 已安装：$(docker --version)，Compose：$(docker compose version --short 2>/dev/null || echo v2)"
  exit 0
fi

if [ "$(id -u)" -ne 0 ]; then
  echo "[01] 安装 Docker 需要 root 权限，请使用：sudo ./01-install-docker.sh" >&2
  exit 1
fi

echo "[01] 检测系统环境"
[ -f /etc/os-release ] && . /etc/os-release && echo "     发行版: ${PRETTY_NAME:-unknown}"

echo "[01] 安装基础工具"
if command -v apt-get >/dev/null 2>&1; then
  apt-get update -y && apt-get install -y ca-certificates curl gnupg lsb-release
elif command -v dnf >/dev/null 2>&1; then
  dnf install -y curl ca-certificates
elif command -v yum >/dev/null 2>&1; then
  yum install -y curl ca-certificates
fi

echo "[01] 通过官方安装器 + 阿里云镜像源安装 Docker"
curl -fsSL https://get.docker.com | sh -s docker --mirror Aliyun

echo "[01] 写入镜像加速与日志轮转配置"
mkdir -p /etc/docker
if [ -f /etc/docker/daemon.json ] && ! grep -q '"registry-mirrors"' /etc/docker/daemon.json 2>/dev/null; then
  cp /etc/docker/daemon.json "/etc/docker/daemon.json.bak.$(date +%s)"
fi
cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me",
    "https://docker.m.daocloud.io"
  ],
  "log-driver": "json-file",
  "log-opts": { "max-size": "100m", "max-file": "3" },
  "live-restore": true
}
EOF

echo "[01] 启动 Docker 并设置开机自启"
systemctl daemon-reload
systemctl enable --now docker

if ! docker compose version >/dev/null 2>&1; then
  echo "[01] 补装 compose 插件"
  command -v apt-get >/dev/null 2>&1 && apt-get install -y docker-compose-plugin
  command -v dnf >/dev/null 2>&1 && dnf install -y docker-compose-plugin
  command -v yum >/dev/null 2>&1 && yum install -y docker-compose-plugin
fi

if [ -n "${SUDO_USER:-}" ]; then
  usermod -aG docker "$SUDO_USER" || true
  echo "[01] 已将用户 '$SUDO_USER' 加入 docker 组（重新登录后免 sudo）"
fi

echo "[01] 完成：$(docker --version) / $(docker compose version --short 2>/dev/null || echo compose-v2)"
