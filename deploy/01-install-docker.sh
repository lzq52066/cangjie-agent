#!/usr/bin/env bash
# ============================================================
# 01 - 安装 Docker Engine + Compose v2
#      已安装则跳过安装，但仍会确保镜像加速配置生效
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

# 本脚本仅用于 Linux 服务器（依赖 systemctl / usermod 等）
if [ "$(uname -s)" = "Darwin" ]; then
  echo "[01] 当前为 macOS，跳过 Docker 安装：请改为安装 Docker Desktop"
  echo "     https://www.docker.com/products/docker-desktop/"
  exit 0
fi

NEED_INSTALL=1
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  NEED_INSTALL=0
  echo "[01] Docker 已安装：$(docker --version)，Compose：$(docker compose version --short 2>/dev/null || echo v2)"
fi

if [ "$NEED_INSTALL" -eq 1 ]; then
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
fi

# ---------- 配置镜像加速（无论新装还是已装都要确保生效） ----------
if [ "$(id -u)" -ne 0 ]; then
  echo "[01] 配置镜像加速需要 root 权限，请使用：sudo ./01-install-docker.sh" >&2
  exit 1
fi

echo "[01] 探测镜像加速地址可用性"
mirrors=""
for m in https://docker.1ms.run https://docker.xuanyuan.me https://docker.m.daocloud.io; do
  # 探测 /v2/ 是否响应：任何 HTTP 状态码（含 401）都说明服务可达，000 为不可达
  code=$(curl -s -m 6 -o /dev/null -w '%{http_code}' "$m/v2/" 2>/dev/null || echo 000)
  if [ "$code" != "000" ]; then
    echo "     可用: $m (HTTP $code)"
    mirrors="${mirrors:+$mirrors, }\"$m\""
  else
    echo "     跳过: $m（不可达）"
  fi
done

if [ -z "$mirrors" ]; then
  echo "[01] 警告：所有候选镜像加速均不可达，将直连 Docker Hub（国内网络可能超时）" >&2
fi

tmp=$(mktemp)
cat > "$tmp" <<EOF
{
  "registry-mirrors": [${mirrors}],
  "log-driver": "json-file",
  "log-opts": { "max-size": "100m", "max-file": "3" },
  "live-restore": true
}
EOF

if [ -f /etc/docker/daemon.json ] && cmp -s "$tmp" /etc/docker/daemon.json; then
  rm -f "$tmp"
  echo "[01] daemon.json 无变化，无需重启 Docker"
else
  mkdir -p /etc/docker
  [ -f /etc/docker/daemon.json ] && cp /etc/docker/daemon.json "/etc/docker/daemon.json.bak.$(date +%s)"
  mv "$tmp" /etc/docker/daemon.json
  echo "[01] 重启 Docker 使镜像加速配置生效"
  systemctl enable --now docker
  systemctl restart docker
fi

echo "[01] 当前生效的镜像加速地址："
docker info --format '{{range .RegistryConfig.Mirrors}}     {{.}}{{"\n"}}{{end}}' 2>/dev/null || true

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
