# ---- 构建阶段：打包 Admin 与 Chat 双端 ----
FROM node:20-alpine AS build
WORKDIR /build

# 先拷贝依赖描述以利用 Docker 层缓存
COPY package.json package-lock.json ./
# 国内构建可放开下一行使用 npmmirror 加速
# RUN npm config set registry https://registry.npmmirror.com
RUN npm ci

# 拷贝源码并构建（产物：dist/admin、dist/chat）
COPY . .
RUN npm run build

# ---- 运行阶段：Nginx 托管静态资源并反代后端 ----
FROM nginx:1.27-alpine
COPY nginx.docker.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
