# 运行时镜像：基于 amazoncorretto:21（服务器国内网络可拉取；temurin 源超时）
FROM amazoncorretto:21

WORKDIR /app

# 上海时区
ENV TZ=Asia/Shanghai

# 复制可启动 jar
COPY zw-insight-app.jar /app/app.jar

EXPOSE 8080

# 容器内存受 compose mem_limit 限制；JVM 按容器内存自适应，封顶 70%
ENTRYPOINT ["sh","-c","java -XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Shanghai -jar /app/app.jar"]
