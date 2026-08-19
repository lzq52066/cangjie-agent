# ---- 构建阶段 ----
FROM maven:3.9-amazoncorretto-17-al2023 AS build
WORKDIR /workspace

# 复制整个项目（依赖层缓存对多模块项目差异较小）
COPY . .

# 构建并跳过测试
RUN mvn clean package -DskipTests -B -q

# ---- 运行阶段 ----
FROM amazoncorretto:17-al2023
WORKDIR /app

# 创建非 root 用户并创建必要目录
RUN useradd -r -m -s /bin/bash cangjie && \
    mkdir -p /app/logs /app/uploads && \
    chown -R cangjie:cangjie /app

USER cangjie

# 复制构建产物
COPY --from=build /workspace/cangjie-start/target/cangjie-start.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM 参数（生产优化，JDK17 兼容）
ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]