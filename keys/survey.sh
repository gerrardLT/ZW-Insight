#!/usr/bin/env bash
echo '=== 运行中的容器 ==='
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
echo '=== 端口监听检查 ==='
for p in 3306 6379 8080 9000 5672 8012 15672; do
  if ss -ltn 2>/dev/null | grep -q ":$p "; then echo "$p OCCUPIED"; else echo "$p free"; fi
done
echo '=== 磁盘 / ==='
df -h / | tail -1
echo '=== 内存 ==='
free -h | head -2
echo '=== docker networks ==='
docker network ls
echo '=== 现有 mysql 容器名(若有) ==='
docker ps --filter "ancestor=mysql:8.0" --format '{{.Names}}'
docker ps --format '{{.Names}} {{.Image}}' | grep -i mysql || true
docker ps --format '{{.Names}} {{.Image}}' | grep -i redis || true
