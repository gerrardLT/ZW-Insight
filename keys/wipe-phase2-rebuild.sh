#!/bin/bash
###############################################################################
# Phase 2：账号体系重建（幂等）
#   1. SUPER_ADMIN 角色（data_scope=ALL）
#   2. admin 账号（123456 BCrypt）+ 角色绑定
#   3. SUPER_ADMIN 动态绑定全部菜单（SELECT FROM sys_menu）
#   4. 租户 9999 体系（重跑 init-test-tenant.sh）
#   5. 登录验证：admin + t9999admin + 菜单树
###############################################################################
set -uo pipefail
MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"
MYSQL_PWD="zwinsight123"
DB="zw_insight"
BASE="http://127.0.0.1:18080"
PASS_CNT=0; FAIL_CNT=0
ok()   { echo "PASS: $1"; PASS_CNT=$((PASS_CNT+1)); }
fail() { echo "FAIL: $1"; FAIL_CNT=$((FAIL_CNT+1)); }

run_sql() { docker exec -i "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" 2>&1 | grep -v "Using a password"; }
q() { docker exec -i "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "$1" 2>/dev/null; }

echo "===== 1-3. 重建 SUPER_ADMIN / admin / 菜单绑定 ====="
run_sql <<'SQL'
-- 超级管理员角色（37 号迁移口径：data_scope=ALL）
INSERT INTO sys_role (id, role_name, role_code, remark, status, tenant_id, data_scope, created_by, created_at, updated_at, deleted, version)
VALUES (1, '超级管理员', 'SUPER_ADMIN', '系统内置超级管理员（归零重建）', 1, NULL, 'ALL', 1, NOW(), NOW(), 0, 0);

-- admin 账号（密码 BCrypt = 123456）
INSERT INTO sys_user (id, username, password, real_name, phone, email, avatar, status, org_id, post_id, tenant_id, created_by, created_at, updated_at, deleted, version)
VALUES (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '13800000000', NULL, NULL, 1, NULL, NULL, NULL, 1, NOW(), NOW(), 0, 0);

-- admin 绑定 SUPER_ADMIN
INSERT INTO sys_user_role (id, user_id, role_id) VALUES (1, 1, 1);

-- SUPER_ADMIN 动态绑定全部菜单（比 99_data-menu.sql 硬编码 88 条更完整，新增菜单自动覆盖）
SET @i = 0;
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT @i := @i + 1, 1, id FROM sys_menu ORDER BY id;
SQL

U=$(q "SELECT COUNT(*) FROM sys_user WHERE username='admin';")
RM=$(q "SELECT COUNT(*) FROM sys_role_menu WHERE role_id=1;")
MN=$(q "SELECT COUNT(*) FROM sys_menu;")
[ "$U" = "1" ] && ok "admin 账号重建" || fail "admin 重建失败"
[ "$RM" = "$MN" ] && ok "SUPER_ADMIN 菜单绑定 $RM 条 = sys_menu $MN 条（全绑定）" || fail "菜单绑定 $RM != 菜单 $MN"

echo ""
echo "===== 4. 租户 9999 体系（init-test-tenant.sh）====="
if [ -f /tmp/init-test-tenant.sh ]; then
  bash /tmp/init-test-tenant.sh > /tmp/init9999.log 2>&1
  T9U=$(q "SELECT COUNT(*) FROM sys_user WHERE username='t9999admin';")
  T9R=$(q "SELECT COUNT(*) FROM serial_number_rule WHERE tenant_id=9999 AND deleted=0;")
  [ "$T9U" = "1" ] && ok "t9999admin 重建" || fail "t9999admin 缺失"
  [ "$T9R" -gt 40 ] 2>/dev/null && ok "租户 9999 编号规则 $T9R 条（T9 前缀）" || fail "9999 编号规则仅 $T9R 条"
else
  fail "init-test-tenant.sh 未上传"
fi

echo ""
echo "===== 5. 登录验证 ====="
login_check() {
  local user="$1" desc="$2"
  docker exec zwi-redis redis-cli DEL "login:ip:fail:127.0.0.1" "login:ip:lock:127.0.0.1" "login_fail:$user" >/dev/null 2>&1
  local cap uuid code resp token
  cap=$(curl -s -m 10 "$BASE/api/v1/captcha/image")
  uuid=$(echo "$cap" | grep -oE '"uuid"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*"uuid"[[:space:]]*:[[:space:]]*"//;s/"$//')
  code=$(docker exec zwi-redis redis-cli GET "captcha:$uuid" | tr -d '\r"')
  resp=$(curl -s -m 15 -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"password\":\"123456\",\"captchaUuid\":\"$uuid\",\"captchaCode\":\"$code\"}")
  token=$(echo "$resp" | grep -oE '"(accessToken|token)"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*:[[:space:]]*"//;s/"$//')
  if [ -n "$token" ]; then
    ok "$desc 登录成功"
    echo "$token"
  else
    fail "$desc 登录失败: $(echo "$resp" | head -c 200)"
  fi
}

ADMIN_TOKEN=$(login_check admin "admin" | tail -1)
# 菜单树验证
MENU_RESP=$(curl -s -m 10 "$BASE/api/v1/system/menu/user" -H "Authorization: Bearer $ADMIN_TOKEN")
MENU_CNT=$(echo "$MENU_RESP" | grep -oE '"id"[[:space:]]*:' | wc -l)
[ "$MENU_CNT" -gt 80 ] && ok "admin 菜单树 $MENU_CNT 节点（>80）" || fail "admin 菜单树异常: $MENU_CNT 节点"
T9_TOKEN=$(login_check t9999admin "t9999admin" | tail -1)

echo ""
echo "===== Phase 2 结果: PASS=$PASS_CNT FAIL=$FAIL_CNT ====="
[ "$FAIL_CNT" -eq 0 ] && echo "PHASE2_GREEN 可以开始交互测试" || echo "PHASE2_RED 需修复"
