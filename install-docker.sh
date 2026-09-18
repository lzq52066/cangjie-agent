#!/usr/bin/env bash
# ============================================================
# Docker Engine + Docker Compose v2 一键安装脚本（Linux）
# 适用：CentOS/RHEL 7+、Ubuntu 18.04+、Debian 10+、Fedora
# 用法：
#   chmod +x install-docker.sh
#   sudo ./install-docker.sh
# 说明：使用 Docker 官方安装器并指定阿里云镜像源，国内可直接安装；
#       自动配置 registry 镜像加速、开机自启，并安装 compose 插件。
# ============================================================
set -euo pipefail

# ---------- 1. 必须 root ----------
if [ "$(id -u)" -ne 0 ]; then
  echo "请使用 root 或 sudo 执行：sudo ./install-docker.sh" >&2
  exit 1
fi

echo "==> [1/6] 检测系统环境"
if [ -f /etc/os-release ]; then
  . /etc/os-release
  echo "    发行版: ${PRETTY_NAME:-unknown}"
else
  echo "    无法识别 /etc/os-release，仍尝试官方安装器"
fi

# 已安装则跳过
if command -v docker >/dev/null 2>&1; then
  echo "==> 检测到 Docker 已安装：$(docker --version)"
else
  echo "==> [2/6] 安装基础工具（curl/ca-certificates）"
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update -y
    apt-get install -y ca-certificates curl gnupg lsb-release
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y curl ca-certificates
  elif command -v yum >/dev/null 2>&1; then
    yum install -y curl ca-certificates
  fi

  echo "==> [3/6] 通过官方安装器 + 阿里云镜像源安装 Docker"
  # 官方脚本支持 --mirror Aliyun，软件包走阿里云，国内稳定
  curl -fsSL https://get.docker.com | sh -s docker --mirror Aliyun
fi

echo "==> [4/6] 配置镜像加速与日志参数"
mkdir -p /etc/docker
# 已有自定义配置则备份，避免覆盖
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
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  },
  "live-restore": true
}
EOF

echo "==> [5/6] 启动 Docker 并设置开机自启"
systemctl daemon-reload
systemctl enable --now docker

echo "==> [6/6] 验证安装"
docker --version
if docker compose version >/dev/null 2>&1; then
  docker compose version
else
  echo "未检测到 compose 插件，尝试补装..."
  if command -v apt-get >/dev/null 2>&1; then apt-get install -y docker-compose-plugin; fi
  if command -v dnf >/dev/null 2>&1; then dnf install -y docker-compose-plugin; fi
  if command -v yum >/dev/null 2>&1; then yum install -y docker-compose-plugin; fi
  docker compose version
fi

# 非 root 用户免 sudo（如通过 sudo 执行，SUDO_USER 才是真实用户）
if [ -n "${SUDO_USER:-}" ]; then
  usermod -aG docker "$SUDO_USER" || true
  echo "    已将用户 '$SUDO_USER' 加入 docker 组，重新登录后可免 sudo 使用 docker"
fi

echo ""
echo "Docker 安装完成。可执行验证："
echo "  docker run --rm hello-world"
echo "随后部署本项目：./deploy.sh up"
