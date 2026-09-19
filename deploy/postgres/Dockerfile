# PostgreSQL 16 + pgvector + pgroonga 双扩展镜像
#
# 基础镜像 groonga/pgroonga:4.0.8-debian-16 已自带：
#   - PostgreSQL 16.14（PGDG trixie-pgdg 构建）
#   - pgroonga 4.0.8（Groonga 官方构建，构建期无需访问 packages.groonga.org）
#   - 官方 postgres docker-entrypoint.sh，PGDATA=/var/lib/postgresql/data
# 本层只补装 pgvector：Debian 源走阿里云加速，PGDG 源保持官方（国内镜像常处于同步中，
# 会出现 Packages 与 Release 校验和不符导致 apt 失败）。
FROM groonga/pgroonga:4.0.8-debian-16

RUN set -eux; \
    sed -i 's|http://deb.debian.org/debian|https://mirrors.aliyun.com/debian|g' /etc/apt/sources.list.d/debian.sources; \
    rm -f /etc/apt/sources.list.d/groonga.sources /etc/apt/sources.list.d/apache-arrow.sources; \
    rm -rf /var/lib/apt/lists/*; \
    apt-get -o Acquire::Retries=5 update; \
    apt-get -o Acquire::Retries=5 install -y --no-install-recommends postgresql-16-pgvector; \
    apt-get clean; \
    rm -rf /var/lib/apt/lists/*; \
    test -f /usr/share/postgresql/16/extension/vector.control; \
    test -f /usr/share/postgresql/16/extension/pgroonga.control

COPY init-extensions.sql /docker-entrypoint-initdb.d/10-init-extensions.sql
