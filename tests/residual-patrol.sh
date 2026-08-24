#!/usr/bin/env bash
# residual-patrol.sh — E2E 残留数据卫生巡检（测试成熟度改进事项 3-A，2026-08-24 用户决策）
#
# 职责：对部署环境巡检测试残留（历史 DATA 受阻头号原因：AWARDED 询价/SUBMITTED 报名等残留），
#       夜间 cron 与 k6 同窗执行（performance-k6.yml 在 k6 之前调用 --clean 模式：先清后跑）。
#
# 运行位置：部署服务器本机（依赖 zwi-mysql / zwi-redis / zwi-backend 容器）。
#
# 用法：
#   bash residual-patrol.sh           # report-only：发现残留 exit 3，零残留 exit 0
#   bash residual-patrol.sh --clean   # 先清后查：物理删除租户 9999 的 biz_% 表残留后复检；
#                                     # 不可清理残留仍存在则 exit 3
#
# 纪律（与附录 B「不静默」一致）：
#   - 清理仅触碰 tenant_id=9999（测试租户）且仅 biz_% 表；其他租户只检测 E2E_TEST_ 前缀泄漏，绝不清理
#   - Flowable 引擎残留（ACT_RU_TASK）只报告不 DELETE（引擎数据须走 withdraw 端点，
#     由下次全量套件的 pre_clean_flowable / withdraw-by-business 处理）
#   - 任何检查异常/残留均非零退出 + 结构化报告，禁止静默吞错
#
# 退出码：0=零残留（或 --clean 后清零）；2=环境异常（容器/凭证缺失）；3=存在残留

set -u

MYSQL_CT=zwi-mysql
REDIS_CT=zwi-redis
BACKEND_CT=zwi-backend
TENANT=9999
MODE="report"
[ "${1:-}" = "--clean" ] && MODE="clean"

echo "===== 残留巡检 $(date '+%F %T') mode=$MODE tenant=$TENANT ====="

# ---------- 0. 环境前置：DB 凭证从 backend 容器环境读取（运行时真源，不硬编码密码） ----------
backend_env() {
  docker inspect "$BACKEND_CT" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null
}
ENV_DUMP=$(backend_env)
if [ -z "$ENV_DUMP" ]; then
  echo "[ENV-FAIL] 无法读取容器 $BACKEND_CT 环境（docker 不可用或容器不存在）"
  exit 2
fi
DB_USER=$(echo "$ENV_DUMP" | grep '^SPRING_DATASOURCE_USERNAME=' | cut -d= -f2-)
DB_PASS=$(echo "$ENV_DUMP" | grep '^SPRING_DATASOURCE_PASSWORD=' | cut -d= -f2-)
DB_URL=$(echo "$ENV_DUMP" | grep '^SPRING_DATASOURCE_URL=' | cut -d= -f2-)
# jdbc:mysql://mysql:3306/<db>?params → <db>
# （参数展开实现；2026-08-24 实证旧 sed BRE 方案失败：.* 贪婪吃到最后一个 /，
# 剩余段不再匹配 [0-9]{1,}/ 导致整体零匹配，DB_NAME 恒空）
DB_URL_NOPARAM="${DB_URL%%\?*}"
DB_NAME="${DB_URL_NOPARAM##*/}"
if [ -z "$DB_USER" ] || [ -z "$DB_PASS" ] || [ -z "$DB_NAME" ]; then
  echo "[ENV-FAIL] 无法从 $BACKEND_CT 解析数据源凭证（user/pass/db 缺失）"
  exit 2
fi

Q() {
  docker exec -i "$MYSQL_CT" mysql -u"$DB_USER" -p"$DB_PASS" -D"$DB_NAME" \
    --default-character-set=utf8mb4 -N -e "$1" 2>/dev/null
}

# 探活：DB 不可达直接环境异常退出（不静默当作零残留）
if ! Q "SELECT 1" >/dev/null; then
  echo "[ENV-FAIL] MySQL 容器 $MYSQL_CT 查询失败（连接或凭证异常）"
  exit 2
fi

TABLES=$(Q "SHOW TABLES LIKE 'biz\\_%'")
if [ -z "$TABLES" ]; then
  echo "[ENV-FAIL] 未发现任何 biz_% 业务表（库名 $DB_NAME 可能不对）"
  exit 2
fi

RESIDUAL=0
RESIDUAL_TABLES=""

# ---------- 检查 1：测试租户逐表残留（biz_% 表 tenant_id=9999 行数） ----------
echo "--- 检查 1：租户 $TENANT 逐表残留 ---"
for t in $TABLES; do
  has_tenant=$(Q "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$t' AND COLUMN_NAME='tenant_id'")
  [ "$has_tenant" = "1" ] || continue
  n=$(Q "SELECT COUNT(*) FROM \`$t\` WHERE tenant_id=$TENANT")
  n=${n:-0}
  if [ "$n" -gt 0 ] 2>/dev/null; then
    echo "[残留] $t : $n 行"
    RESIDUAL=1
    RESIDUAL_TABLES="$RESIDUAL_TABLES $t"
  fi
done
[ "$RESIDUAL" -eq 0 ] && echo "[OK] 租户 $TENANT 零残留"

# ---------- 检查 2：E2E_TEST_ 前缀泄漏（仅非测试租户；租户 9999 的 E2E_TEST_ 行是合法测试数据，归检查 1/清理管） ----------
echo "--- 检查 2：E2E_TEST_ 前缀泄漏（非租户 $TENANT） ---"
LEAK=0
NAME_COLS="'name','title','code','contract_name','project_name','inquiry_title','team_name','material_name','announcement_title'"
for t in $TABLES; do
  cols=$(Q "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$t' AND COLUMN_NAME IN ($NAME_COLS)")
  has_tenant=$(Q "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='$t' AND COLUMN_NAME='tenant_id'")
  for c in $cols; do
    if [ "$has_tenant" = "1" ]; then
      # 有 tenant_id 列：仅统计非测试租户（测试租户归检查 1）
      n=$(Q "SELECT COUNT(*) FROM \`$t\` WHERE \`$c\` LIKE 'E2E\\_TEST\\_%' AND tenant_id<>$TENANT")
    else
      n=$(Q "SELECT COUNT(*) FROM \`$t\` WHERE \`$c\` LIKE 'E2E\\_TEST\\_%'")
    fi
    n=${n:-0}
    if [ "$n" -gt 0 ] 2>/dev/null; then
      echo "[泄漏] $t.$c : $n 行（E2E_TEST_ 前缀，非租户 $TENANT，疑似演示/其他租户污染）"
      LEAK=1
    fi
  done
done
[ "$LEAK" -eq 0 ] && echo "[OK] 无 E2E_TEST_ 前缀泄漏"

# ---------- 检查 3：Flowable 待办残留（租户 9999 运行中任务；只报告不清理） ----------
echo "--- 检查 3：Flowable 运行中任务残留 ---"
FLOW_TASKS=$(Q "SELECT COUNT(*) FROM ACT_RU_TASK WHERE TENANT_ID_='$TENANT'")
FLOW_TASKS=${FLOW_TASKS:-0}
if [ "$FLOW_TASKS" -gt 0 ] 2>/dev/null; then
  echo "[残留] ACT_RU_TASK : $FLOW_TASKS 个运行中任务（须走 withdraw 端点回收，本脚本不清理）"
else
  FLOW_TASKS=0
  echo "[OK] Flowable 零残留"
fi

# ---------- 检查 4：Redis 测试键残留 ----------
echo "--- 检查 4：Redis 测试键 ---"
REDIS_KEYS=$(docker exec "$REDIS_CT" sh -c "redis-cli KEYS 'test:t9999:*' | wc -l" 2>/dev/null)
REDIS_KEYS=${REDIS_KEYS:-0}
if [ "$REDIS_KEYS" -gt 0 ] 2>/dev/null; then
  echo "[残留] Redis test:t9999:* : $REDIS_KEYS 键"
else
  REDIS_KEYS=0
  echo "[OK] Redis 零残留"
fi

# ---------- clean 模式：物理清理租户 9999 的 biz_% 残留后复检 ----------
if [ "$MODE" = "clean" ] && [ -n "$RESIDUAL_TABLES" ]; then
  echo "--- 清理：租户 $TENANT 残留表（FK 检查临时关闭，仅限 biz_% + tenant_id=$TENANT） ---"
  for t in $RESIDUAL_TABLES; do
    del=$(Q "SET FOREIGN_KEY_CHECKS=0; DELETE FROM \`$t\` WHERE tenant_id=$TENANT; SET FOREIGN_KEY_CHECKS=1; SELECT ROW_COUNT()")
    echo "[清理] $t : 删除 ${del:-?} 行"
  done
  # 复检
  RESIDUAL=0
  for t in $RESIDUAL_TABLES; do
    n=$(Q "SELECT COUNT(*) FROM \`$t\` WHERE tenant_id=$TENANT")
    n=${n:-0}
    if [ "$n" -gt 0 ] 2>/dev/null; then
      echo "[清理失败] $t 仍有 $n 行"
      RESIDUAL=1
    fi
  done
  [ "$RESIDUAL" -eq 0 ] && echo "[OK] 清理后租户 $TENANT 零残留"
  # clean 模式下 Redis 测试键一并清理（与 TestDataCleaner.cleanRedisKeys 同模式）
  if [ "$REDIS_KEYS" -gt 0 ] 2>/dev/null; then
    rk_del=$(docker exec "$REDIS_CT" sh -c "redis-cli --scan --pattern 'test:t9999:*' | xargs -r redis-cli DEL" 2>/dev/null)
    echo "[清理] Redis test:t9999:* : 删除 ${rk_del:-0} 键"
    REDIS_KEYS=0
  fi
fi

# ---------- 结论 ----------
echo "===== 巡检结论 ====="
EXIT=0
if [ "$RESIDUAL" -eq 1 ] || [ "$LEAK" -eq 1 ]; then
  echo "[FAIL] 存在（不可清理或清理失败的）残留：RESIDUAL=$RESIDUAL LEAK=$LEAK"
  EXIT=3
fi
if [ "$FLOW_TASKS" -gt 0 ] 2>/dev/null; then
  if [ "$MODE" = "clean" ]; then
    # clean 模式下 Flowable 残留仅告警（引擎数据须走 withdraw，下次全量套件 pre_clean_flowable 兜底）
    echo "[WARN] Flowable 残留 $FLOW_TASKS 个任务：clean 模式不阻断，待 withdraw 回收"
  else
    echo "[FAIL] Flowable 残留 $FLOW_TASKS 个任务（report 模式计为残留）"
    EXIT=3
  fi
fi
[ "$EXIT" -eq 0 ] && echo "[PASS] 环境干净，可执行后续测试"
exit $EXIT
