#!/usr/bin/env bash
PW=zwinsight123
DB=zw_insight
{
  echo "=== --force 重跑 99_data-menu.sql，捕获真实错误（菜单重复键会被忽略）==="
  docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW --force $DB" < /root/zwi-deploy/db-init/99_data-menu.sql 2>&1 | grep -i error | head -20
  echo "=== sys_role 表结构 ==="
  docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW -N -e 'SHOW CREATE TABLE $DB.sys_role\G'" 2>&1 | head -40
  echo "=== sys_user 表结构 ==="
  docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW -N -e 'SHOW CREATE TABLE $DB.sys_user\G'" 2>&1 | head -50
} > /root/zwi-deploy/diag2.out 2>&1
echo done
