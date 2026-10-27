# =============================================================================
# 餐厅点餐系统 MVP — 后端镜像（Spring Boot 3 / Java 17）
#
# 构建上下文 = 仓库根目录（docker-compose.yml: build.context: .）
#   docker build -f deploy/backend/Dockerfile -t restaurant-backend .
#
# 说明：
#   - 本机 JDK 21，但后端 pom 必须以 release 17 编译（见 brief §6）
#   - 最终镜像只含 JRE 与 fat jar，不包含 Maven/source
#   - 运行用户为非 root（app）；本地兜底存储目录 /app/storage 由 compose 卷挂载
#   - 健康检查依赖 Actuator /actuator/health（brief §4.7）
#   - 本机无 Docker，本镜像构建/运行状态：未验证
# =============================================================================

# ---------- 构建阶段 ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# 先单独拷贝 pom 以复用依赖层缓存（单模块后端）
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -q -f backend/pom.xml -DskipTests dependency:go-offline

# 再拷贝源码并打包（pom 内 spring-boot-maven-plugin 需产出可执行 fat jar）
COPY backend/src backend/src
RUN mvn -B -q -f backend/pom.xml -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m" \
    SPRING_PROFILES_ACTIVE=dev

RUN apk add --no-cache tzdata curl \
    && addgroup -S app \
    && adduser -S -G app app \
    && mkdir -p /app/storage /app/logs \
    && chown -R app:app /app

WORKDIR /app

# Spring Boot repackage 只会留下一个 *.jar（*.jar.original 不匹配该通配符）
COPY --from=build /workspace/backend/target/*.jar /app/app.jar
RUN chown app:app /app/app.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
