# 前端运行时镜像（只托管已构建好的 dist，服务器上不跑 npm）
# 构建上下文为部署包根目录（.）
FROM nginx:1.27-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend/dist /usr/share/nginx/html
EXPOSE 80
