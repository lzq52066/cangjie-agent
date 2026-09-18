# PostgreSQL 16 + pgvector + pgroonga 双扩展镜像
# 官方 pgvector/pgvector 镜像不含 pgroonga，而 pgroonga 又不在 PGDG 源中，
# 因此基于官方 postgres:16 镜像叠加 Groonga 官方 apt 源自行构建。
FROM postgres:16-bookworm

# postgres 官方镜像已内置 PGDG apt 源（可直接安装 postgresql-16-postgresql-vector）
# pgroonga 需额外添加 Groonga 官方 apt 源
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates wget lsb-release gnupg \
    && wget -q https://packages.groonga.org/debian/groonga-apt-source-latest-bookworm.deb \
    && apt-get install -y -V --no-install-recommends ./groonga-apt-source-latest-bookworm.deb \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        postgresql-16-postgresql-vector \
        postgresql-16-pgdg-pgroonga \
    && rm -f ./groonga-apt-source-latest-bookworm.deb \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 数据卷首次初始化时预装扩展（docker-entrypoint-initdb.d 仅在数据目录为空时执行）
COPY init-extensions.sql /docker-entrypoint-initdb.d/10-init-extensions.sql
