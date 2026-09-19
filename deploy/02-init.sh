#!/usr/bin/env bash
# ============================================================
# 02 - 初始化
#      1) 在宿主机创建 ./data 数据目录（数据库/缓存/对象存储/日志）
#      2) 由 .env.example 生成 .env，并自动随机化密钥、探测对外地址
#      3) 校验前后端制品是否已就位
#      本脚本幂等：重复执行不会覆盖已有 .env，也不会丢失已有数据
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

# GNU sed 与 BSD sed 的 -i 参数不同，这里做兼容封装
sed_inplace() {
  if sed --version >/dev/null 2>&1; then
    sed -i "$@"
  else
    sed -i '' "$@"
  fi
}

# 生成 n 位随机字母数字串（避免 / & @ 等在 URL、sed、连接串里需要转义的字符）
rand() {
  LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c "$1"
}

# ---------- 1) 数据目录 ----------
DATA_DIR="$(pwd)/data"
echo "[02] 准备数据目录：$DATA_DIR"
mkdir -p data/postgres data/redis data/minio data/logs

# 容器内用户固定：PostgreSQL / Redis 为 uid 999，MinIO / 后端为 uid 1000，
# 目录由 root 创建时需要放开写权限，否则容器会因无权限写入而启动失败
if [ "$(id -u)" -eq 0 ]; then
  chown -R 999:999 data/postgres data/redis 2>/dev/null || true
  chmod 700 data/postgres data/redis 2>/dev/null || true
  chown -R 1000:1000 data/minio data/logs 2>/dev/null || true
else
  echo "[02] 提示：当前非 root，若容器报数据目录权限错误，请执行：" >&2
  echo "     sudo chown -R 999:999 data/postgres data/redis" >&2
  echo "     sudo chown -R 1000:1000 data/minio data/logs" >&2
fi
echo "     data/postgres  数据库（可整目录备份/迁移）"
echo "     data/redis     Redis AOF"
echo "     data/minio     对象存储文件"
echo "     data/logs      后端日志"

# ---------- 2) .env ----------
if [ ! -f .env ]; then
  cp .env.example .env
  echo "[02] 已由 .env.example 生成 .env"

  # 2.1 探测对外访问地址，用于 MinIO 预签名 URL（必须从浏览器可达）
  public_ip=""
  for url in https://api.ipify.org https://ifconfig.me/ip https://ipinfo.io/ip; do
    public_ip=$(curl -fsS -m 6 "$url" 2>/dev/null | tr -d '[:space:]' || true)
    [ -n "$public_ip" ] && break
  done
  if [ -z "$public_ip" ]; then
    public_ip=$(hostname -I 2>/dev/null | awk '{print $1}')
  fi
  if [ -z "$public_ip" ]; then
    public_ip=$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -1)
  fi

  # MinIO 不直接对外暴露端口，文件与控制台都经 80 端口的 Nginx 反代：
  #   文件预签名根地址  http://<IP>/<桶名>/...
  #   控制台           http://<IP>/minio-console/
  web_port=$(awk -F= '/^WEB_PORT=/{print $2}' .env)
  if [ -n "$public_ip" ]; then
    if [ "${web_port:-80}" = "80" ]; then
      web_base="http://${public_ip}"
    else
      web_base="http://${public_ip}:${web_port}"
    fi
    sed_inplace "s|^MINIO_PUBLIC_ENDPOINT=.*|MINIO_PUBLIC_ENDPOINT=${web_base}|" .env
    sed_inplace "s|^MINIO_CONSOLE_PUBLIC_URL=.*|MINIO_CONSOLE_PUBLIC_URL=${web_base}/minio-console|" .env
    echo "[02] 已自动设置 MINIO_PUBLIC_ENDPOINT=${web_base}（文件走 /<桶名>/，控制台走 /minio-console/）"
  else
    echo "[02] 警告：未能探测到对外 IP，请手工修改 .env 的 MINIO_PUBLIC_ENDPOINT 与 MINIO_CONSOLE_PUBLIC_URL" >&2
  fi

  # 2.2 随机化安全密钥，避免生产环境沿用示例值
  #     数据库/Redis/MinIO 的账号密码保持 .env 中的固定默认值（cangjie / cangjie123），便于自行连接
  set_env() {
    sed_inplace "s|^${1}=.*|${1}=${2}|" .env
  }
  set_env CANGJIE_MODEL_KEY_SECRET "$(rand 32)"
  set_env SA_TOKEN_JWT_SECRET_KEY "$(rand 64)"
  echo "[02] 已随机生成模型 Key 加密密钥与会话签名密钥"
  echo "[02] 数据库/Redis/MinIO 使用固定账号密码：$(awk -F= '/^POSTGRES_USER=/{print $2}' .env) / $(awk -F= '/^POSTGRES_PASSWORD=/{print $2}' .env)（如需修改请直接编辑 .env）"
  # 管理员初始密码使用固定值（默认 cangjie123），首次登录后会被强制修改，因此不随机化
  echo "[02] 管理员初始账号/密码：$(awk -F= '/^SYSTEM_DEFAULT_USERNAME=/{print $2}' .env) / $(awk -F= '/^SYSTEM_DEFAULT_PASSWORD=/{print $2}' .env)（首次登录需修改）"
else
  echo "[02] 已存在 .env，跳过生成（如需重置配置请先删除 .env）"
  if grep -qE 'change-me' .env; then
    echo "[02] 警告：.env 中的安全密钥仍是示例值，对外暴露前请务必修改（可执行一次 ./02-init.sh 自动随机化）" >&2
  fi
fi

# ---------- 3) 制品校验 ----------
if [ ! -f backend/artifacts/cangjie-start.jar ]; then
  echo "[02] 错误：未找到 backend/artifacts/cangjie-start.jar" >&2
  echo "     请用本地 pack.ps1 / pack.sh 打包，或手动拷贝 cangjie-start/target/cangjie-start.jar 到 backend/artifacts/ 目录" >&2
  exit 1
fi
echo "[02] 后端制品: backend/artifacts/cangjie-start.jar ($(du -h backend/artifacts/cangjie-start.jar | cut -f1))"

if [ ! -d frontend/dist/admin ] || [ ! -d frontend/dist/chat ]; then
  echo "[02] 错误：未找到 frontend/dist/admin 或 frontend/dist/chat" >&2
  echo "     请执行前端构建（npm run build）后，将 dist 目录内容拷贝到 frontend/dist/" >&2
  exit 1
fi
echo "[02] 前端制品: frontend/dist（admin + chat）"

echo "[02] 完成。数据目录：$DATA_DIR"
