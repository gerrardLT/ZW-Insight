#!/usr/bin/env bash
set -u
DC=/root/zwi-deploy
DB=zw_insight
PW=zwinsight123

echo "=== 等待 MySQL 健康 ==="
for i in $(seq 1 40); do
  s=$(docker inspect -f '{{.State.Health.Status}}' zwi-mysql 2>/dev/null)
  echo "try $i: ${s:-none}"
  if [ "$s" = "healthy" ]; then break; fi
  sleep 4
done
if [ "$s" != "healthy" ]; then echo "MySQL 未就绪，退出"; exit 1; fi

echo "=== 按序灌库 ==="
fail=0
for f in $(ls -1 $DC/db-init/*.sql | sort); do
  name=$(basename "$f")
  # 用 --force 跳过单条错误但记录；先不加 force，捕获错误
  err=$(docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW $DB" < "$f" 2>&1)
  if [ -n "$err" ]; then
    echo "[ERR] $name:"
    echo "$err" | head -5
    fail=$((fail+1))
  else
    echo "[OK ] $name"
  fi
done

echo "=== 表数量 ==="
docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW -N -e 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\"$DB\";'"
echo "=== 关键种子：菜单/角色/管理员 ==="
docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW -N -e 'SELECT (SELECT COUNT(*) FROM $DB.sys_menu), (SELECT COUNT(*) FROM $DB.sys_role), (SELECT COUNT(*) FROM $DB.sys_user);'" 2>&1 | head -3
echo "=== 灌库错误条目数: $fail ==="
