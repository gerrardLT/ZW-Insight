#!/bin/bash
###############################################################################
# Phase 1：业务数据归零清空（白名单制）
# 前置：Phase 0 备份已 SUCCESS（备份 ID 2089258929198526465, 24.5MB）
# 保留白名单：sys_menu / sys_tenant / sys_tenant_type / sys_config /
#   sys_version / sys_backup_record / sys_config_change_log(审计留痕) /
#   serial_number_rule / flyway_schema_history /
#   ACT_RE_* / ACT_GE_* / ACT_PROCDEF_INFO / wf_process_def / wf_business_type
# 清空：biz_*(116) / bd_*(5) / msg_*(8) / file_*(2) / wf 运行痕迹(4) /
#   ACT_RU_*(14) / ACT_HI_*(9) / ACT_EVT_LOG / FLW_RU_*(2) / _tmp_deltest /
#   账号类 sys_*(16)
###############################################################################
set -uo pipefail
MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"
REDIS_CT="${ZWI_REDIS_CT:-zwi-redis}"
MYSQL_PWD="zwinsight123"
DB="zw_insight"
PASS_CNT=0; FAIL_CNT=0
ok()   { echo "PASS: $1"; PASS_CNT=$((PASS_CNT+1)); }
fail() { echo "FAIL: $1"; FAIL_CNT=$((FAIL_CNT+1)); }

echo "===== 0. 前置断言：清空前备份存在 ====="
BK_CNT=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e \
  "SELECT COUNT(*) FROM sys_backup_record WHERE id=2089258929198526465 AND status='SUCCESS';" 2>/dev/null)
if [ "$BK_CNT" != "1" ]; then
  fail "清空前备份记录不存在/非 SUCCESS，中止清空"
  exit 1
fi
ok "清空前备份 2089258929198526465 存在且 SUCCESS"

echo ""
echo "===== 1. 动态清空 biz_* (116 张) ====="
docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e \
  "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB' AND table_name LIKE 'biz\_%';" 2>/dev/null > /tmp/wipe_biz.txt
BIZ_CNT=$(wc -l < /tmp/wipe_biz.txt)
echo "发现 biz_* 表 $BIZ_CNT 张"
SQL_BIZ="SET FOREIGN_KEY_CHECKS=0;"
while read -r t; do SQL_BIZ="$SQL_BIZ TRUNCATE TABLE \`$t\`;"; done < /tmp/wipe_biz.txt
SQL_BIZ="$SQL_BIZ SET FOREIGN_KEY_CHECKS=1;"
echo "$SQL_BIZ" | docker exec -i "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" 2>/dev/null
LEFT=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e \
  "SELECT COUNT(*) FROM biz_project;" 2>/dev/null)
[ "$LEFT" = "0" ] && ok "biz_* 全部 TRUNCATE（抽查 biz_project=0）" || fail "biz_project 残留 $LEFT"

echo ""
echo "===== 2. 基础数据/消息/文件表（显式清单）====="
EXPLICIT_TABLES="bd_company bd_inspection_scheme bd_material bd_material_category bd_owner bd_supplier
file_info file_storage
msg_announcement msg_available_shortcut msg_message msg_notice msg_push_config msg_template msg_user_shortcut
wf_approval_record wf_delegate_config wf_rollback_action wf_urge_record
_tmp_deltest"
SQL_EX="SET FOREIGN_KEY_CHECKS=0;"
for t in $EXPLICIT_TABLES; do
  EXISTS=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB' AND table_name='$t';" 2>/dev/null)
  if [ "$EXISTS" = "1" ]; then SQL_EX="$SQL_EX TRUNCATE TABLE \`$t\`;"; fi
done
SQL_EX="$SQL_EX SET FOREIGN_KEY_CHECKS=1;"
echo "$SQL_EX" | docker exec -i "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" 2>/dev/null
M1=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "SELECT COUNT(*) FROM bd_material;" 2>/dev/null)
M2=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "SELECT COUNT(*) FROM msg_message;" 2>/dev/null)
[ "$M1" = "0" ] && [ "$M2" = "0" ] && ok "bd_*/msg_*/file_*/wf痕迹 全部清空" || fail "显式清单残留 bd_material=$M1 msg_message=$M2"

echo ""
echo "===== 3. Flowable 运行时 + 历史（ACT_RE_*/ACT_GE_* 保留）====="
docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e \
  "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB' AND (table_name LIKE 'ACT\_RU\_%' OR table_name LIKE 'ACT\_HI\_%' OR table_name LIKE 'FLW\_RU\_%' OR table_name='ACT_EVT_LOG');" 2>/dev/null > /tmp/wipe_act.txt
ACT_CNT=$(wc -l < /tmp/wipe_act.txt)
echo "发现 Flowable 运行时/历史表 $ACT_CNT 张"
SQL_ACT="SET FOREIGN_KEY_CHECKS=0;"
while read -r t; do SQL_ACT="$SQL_ACT TRUNCATE TABLE \`$t\`;"; done < /tmp/wipe_act.txt
SQL_ACT="$SQL_ACT SET FOREIGN_KEY_CHECKS=1;"
echo "$SQL_ACT" | docker exec -i "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" 2>/dev/null
T=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "SELECT COUNT(*) FROM ACT_RU_TASK;" 2>/dev/null)
E=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "SELECT COUNT(*) FROM ACT_HI_PROCINST;" 2>/dev/null)
P=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "SELECT COUNT(*) FROM ACT_RE_PROCDEF;" 2>/dev/null)
[ "$T" = "0" ] && [ "$E" = "0" ] && ok "Flowable 运行时+历史清零（ACT_RU_TASK=0, ACT_HI_PROCINST=0）" || fail "Flowable 残留 task=$T procinst=$E"
[ "$P" -gt 0 ] 2>/dev/null && ok "流程定义保留（ACT_RE_PROCDEF=$P 未动）" || fail "流程定义丢失！"

echo ""
echo "===== 4. 账号类 sys_* 清空（sys_menu/sys_tenant/sys_tenant_type/sys_config/sys_version/sys_backup_record/sys_config_change_log 保留）====="
ACCOUNT_TABLES="sys_user sys_user_role sys_role sys_role_menu sys_org sys_post sys_dict sys_dict_item
sys_tenant_menu sys_user_project sys_login_device sys_oper_log sys_login_log sys_audit_log
sys_template sys_supplier_account sys_backup_restore_log sys_budget_control_config"
SQL_ACC="SET FOREIGN_KEY_CHECKS=0;"
for t in $ACCOUNT_TABLES; do SQL_ACC="$SQL_ACC TRUNCATE TABLE \`$t\`;"; done
SQL_ACC="$SQL_ACC SET FOREIGN_KEY_CHECKS=1;"
echo "$SQL_ACC" | docker exec -i "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" 2>/dev/null
U=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "SELECT COUNT(*) FROM sys_user;" 2>/dev/null)
R=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "SELECT COUNT(*) FROM sys_role;" 2>/dev/null)
MN=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "SELECT COUNT(*) FROM sys_menu;" 2>/dev/null)
TN=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "SELECT COUNT(*) FROM sys_tenant;" 2>/dev/null)
SN=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PWD" "$DB" -N -e "SELECT COUNT(*) FROM serial_number_rule;" 2>/dev/null)
[ "$U" = "0" ] && [ "$R" = "0" ] && ok "账号类清零（sys_user=0, sys_role=0）" || fail "账号残留 user=$U role=$R"
[ "$MN" -gt 80 ] 2>/dev/null && ok "sys_menu 保留（$MN 条）" || fail "sys_menu 丢失！"
[ "$TN" = "2" ] && ok "sys_tenant 保留（2 条）" || fail "sys_tenant 异常: $TN"
[ "$SN" -gt 40 ] 2>/dev/null && ok "serial_number_rule 保留（$SN 条）" || fail "编号规则丢失！"

echo ""
echo "===== 5. Redis FLUSHDB（编号序列+登录态归零）====="
docker exec "$REDIS_CT" redis-cli FLUSHDB >/dev/null
DS=$(docker exec "$REDIS_CT" redis-cli DBSIZE)
[ "$DS" = "0" ] && ok "Redis 清零（DBSIZE=0）" || fail "Redis 残留 $DS 键"

echo ""
echo "===== 6. MinIO：保留 backup/db/，清除其余对象 ====="
docker exec zwi-minio sh -c 'mc alias set local http://127.0.0.1:9000 minioadmin minioadmin >/dev/null 2>&1 && mc ls local/' 2>/dev/null || echo "(mc 不可用，跳过对象清理，仅备份保留在 backup/db/)"
# 逐桶清理非 backup/ 前缀对象
docker exec zwi-minio sh -c '
mc alias set local http://127.0.0.1:9000 minioadmin minioadmin >/dev/null 2>&1
for b in $(mc ls local/ 2>/dev/null | awk -F/ "{print \$1}"); do
  echo "桶: $b"
  # 列出非 backup/ 前缀对象并删除
  mc ls --recursive "local/$b/" 2>/dev/null | grep -v "\sbackup/" | awk "{print \$NF}" | while read -r o; do
    mc rm --force "local/$b/$o" >/dev/null 2>&1
  done
done
echo "清理后剩余对象:"
mc ls --recursive local/ 2>/dev/null | head -20
' 2>/dev/null || echo "(MinIO 清理步骤异常，人工复核)"

echo ""
echo "===== Phase 1 结果: PASS=$PASS_CNT FAIL=$FAIL_CNT ====="
[ "$FAIL_CNT" -eq 0 ] && echo "PHASE1_GREEN 可以进入重建" || echo "PHASE1_RED 需人工复核"
