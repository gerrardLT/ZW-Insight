#!/usr/bin/env bash
# 全面探测候选种子表：行数 + 中文乱码抽样
PW=zwinsight123
DB=zw_insight
Q() { docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW --default-character-set=utf8mb4 -N -e \"$1\"" 2>/dev/null; }

echo "=== 行数统计 ==="
for t in sys_menu sys_role sys_role_menu sys_user sys_user_role sys_config sys_template msg_available_shortcut sys_budget_control_config; do
  c=$(Q "SELECT COUNT(*) FROM $DB.$t")
  echo "$t = ${c:-MISSING}"
done

echo "=== sys_config 抽样(中文 config_name) ==="
Q "SELECT id, config_name FROM $DB.sys_config ORDER BY id LIMIT 5"

echo "=== sys_template 抽样 ==="
Q "SELECT id, template_name FROM $DB.sys_template ORDER BY id LIMIT 5"

echo "=== msg_available_shortcut 抽样 ==="
Q "SELECT id, name FROM $DB.msg_available_shortcut ORDER BY id LIMIT 5"

echo "=== sys_user 全部(检查是否仅 admin 种子) ==="
Q "SELECT id, username, real_name FROM $DB.sys_user ORDER BY id"

echo "=== sys_role 全部 ==="
Q "SELECT id, role_code, role_name FROM $DB.sys_role ORDER BY id"
