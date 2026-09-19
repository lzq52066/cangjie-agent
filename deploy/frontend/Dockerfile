# 前端运行时镜像（只托管已构建好的 dist，服务器上不跑 npm）
# 构建上下文为 frontend/ 目录
#
# Nginx 配置以模板方式放入 /etc/nginx/templates/default.conf.template：
# 官方 nginx 镜像启动时会用 envsubst 将 ${MINIO_BUCKET} 替换为环境变量值，
# 其余 nginx 内置变量（$host、$remote_addr 等）不受影响。
FROM nginx:1.27-alpine
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY dist /usr/share/nginx/html
EXPOSE 80
