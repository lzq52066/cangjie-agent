# 前端运行时镜像（只托管已构建好的 dist，服务器上不跑 npm）
# 构建上下文为 frontend/ 目录
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 80
