# 各 location 定义：由 HTTP 版与 HTTPS 版 nginx 配置共同 include（均在 server 块内）
# 注意：本文件不带 server { } 外壳，仅供 include。

client_max_body_size 512m;

# ---------- MinIO S3（预签名 URL 必须保留根路径 /<桶名>/...） ----------
# 后端用内网地址 http://minio:9000 签名，再把 host 替换为站点根地址；
# 因此这里必须：1) 路径不加前缀原样转发；2) Host 伪装成签名用的 minio:9000，
# 否则 SigV4 校验 host 不一致会返回 403。
location /${MINIO_BUCKET}/ {
    proxy_pass http://minio:9000;
    proxy_set_header Host minio:9000;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

# ---------- MinIO 控制台（子路径 /minio-console/） ----------
location = /minio-console {
    return 301 /minio-console/;
}
location /minio-console/ {
    proxy_pass http://minio:9001/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
    proxy_read_timeout 300s;
    chunked_transfer_encoding off;
}

# ---------- 后端接口（含 SSE 流式对话：关闭缓冲、放开读超时） ----------
location /api/ {
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Connection '';
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
    chunked_transfer_encoding on;
}

# 渠道触发类 SSE 接口
location /trigger/ {
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 600s;
}

# OpenAI 兼容端点
location = /v1/chat/completions {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

# 网页挂件静态脚本（由后端 static 提供）
location /widget/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

# ---------- 静态前端 ----------
# 管理后台
location /admin/ {
    alias /usr/share/nginx/html/admin/;
    try_files $uri $uri/ /admin/index.html;
}

# 对话端
location /chat/ {
    alias /usr/share/nginx/html/chat/;
    try_files $uri $uri/ /chat/index.html;
}

location = / {
    return 302 /admin/;
}
