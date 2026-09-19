#!/usr/bin/env bash
# ============================================================
# 01 - 安装 Docker Engine + Compose v2
#      已安装则跳过安装，但仍会确保镜像加速配置生效
#
# 安装策略（逐级回退，适配国内网络）：
#       1) 官方安装器 get.docker.com（--mirror Aliyun）
#       2) 阿里云 / 腾讯云 / 华为云 / 清华 镜像站的 docker-ce 仓库
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

# ---------- 环境自检 ----------
echo "[01] 环境自检"
if [ -r /proc/meminfo ]; then
  mem_mb=$(awk '/^MemTotal:/{printf "%d", $2/1024}' /proc/meminfo)
  echo "     内存: ${mem_mb} MB"
  if [ "$mem_mb" -lt 3800 ]; then
    echo "     警告：内存不足 4GB，后端默认 -Xmx2g，建议在 .env 中调小 JAVA_OPTS 的 -Xmx" >&2
  fi
fi
avail_mb=$(df -Pk / | awk 'NR==2{printf "%d", $4/1024}')
echo "     根分区可用磁盘: ${avail_mb} MB"
if [ "${avail_mb:-0}" -lt 10240 ]; then
  echo "     警告：可用磁盘不足 10GB，镜像与数据可能放不下" >&2
fi
if [ -f /sys/fs/selinux/enforce ] && [ "$(cat /sys/fs/selinux/enforce 2>/dev/null || echo 0)" = "1" ]; then
  echo "     SELinux: 已开启（数据目录挂载已用 :z 标签适配，无需关闭）"
else
  echo "     SELinux: 未开启"
fi

# ---------- 安装 ----------
install_docker_official() {
  local tmp=/tmp/get-docker.sh
  if ! curl -fsSL -m 30 --retry 2 "https://get.docker.com" -o "$tmp" 2>/dev/null; then
    echo "     get.docker.com 不可达" >&2
    return 1
  fi
  echo "     使用官方安装器（--mirror Aliyun）"
  sh "$tmp" --mirror Aliyun
}

install_docker_mirror() {
  local m distro codename arch
  for m in \
    https://mirrors.aliyun.com \
    https://mirrors.cloud.tencent.com \
    https://mirrors.huaweicloud.com \
    https://mirrors.tuna.tsinghua.edu.cn ; do

    if command -v yum >/dev/null 2>&1; then
      if [ "$(curl -s -m 8 -o /dev/null -w '%{http_code}' "$m/docker-ce/linux/centos/docker-ce.repo")" != "200" ]; then
        continue
      fi
      echo "     使用镜像站：$m（yum 仓库）"
      curl -fsSL "$m/docker-ce/linux/centos/docker-ce.repo" -o /etc/yum.repos.d/docker-ce.repo
      # 仓库文件里可能仍指向 download.docker.com，统一改写到镜像站
      sed -i "s|https\?://download.docker.com|$m/docker-ce|g" /etc/yum.repos.d/docker-ce.repo
      if yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin; then
        return 0
      fi
    elif command -v apt-get >/dev/null 2>&1; then
      distro="${ID:-debian}"
      codename="${VERSION_CODENAME:-}"
      [ -z "$codename" ] && command -v lsb_release >/dev/null 2>&1 && codename=$(lsb_release -cs 2>/dev/null || true)
      [ -z "$codename" ] && continue
      if [ "$(curl -s -m 8 -o /dev/null -w '%{http_code}' "$m/docker-ce/linux/$distro/gpg")" != "200" ]; then
        continue
      fi
      echo "     使用镜像站：$m（apt 仓库，$distro/$codename）"
      arch=$(dpkg --print-architecture)
      install -m 0755 -d /etc/apt/keyrings
      curl -fsSL "$m/docker-ce/linux/$distro/gpg" | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
      chmod a+r /etc/apt/keyrings/docker.gpg
      echo "deb [arch=$arch signed-by=/etc/apt/keyrings/docker.gpg] $m/docker-ce/linux/$distro $codename stable" \
        > /etc/apt/sources.list.d/docker.list
      if apt-get update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin; then
        return 0
      fi
    else
      return 1
    fi
  done
  return 1
}

if [ "$NEED_INSTALL" -eq 1 ]; then
  if [ "$(id -u)" -ne 0 ]; then
    echo "[01] 安装 Docker 需要 root 权限，请使用：sudo ./01-install-docker.sh" >&2
    exit 1
  fi

  echo "[01] 检测系统环境"
  if [ -f /etc/os-release ]; then
    # shellcheck disable=SC1091
    . /etc/os-release
    echo "     发行版: ${PRETTY_NAME:-unknown}"
  fi

  echo "[01] 安装基础工具"
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update -y && apt-get install -y ca-certificates curl gnupg lsb-release
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y curl ca-certificates
  elif command -v yum >/dev/null 2>&1; then
    yum install -y curl ca-certificates
  fi

  echo "[01] 安装 Docker"
  if ! install_docker_official; then
    echo "[01] 官方安装器不可用，改用国内镜像站的 docker-ce 仓库"
    if ! install_docker_mirror; then
      echo "[01] 错误：所有安装源均失败，请检查网络后重试，或手动安装 Docker" >&2
      exit 1
    fi
  fi

  if ! command -v docker >/dev/null 2>&1; then
    echo "[01] 错误：安装后仍未找到 docker 命令" >&2
    exit 1
  fi
  echo "[01] 已安装：$(docker --version)"
fi

# 无论新装还是已装，都确保 docker 服务在运行
if ! docker info >/dev/null 2>&1; then
  echo "[01] 启动 Docker 服务"
  systemctl enable --now docker
fi

# ---------- 配置镜像加速（必须让 dockerd 重新读取 daemon.json 才生效） ----------
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
