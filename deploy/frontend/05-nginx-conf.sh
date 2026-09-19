#!/bin/sh
# ============================================================
# 依据证书是否存在，生成 nginx 配置（官方 nginx 镜像会自动执行 /docker-entrypoint.d/ 下的脚本）
#
#   证书约定：/etc/nginx/certs/server.crt（含证书链）+ server.key
#   有证书 → 监听 443 提供 HTTPS，80 的请求 301 跳转到 443
#   无证书 → 仅监听 80 提供 HTTP（保持原有行为，不会因缺少证书而启动失败）
#
# 换证书后执行 docker compose restart frontend 即生效
# 脚本名以 05- 开头是有意的：先于镜像自带的 10-listen-on-ipv6-by-default.sh 生成配置，
# 让该脚本能对本文件生成的 80 server 补上 IPv6 监听。
# ============================================================
set -e

conf_dir="/etc/nginx/conf.d"
cert_dir="/etc/nginx/certs"
cert="$cert_dir/server.crt"
key="$cert_dir/server.key"

mkdir -p "$conf_dir"
rm -f "$conf_dir/default.conf"

if [ -f "$cert" ] && [ -f "$key" ]; then
  echo "[nginx] 检测到证书 $cert，启用 HTTPS（HTTP 将 301 跳转到 HTTPS）"
  template="/opt/nginx/ssl.conf.template"
else
  echo "[nginx] 未检测到 $cert / $key，仅启用 HTTP"
  echo "[nginx] 启用 HTTPS：把证书放到宿主机 deploy/frontend/certs/ 下（命名 server.crt / server.key），"
  echo "[nginx] 再执行 docker compose restart frontend"
  template="/opt/nginx/http.conf.template"
fi

# locations.inc 里含 ${MINIO_BUCKET} 占位符，必须先替换成真实桶名再给 nginx include，
# 否则 nginx 会把它当成未知变量而拒绝启动。
# 只替换 ${MINIO_BUCKET}，避免误动 nginx 内置变量（$host、$remote_addr 等）。
envsubst '${MINIO_BUCKET}' < /etc/nginx/locations.inc > /etc/nginx/locations.conf
envsubst '${MINIO_BUCKET}' < "$template" > "$conf_dir/default.conf"
