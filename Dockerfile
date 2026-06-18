# ========== 第一阶段：构建阶段 ==========
FROM maven:3.8.4-openjdk-17-slim AS build

# 设置工作目录
WORKDIR /build

# 先复制 pom.xml，利用 Docker 缓存依赖层（只要 pom.xml 不变，依赖包不会重新下载）
COPY pom.xml .

# 下载所有依赖（此层会被缓存）
RUN mvn dependency:go-offline -B

# 复制源码
COPY src ./src

# 打包（跳过测试）
RUN mvn clean package -DskipTests

# ========== 第二阶段：运行阶段 ==========
FROM eclipse-temurin:17-jre-alpine

# 设置时区（可选，建议加上）
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建应用目录
WORKDIR /app

# 从构建阶段复制 jar 包
COPY --from=build /build/target/*.jar app.jar

# 暴露端口
EXPOSE 8081

# 启动命令（支持通过环境变量覆盖配置）
ENTRYPOINT ["java", "-jar", "app.jar"]