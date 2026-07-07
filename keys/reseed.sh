#!/usr/bin/env bash
PW=zwinsight123
DB=zw_insight
# --force：忽略菜单重复键，继续执行后面的角色/管理员/关联插入
docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW --force $DB" < /root/zwi-deploy/db-init/99_data-menu.sql 2>&1 | grep -iv "1062" | grep -i error | head -20
Q() { docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW -N -e '$1'" 2>/dev/null; }
{
  echo "sys_menu=$(Q 'SELECT COUNT(*) FROM zw_insight.sys_menu')"
  echo "sys_role=$(Q 'SELECT COUNT(*) FROM zw_insight.sys_role')"
  echo "sys_role_menu=$(Q 'SELECT COUNT(*) FROM zw_insight.sys_role_menu')"
  echo "sys_user=$(Q 'SELECT COUNT(*) FROM zw_insight.sys_user')"
  echo "sys_user_role=$(Q 'SELECT COUNT(*) FROM zw_insight.sys_user_role')"
  echo "admin_user=$(Q 'SELECT username FROM zw_insight.sys_user WHERE id=1')"
} > /root/zwi-deploy/reseed.out 2>&1
cat /root/zwi-deploy/reseed.out
