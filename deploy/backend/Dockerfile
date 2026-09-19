# 后端运行时镜像（不含 jar：jar 由 docker-compose 挂载 ./backend/artifacts 进容器，
# 更新制品无需重建镜像，见 docker-compose.yml 中 backend 的 volumes）
#
# 基础镜像 amazoncorretto:17-al2023 已自带 curl（健康检查用）与 tzdata（Asia/Shanghai），
# 且不含 microdnf/useradd，故这里不装任何包、也不调用 useradd，直接写入 passwd/group 记录。
FROM amazoncorretto:17-al2023
WORKDIR /app

RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "cangjie:x:1000:1000::/home/cangjie:/sbin/nologin" >> /etc/passwd \
    && echo "cangjie:x:1000:" >> /etc/group \
    && mkdir -p /home/cangjie /app/logs /app/uploads /app/artifacts \
    && chown -R 1000:1000 /home/cangjie /app

USER 1000

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# shell 形式以展开 $JAVA_OPTS，exec 保证 java 为 PID 1
ENTRYPOINT exec java $JAVA_OPTS \
    -XX:+UseZGC \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom \
    -jar /app/artifacts/cangjie-start.jar
