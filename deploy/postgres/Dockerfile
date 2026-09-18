# PostgreSQL 16 + pgvector + pgroonga 双扩展镜像（与源码包内一致）
FROM postgres:16-bookworm

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

COPY init-extensions.sql /docker-entrypoint-initdb.d/10-init-extensions.sql
