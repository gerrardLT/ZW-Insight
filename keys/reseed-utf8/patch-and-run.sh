#!/usr/bin/env bash
# 修正 99.sql 的 sys_role INSERT（移除实际表不存在的 sort_order 列）后重跑重灌
set -u
RD=/root/zwi-deploy/reseed

echo "=== 修正前 sys_role 行 ==="
grep -n "sys_role (id" "$RD/99.sql"

# 1) 列清单去掉 sort_order
sed -i 's/INSERT INTO sys_role (id, role_name, role_code, sort_order, status, tenant_id, created_at)/INSERT INTO sys_role (id, role_name, role_code, status, tenant_id, created_at)/' "$RD/99.sql"
# 2) 值列表去掉 sort_order 的值（紧跟 SUPER_ADMIN 之后那个 1,）
sed -i "s/'SUPER_ADMIN', 1, 1, NULL, NOW());/'SUPER_ADMIN', 1, NULL, NOW());/" "$RD/99.sql"

echo "=== 修正后 sys_role 行 ==="
grep -n "sys_role (id" "$RD/99.sql"
grep -n "SUPER_ADMIN" "$RD/99.sql"

echo "=== 重新执行重灌 ==="
bash "$RD/run-reseed.sh"
