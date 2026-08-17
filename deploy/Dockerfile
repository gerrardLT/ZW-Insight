# 运行时镜像：基于 amazoncorretto:21（服务器国内网络可拉取；temurin 源超时）
FROM amazoncorretto:21

WORKDIR /app

# 上海时区
ENV TZ=Asia/Shanghai

# R6-01 修复：安装 MySQL 客户端（备份/恢复依赖 mysqldump/mysql，原镜像缺失致每日备份 48 连败）
# 实测基础镜像为 Amazon Linux 2（yum 包管理器，非 AL2023），客户端包名为 mariadb（5.5.68，
# 实测直接提供 /usr/bin/mysql 与 /usr/bin/mysqldump，无需符号链接）
# 与 MySQL 8.0.37 服务端（mysql_native_password 认证）的兼容性以服务器真实 dump 冒烟实证为准
# 构建期 --version 硬断言：二进制不可用则构建失败，无静默处理
RUN yum install -y mariadb \
    && mysqldump --version \
    && mysql --version \
    && yum clean all

# 复制可启动 jar
COPY zw-insight-app.jar /app/app.jar

EXPOSE 8080

# 容器内存受 compose mem_limit 限制；JVM 按容器内存自适应，封顶 70%
ENTRYPOINT ["sh","-c","java -XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Shanghai -jar /app/app.jar"]
