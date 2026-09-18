# 后端运行时镜像（只打包已编译好的 jar，服务器上不做 Maven 编译）
FROM amazoncorretto:17-al2023
WORKDIR /app

# 健康检查需要 curl，时区需要 tzdata
RUN microdnf install -y curl tzdata && microdnf clean all \
    && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

RUN useradd -r -m -s /bin/bash cangjie \
    && mkdir -p /app/logs /app/uploads \
    && chown -R cangjie:cangjie /app
USER cangjie

# jar 与本 Dockerfile 同目录，构建上下文即 backend/
COPY --chown=cangjie:cangjie cangjie-start.jar app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# shell 形式以展开 $JAVA_OPTS，exec 保证 java 为 PID 1
ENTRYPOINT exec java $JAVA_OPTS \
    -XX:+UseZGC \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom \
    -jar app.jar
