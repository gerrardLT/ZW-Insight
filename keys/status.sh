#!/usr/bin/env bash
PW=zwinsight123
Q() { docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW -N -e '$1'" 2>/dev/null; }
{
  echo "tables=$(Q 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema="zw_insight"')"
  echo "sys_menu=$(Q 'SELECT COUNT(*) FROM zw_insight.sys_menu')"
  echo "sys_role=$(Q 'SELECT COUNT(*) FROM zw_insight.sys_role')"
  echo "sys_user=$(Q 'SELECT COUNT(*) FROM zw_insight.sys_user')"
} > /root/zwi-deploy/status.out 2>&1
cat /root/zwi-deploy/status.out
