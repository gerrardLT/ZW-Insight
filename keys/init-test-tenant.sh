#!/usr/bin/env bash
###############################################################################
# init-test-tenant.sh — 初始化 L4 自动化测试租户（tenant_id=9999）
#
# 背景：full-layer-test-suite 需求 5.1 要求 L4 生命周期模拟运行在隔离租户
#       tenant_id=9999 上，与演示租户 1 完全隔离。
#       后端 POST /api/v1/platform/tenant 仅创建租户+管理员，不初始化编号规则，
#       且租户 ID 为雪花生成无法固定为 9999，故采用 SQL 种子方式（幂等）。
#
# 初始化内容（全部 INSERT IGNORE / 条件 UPDATE，可重复执行）：
#   1. sys_tenant id=9999：开通全部 12 个功能模块（TenantModuleInterceptor 要求）
#   2. sys_user t9999admin/123456（与 99_data-menu.sql admin 同 BCrypt 哈希）
#   3. sys_user_role：绑定全局 SUPER_ADMIN(role_id=1)
#      —— 权限拦截器对 SUPER_ADMIN 豁免；37 号迁移已置 data_scope=ALL
#   3c. t9999user/123456 低权限用户（无任何权限点的 T9999_LIMITED 角色，
#       data_scope=SELF）——供 test-api-authz.sh 越权 403 负向断言
#   4. serial_number_rule：动态复制租户 1 全部规则到 9999（id+95000 偏移，
#      uk_business_tenant 唯一键 + INSERT IGNORE 保证幂等）
#
# 运行位置：服务器 129.204.3.200（/root/zwi-deploy）
# 用法：bash init-test-tenant.sh
###############################################################################
set -uo pipefail

MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"
MYSQL_PASS="${ZWI_MYSQL_PASS:-zwinsight123}"
DB="zw_insight"

run_sql() {
  docker exec -i "$MYSQL_CT" mysql -uroot -p"$MYSQL_PASS" "$DB" 2>&1 | grep -v "Using a password"
}

echo "=== init test tenant 9999 ==="

run_sql <<'SQL'
-- 1. 测试租户（全模块开通，有效期到 2099，避免登录被租户过期校验拦截）
INSERT IGNORE INTO sys_tenant
  (id, tenant_code, tenant_name, contact_name, contact_phone, status,
   expire_date, start_date, end_date, max_users, user_type, modules,
   created_at, updated_at)
VALUES
  (9999, 'T9999', 'ZWI-AutoTest-Tenant', 'AutoTest', '13900009999', 1,
   '2099-12-31', '2026-01-01', '2099-12-31', 50, 'ENTERPRISE',
   JSON_ARRAY('TENDER','BUDGET','PURCHASE','LABOR','MATERIAL','MACHINE',
              'SUBCONTRACT','SITE','FINANCE','HR','PRICE_COMPARE','DASHBOARD'),
   NOW(), NOW());

-- 已存在时也确保模块/状态/有效期正确（幂等修正）
UPDATE sys_tenant SET
  modules = JSON_ARRAY('TENDER','BUDGET','PURCHASE','LABOR','MATERIAL','MACHINE',
                       'SUBCONTRACT','SITE','FINANCE','HR','PRICE_COMPARE','DASHBOARD'),
  status = 1, expire_date = '2099-12-31', end_date = '2099-12-31'
WHERE id = 9999;

-- 2. 测试租户管理员（密码 123456，哈希与 99_data-menu.sql admin 一致）
INSERT IGNORE INTO sys_user
  (id, username, password, real_name, phone, email, avatar, status,
   org_id, post_id, tenant_id, created_by, created_at, updated_at, deleted, version)
VALUES
  (9999001, 't9999admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
   'T9999 Admin', '13900009999', NULL, NULL, 1,
   NULL, NULL, 9999, 1, NOW(), NOW(), 0, 0);

-- 3. 绑定全局 SUPER_ADMIN 角色（id=1，tenant_id=NULL 全局角色）
INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES (9999001, 9999001, 1);

-- 3b. 补齐 37 号迁移（该库未执行过）：超管数据范围应为 ALL，
--     否则行级数据权限按 SELF 过滤，审批人看不到他人单据
UPDATE sys_role SET data_scope = 'ALL' WHERE role_code = 'SUPER_ADMIN';

-- 4. 编号规则：复制租户 1 全部规则到 9999，但前缀加 'T9' 前缀
--    原因：biz_project.uk_project_code 等业务编号唯一键为全局唯一（不含 tenant_id），
--    而编号生成按租户计数，两租户同日创建会生成相同编号而撞键。
--    给测试租户独立前缀，保证编号全局不与租户 1 冲突（不改后端）。
--    先 DELETE 再 INSERT，确保已存在的 9999 规则也被修正为新前缀（幂等）。
DELETE FROM serial_number_rule WHERE tenant_id = 9999;
INSERT INTO serial_number_rule
  (id, business_type, rule_prefix, date_format, seq_length, reset_period,
   description, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT id + 95000, business_type, CONCAT('T9', rule_prefix), date_format, seq_length, reset_period,
       description, 9999, 1, NOW(), NOW(), 0, 0
FROM serial_number_rule
WHERE tenant_id = 1 AND deleted = 0;

-- 5. 越权负向测试专用低权限用户（2026-08-13）：
--    绑定无任何权限点的受限角色（data_scope=SELF），供 test-api-authz.sh 断言
--    @RequiresPermission 接口返回 403（SUPER_ADMIN 豁免机制的反面验证）。
INSERT IGNORE INTO sys_role
  (id, role_name, role_code, remark, status, tenant_id, data_scope,
   created_by, created_at, updated_at, deleted, version)
VALUES
  (9999900, 'T9999受限角色', 'T9999_LIMITED', '自动化测试低权限角色：无任何权限点，供越权403负向断言',
   1, 9999, 'SELF', 1, NOW(), NOW(), 0, 0);
INSERT IGNORE INTO sys_user
  (id, username, password, real_name, phone, email, avatar, status,
   org_id, post_id, tenant_id, created_by, created_at, updated_at, deleted, version)
VALUES
  (9999002, 't9999user', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
   'T9999 Limited User', '13900009998', NULL, NULL, 1,
   NULL, NULL, 9999, 1, NOW(), NOW(), 0, 0);
INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES (9999002, 9999002, 9999900);
-- 确保受限角色无任何权限点（幂等清理，防历史残留授权干扰 403 断言）
DELETE FROM sys_role_menu WHERE role_id = 9999900;
SQL

echo "=== verify ==="
run_sql <<'SQL'
SELECT id, tenant_code, status, end_date, JSON_LENGTH(modules) AS module_cnt
FROM sys_tenant WHERE id = 9999;
SELECT id, username, tenant_id, status FROM sys_user WHERE id = 9999001;
SELECT ur.user_id, ur.role_id, r.role_code, r.data_scope
FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
WHERE ur.user_id IN (9999001, 9999002);
SELECT COUNT(*) AS serial_rule_cnt FROM serial_number_rule WHERE tenant_id = 9999 AND deleted = 0;
SQL

echo "=== done ==="
