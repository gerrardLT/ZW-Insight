#!/usr/bin/env bash
# ============================================================
# 任务 3.2：清表并以 utf8mb4 重灌种子
# 仅联调环境(dev)，需求 6.2/6.3/9.4
# ============================================================
set -u
PW=zwinsight123
DB=zw_insight
RD=/root/zwi-deploy/reseed
TS=$(date +%Y%m%d-%H%M%S)
BK=/root/zwi-deploy/backups

MYSQL() { docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW --default-character-set=utf8mb4 $DB" 2>&1; }
Q() { docker exec -i zwi-mysql sh -c "mysql -uroot -p$PW --default-character-set=utf8mb4 -N -e \"$1\"" 2>/dev/null; }

# ---------- 0. 环境与前置确认 ----------
PROFILE=$(docker inspect zwi-backend --format '{{range .Config.Env}}{{println .}}{{end}}' | grep SPRING_PROFILES_ACTIVE | head -1)
echo "环境标识: $PROFILE"
case "$PROFILE" in
  *prod*) echo "[ABORT] 检测到生产环境标识，终止重灌"; exit 1;;
esac
echo "[OK] 非生产环境，继续"

# ---------- 1. 即时安全备份（受影响表） ----------
echo "=== 重灌前即时备份 ==="
docker exec zwi-mysql sh -c "mysqldump -uroot -p$PW --default-character-set=utf8mb4 --skip-extended-insert $DB sys_menu sys_role sys_role_menu sys_user sys_user_role sys_config sys_template msg_available_shortcut" 2>/dev/null | gzip > $BK/reseed-pre-$TS.sql.gz
ls -lh $BK/reseed-pre-$TS.sql.gz

# ---------- 2. 流式重灌（utf8mb4 客户端） ----------
echo "=== 执行重灌 ==="
ERR=$( {
  echo "SET NAMES utf8mb4;"
  echo "SET FOREIGN_KEY_CHECKS=0;"
  echo "TRUNCATE TABLE sys_role_menu;"
  echo "TRUNCATE TABLE sys_user_role;"
  echo "TRUNCATE TABLE sys_menu;"
  echo "TRUNCATE TABLE sys_role;"
  echo "TRUNCATE TABLE sys_user;"
  cat "$RD/99.sql"
  cat "$RD/dict.sql"
  echo "SET FOREIGN_KEY_CHECKS=1;"
} | MYSQL | grep -iv "Using a password" )
if [ -n "$ERR" ]; then
  echo "[ERR] 重灌出现错误:"; echo "$ERR" | head -30
else
  echo "[OK] 重灌无错误"
fi

# ---------- 3. 核对：行数 ----------
echo "=== 重灌后行数 ==="
for t in sys_menu sys_role sys_role_menu sys_user sys_user_role sys_config sys_template msg_available_shortcut; do
  echo "$t = $(Q "SELECT COUNT(*) FROM $DB.$t")"
done

# ---------- 4. 核对：中文明文（utf8mb4 读取） ----------
echo "=== 侧边栏顶层模块(parent_id=0) ==="
Q "SELECT id, menu_name FROM $DB.sys_menu WHERE parent_id=0 ORDER BY id"
echo "=== 基础数据子菜单(parent_id=18) ==="
Q "SELECT id, menu_name FROM $DB.sys_menu WHERE parent_id=18 ORDER BY id"
echo "=== sys_role / sys_user ==="
Q "SELECT id, role_name FROM $DB.sys_role"
Q "SELECT id, real_name FROM $DB.sys_user"
echo "=== sys_config 抽样 ==="
Q "SELECT id, config_name FROM $DB.sys_config ORDER BY id LIMIT 5"
echo "=== sys_template / msg_available_shortcut 抽样 ==="
Q "SELECT id, template_name FROM $DB.sys_template ORDER BY id LIMIT 3"
Q "SELECT id, name FROM $DB.msg_available_shortcut ORDER BY id LIMIT 3"

# ---------- 5. 核对：首页 HEX 应为 E9A696E9A1B5 ----------
echo "=== 首页 HEX(应=E9A696E9A1B5) ==="
Q "SELECT HEX(menu_name) FROM $DB.sys_menu WHERE id=1"
echo "=== 完成 ==="
