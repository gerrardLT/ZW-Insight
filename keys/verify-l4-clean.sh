#!/usr/bin/env bash
# _verify-clean.sh — 验收：清理零残留 + 基建保留 + 租户1零污染
set -uo pipefail
MYSQL="docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -B"

echo "=== 1. 租户9999 业务表残留行数（应为0）==="
# 提高 group_concat 长度上限，避免拼接 SQL 被截断导致统计失真
SUMSQL=$($MYSQL -e "SET SESSION group_concat_max_len=1000000; SELECT GROUP_CONCAT('SELECT COUNT(*) c FROM \`',TABLE_NAME,'\` WHERE tenant_id=9999' SEPARATOR ' UNION ALL ') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zw_insight' AND COLUMN_NAME='tenant_id' AND TABLE_NAME LIKE 'biz_%';" 2>/dev/null)
TOTAL=$($MYSQL -e "SELECT COALESCE(SUM(c),0) FROM ($SUMSQL) x;" 2>/dev/null)
echo "biz_ 表 tenant_id=9999 残留总行数: $TOTAL"

echo "=== 2. Flowable 运行时任务残留（应为0）==="
$MYSQL -e "SELECT COUNT(*) FROM ACT_RU_TASK WHERE TENANT_ID_='9999';" 2>/dev/null

echo "=== 3. 测试基建保留（admin+编号规则应存在）==="
$MYSQL -e "SELECT COUNT(*) FROM sys_user WHERE id=9999001; SELECT COUNT(*) FROM serial_number_rule WHERE tenant_id=9999 AND deleted=0;" 2>/dev/null

echo "=== 4. 租户1零污染（不应有本测试名称的项目落到租户1）==="
$MYSQL -e "SELECT COUNT(*) FROM biz_project WHERE tenant_id=1 AND project_name LIKE '%测试%中维综合楼%';" 2>/dev/null

echo "=== done ==="
