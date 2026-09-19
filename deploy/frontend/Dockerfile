# 前端运行时镜像（只托管已构建好的 dist，服务器上不跑 npm）
# 构建上下文为 frontend/ 目录
#
# 配置生成方式：容器启动时由 /docker-entrypoint.d/05-nginx-conf.sh 检测
#   /etc/nginx/certs 下是否有 server.crt + server.key，
#   有则生成 HTTPS 配置（80 跳 443），无则生成纯 HTTP 配置（不会因缺证书启动失败）。
#   该脚本用 envsubst 把 ${MINIO_BUCKET} 替换成真实桶名后，生成
#   /etc/nginx/locations.conf 与 /etc/nginx/conf.d/default.conf；
#   nginx 内置变量（$host 等）不受影响。
FROM nginx:1.27-alpine

COPY nginx.conf.template     /opt/nginx/http.conf.template
COPY nginx-ssl.conf.template /opt/nginx/ssl.conf.template
COPY nginx-locations.inc     /etc/nginx/locations.inc
COPY 05-nginx-conf.sh        /docker-entrypoint.d/05-nginx-conf.sh
RUN chmod +x /docker-entrypoint.d/05-nginx-conf.sh

COPY dist /usr/share/nginx/html
EXPOSE 80 443
