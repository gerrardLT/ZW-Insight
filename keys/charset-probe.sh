#!/usr/bin/env bash
# 探测 zw_insight 种子相关表中文是否乱码（与 UTF-8 原文比对）
# 输出 HEX，编码无关，便于与种子源对照
PW=zwinsight123
DB=zw_insight
Q() { docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW --default-character-set=utf8mb4 -N -e \"$1\""; }

echo "=== 连接字符集 ==="
Q "SHOW VARIABLES LIKE 'character_set_client'"
Q "SHOW VARIABLES LIKE 'character_set_connection'"

echo "=== sys_menu 顶层(parent_id=0) id / 明文 / HEX ==="
Q "SELECT id, menu_name, HEX(menu_name) FROM $DB.sys_menu WHERE parent_id=0 ORDER BY id"

echo "=== sys_role ==="
Q "SELECT id, role_name, HEX(role_name) FROM $DB.sys_role ORDER BY id"

echo "=== sys_user real_name ==="
Q "SELECT id, real_name, HEX(real_name) FROM $DB.sys_user ORDER BY id"

echo "=== 抽样：基础数据子菜单(parent_id=18) ==="
Q "SELECT id, menu_name, HEX(menu_name) FROM $DB.sys_menu WHERE parent_id=18 ORDER BY id"
