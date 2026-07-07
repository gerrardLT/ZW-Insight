#!/usr/bin/env bash
# 乱码残留检测：干净中文 UTF-8 不含 0xC3(HEX '%C3%')，latin1 双编码乱码必含
PW=zwinsight123
DB=zw_insight
Q() { docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW --default-character-set=utf8mb4 -N -e \"$1\"" 2>/dev/null; }

echo "=== 乱码残留计数（应全部为 0）==="
echo "sys_menu.menu_name      = $(Q "SELECT COUNT(*) FROM $DB.sys_menu WHERE HEX(menu_name) LIKE '%C3%'")"
echo "sys_role.role_name      = $(Q "SELECT COUNT(*) FROM $DB.sys_role WHERE HEX(role_name) LIKE '%C3%'")"
echo "sys_user.real_name      = $(Q "SELECT COUNT(*) FROM $DB.sys_user WHERE HEX(real_name) LIKE '%C3%'")"
echo "sys_config.config_name  = $(Q "SELECT COUNT(*) FROM $DB.sys_config WHERE HEX(config_name) LIKE '%C3%'")"
echo "sys_config.remark       = $(Q "SELECT COUNT(*) FROM $DB.sys_config WHERE remark IS NOT NULL AND HEX(remark) LIKE '%C3%'")"
echo "sys_template.tpl_name   = $(Q "SELECT COUNT(*) FROM $DB.sys_template WHERE HEX(template_name) LIKE '%C3%'")"
echo "msg_shortcut.name       = $(Q "SELECT COUNT(*) FROM $DB.msg_available_shortcut WHERE HEX(name) LIKE '%C3%'")"

echo "=== 全部 91 条菜单名（逐条肉眼可核对） ==="
Q "SELECT id, menu_name FROM $DB.sys_menu ORDER BY id"
