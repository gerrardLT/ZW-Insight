#!/usr/bin/env bash
# ============================================================
# cleanup-garbage-data.sh — R7 垃圾数据大扫除（阶段 D-0 + D-1）
#
# 背景（2026-09-18 数据审计 Round 7 取证）：
#   R7-02 缺陷：ProjectService.delete 原仅删 biz_project 单表，372 个已删项目
#               遗留 ~2000 条孤儿子表；另有 32 个测试标记项目存活未清。
#   R7-03 污染：API/E2E 测试脚本拿演示项目当靶子反复提交 1 元单据，
#               导致 91501/91801/90001 的累计值被抬高（+35 / +250 / +35）。
#
# 用法：
#   bash cleanup-garbage-data.sh              # dry-run（默认）：只统计，零写入
#   bash cleanup-garbage-data.sh --execute    # 真实执行：先 mysqldump 全量备份再删
#
# 安全约束：
#   * 默认 dry-run，未显式传 --execute 绝不写库
#   * execute 模式强制先备份，备份校验不通过则中止
#   * 所有回滚 UPDATE 带「当前值 = 已知污染值」条件，天然幂等且不会误改干净行
#   * 演示种子项目（90001-90004）只删雪花 ID 垃圾，绝不触碰 90xxx 种子行
#   * ACT_RU_TASK 只报告不删除（见文末说明）
# ============================================================
set -uo pipefail

MYSQL_CT="${MYSQL_CT:-zwi-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PW="${MYSQL_PW:-zwinsight123}"
DB="${MYSQL_DATABASE:-zw_insight}"
BACKUP_DIR="${BACKUP_DIR:-/root/zwi-deploy/backups}"

MODE="dry-run"
[ "${1:-}" = "--execute" ] && MODE="execute"

# 字符集固化 utf8mb4：防中文双重编码（同 deploy/run-migration.sh 的加固理由）
MY="docker exec $MYSQL_CT mysql -u$MYSQL_USER -p$MYSQL_PW --default-character-set=utf8mb4 $DB"
Q()  { $MY -N -B -e "$1" 2>/dev/null; }
QT() { $MY -t -e "$1" 2>/dev/null; }

# 测试标记项目判定式（与 _garbage_probe.sh 的 E/F 节完全一致）
# 已核对：4 个演示种子项目名（滨江花园一期工程 / 城南市政道路改造 /
# 高新区产业园二期 / 城北河道综合整治工程）均不匹配任一分支
TEST_IDS="2089276036854378498,2090675172153552898"  # A(归零演示 CLOSED)+B(ceshi DRAFT) —— Probe #4 确认为测试垃圾
TEST_PRED="project_name LIKE 'E2E_TEST_%' OR project_name REGEXP '_1[0-9]{12}' OR project_name LIKE 'B4验证%' OR project_name LIKE 'B3验证%' OR project_name LIKE 'API测试项目%' OR project_name LIKE 'E2E审批UI%'"
TEST_PRED_EXTENDED="$TEST_PRED OR id IN ($TEST_IDS)"

# 演示项目 ID 段（种子固定 ID）
DEMO="project_id BETWEEN 90001 AND 90004"
SEED_ID="id BETWEEN 90001 AND 99999"

TOTAL_DELETED=0

echo "############################################################"
echo "# ZW-Insight 垃圾数据大扫除（R7 阶段 D）"
echo "# 模式: $MODE"
echo "# 时间: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "############################################################"

# ------------------------------------------------------------
# 0. 前置连通性检查
# ------------------------------------------------------------
DBNAME=$(Q "SELECT DATABASE();")
if [ "$DBNAME" != "$DB" ]; then
  echo "FATAL: 数据库连接失败（期望 $DB，实际 '$DBNAME'）" >&2
  exit 1
fi
echo ""
echo "=== 0. 连接确认 ==="
echo "  数据库: $DBNAME  容器: $MYSQL_CT"
echo "  biz_project 现状: 存活=$(Q "SELECT COUNT(*) FROM biz_project WHERE deleted=0;") 已删=$(Q "SELECT COUNT(*) FROM biz_project WHERE deleted=1;")"

# 安全不变量①：测试项目判定式绝不可命中 4 个演示种子项目。
# 加此断言的缘由：2026-09-18 本脚本的 TEST_PRED 曾因中文字面量被误插空格
# （'API测试项目%' 写成 'API 测试项目%'）而漏掉 11 个测试项目，而 dry-run
# 总数又因新增 A+B 的子表行数巧合抵消而未变，仅靠人工比对命中清单才发现。
# 断言 + 分支明细可让此类漂移立即暴露。
SEED_HIT=$(Q "SELECT COUNT(*) FROM biz_project WHERE id BETWEEN 90001 AND 90004 AND ($TEST_PRED_EXTENDED);")
if [ "${SEED_HIT:-1}" != "0" ]; then
  echo "FATAL: 测试项目判定式误命中 ${SEED_HIT:-?} 个演示种子项目，已中止（未写库）。" >&2
  exit 3
fi
echo "  ✓ 不变量①：判定式未命中任何演示种子项目（90001-90004）"

# 安全不变量②：逐分支命中数明细，防某一分支静默失效（如中文字面量损坏）
echo "  判定分支命中明细（存活项目）:"
for BR in "project_name LIKE 'E2E_TEST_%'" \
          "project_name REGEXP '_1[0-9]{12}'" \
          "project_name LIKE 'B4验证%'" \
          "project_name LIKE 'B3验证%'" \
          "project_name LIKE 'API测试项目%'" \
          "project_name LIKE 'E2E审批UI%'" \
          "id IN ($TEST_IDS)"; do
  N=$(Q "SELECT COUNT(*) FROM biz_project WHERE deleted=0 AND ($BR);")
  printf "    %-46s %s\n" "$BR" "${N:-ERR}"
done

# ------------------------------------------------------------
# 1. 备份（仅 execute 模式，且校验通过才继续）
# ------------------------------------------------------------
if [ "$MODE" = "execute" ]; then
  echo ""
  echo "=== 1. mysqldump 全量备份 ==="
  mkdir -p "$BACKUP_DIR"
  TS=$(date -u +%Y%m%dT%H%M%SZ)
  BACKUP_FILE="$BACKUP_DIR/zw_insight_pre_cleanup_$TS.sql"
  docker exec "$MYSQL_CT" mysqldump -u"$MYSQL_USER" -p"$MYSQL_PW" \
    --default-character-set=utf8mb4 --single-transaction --routines --triggers \
    "$DB" > "$BACKUP_FILE" 2>/dev/null
  SIZE=$(stat -c%s "$BACKUP_FILE" 2>/dev/null || echo 0)
  HASDDL=$(grep -c 'CREATE TABLE' "$BACKUP_FILE" 2>/dev/null || echo 0)
  echo "  备份文件: $BACKUP_FILE"
  echo "  大小: $SIZE 字节   CREATE TABLE 数: $HASDDL"
  # 校验门槛：219 张表 → 至少 200 个 CREATE TABLE，且体积 > 1MB
  if [ "${SIZE:-0}" -lt 1000000 ] || [ "${HASDDL:-0}" -lt 200 ]; then
    echo "FATAL: 备份校验未通过（体积或表数不足），中止执行。未做任何写操作。" >&2
    exit 2
  fi
  echo "  ✓ 备份校验通过"
else
  echo ""
  echo "=== 1. 备份（dry-run 模式跳过）==="
  echo "  ℹ 真实执行前会自动 mysqldump 到 $BACKUP_DIR 并校验"
fi

# ------------------------------------------------------------
# 辅助：统计或执行一条 DELETE
#   run_delete <说明> <表名> <WHERE 条件>
# ------------------------------------------------------------
run_delete() {
  local label="$1" tbl="$2" where="$3"
  local cnt
  cnt=$(Q "SELECT COUNT(*) FROM \`$tbl\` WHERE $where;")
  cnt="${cnt:-0}"
  if [ "$MODE" = "execute" ] && [ "$cnt" -gt 0 ]; then
    Q "DELETE FROM \`$tbl\` WHERE $where;"
    local left
    left=$(Q "SELECT COUNT(*) FROM \`$tbl\` WHERE $where;")
    printf "  %-34s 删除 %6s 行   残留 %s\n" "$label" "$cnt" "${left:-?}"
    [ "${left:-1}" != "0" ] && echo "    WARN: $tbl 仍有残留，可能触发锁等待，请复核"
  else
    printf "  %-34s 待删 %6s 行\n" "$label" "$cnt"
  fi
  TOTAL_DELETED=$((TOTAL_DELETED + cnt))
}

# ============================================================
# D-0：演示项目（90001-90004）下的测试垃圾
# ============================================================
echo ""
echo "=== D-0. 演示项目下的雪花 ID 垃圾单据 ==="
echo "  判据: $DEMO AND NOT $SEED_ID"
echo "  取证结论: 除 payment_apply/purchase_settlement/construction_contract/material_stock 外，"
echo "            其余垃圾均已是 deleted=1（不影响 deleted=0 聚合），物理清除属卫生整理"

# 动态取所有含 project_id 的表（排除 biz_project 自身），逐表统计雪花残留
TABLES=$(Q "SELECT TABLE_NAME FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA='$DB' AND COLUMN_NAME='project_id'
              AND TABLE_NAME<>'biz_project' ORDER BY TABLE_NAME;")
D0_TABLES=""
for T in $TABLES; do
  C=$(Q "SELECT COUNT(*) FROM \`$T\` WHERE $DEMO AND NOT $SEED_ID;")
  if [ "${C:-0}" != "0" ]; then
    run_delete "$T" "$T" "$DEMO AND NOT $SEED_ID"
    D0_TABLES="$D0_TABLES $T"
  fi
done
[ -z "$D0_TABLES" ] && echo "  （无雪花 ID 残留）"

echo ""
echo "--- D-0 污染累计值回滚（条件 UPDATE，仅命中已知污染值）---"
# 逐条：先报当前值，再按「当前值=污染值」条件更新，保证幂等且不误伤干净行
rollback() {
  local label="$1" sql_sel="$2" sql_upd="$3" expect="$4"
  local cur
  cur=$(Q "$sql_sel")
  if [ "$cur" = "$expect" ]; then
    if [ "$MODE" = "execute" ]; then
      Q "$sql_upd"
      local after; after=$(Q "$sql_sel")
      printf "  %-46s %s -> %s\n" "$label" "$cur" "$after"
    else
      printf "  %-46s %s -> 待回滚\n" "$label" "$cur"
    fi
  else
    printf "  %-46s 当前 %s（非污染值 %s）跳过\n" "$label" "${cur:-NULL}" "$expect"
  fi
}
rollback "biz_purchase_contract 91501.cumulative_paid" \
  "SELECT cumulative_paid FROM biz_purchase_contract WHERE id=91501;" \
  "UPDATE biz_purchase_contract SET cumulative_paid=6000000.00 WHERE id=91501 AND cumulative_paid=6000035.00;" \
  "6000035.00"
rollback "biz_purchase_contract 91501.cumulative_settlement" \
  "SELECT cumulative_settlement FROM biz_purchase_contract WHERE id=91501;" \
  "UPDATE biz_purchase_contract SET cumulative_settlement=7000000.00 WHERE id=91501 AND cumulative_settlement=7000001.00;" \
  "7000001.00"
rollback "biz_subcontract 91801.cumulative_settlement" \
  "SELECT cumulative_settlement FROM biz_subcontract WHERE id=91801;" \
  "UPDATE biz_subcontract SET cumulative_settlement=3000000.00 WHERE id=91801 AND cumulative_settlement=3000250.00;" \
  "3000250.00"
rollback "biz_project 90001.total_expense" \
  "SELECT total_expense FROM biz_project WHERE id=90001;" \
  "UPDATE biz_project SET total_expense=15000000.00 WHERE id=90001 AND total_expense=15000035.00;" \
  "15000035.00"

# ============================================================
# D-1a：孤儿子表（引用 deleted=1 项目 / 引用物理不存在项目）
# ============================================================
echo ""
echo "=== D-1a. 孤儿子表（引用已逻辑删除的项目）==="
for T in $TABLES; do
  run_delete "$T" "$T" \
    "project_id IN (SELECT id FROM biz_project WHERE deleted=1)"
done

echo ""
echo "=== D-1b. 孤儿子表（引用物理不存在的项目）==="
for T in $TABLES; do
  run_delete "$T" "$T" \
    "project_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM biz_project p WHERE p.id=\`$T\`.project_id)"
done

# ============================================================
# D-1c：存活测试标记项目及其全部子表
# ============================================================
echo ""
echo "=== D-1c. 存活测试标记项目 ==="
TEST_CNT=$(Q "SELECT COUNT(*) FROM biz_project WHERE deleted=0 AND ($TEST_PRED_EXTENDED);")
echo "  命中项目数: ${TEST_CNT:-0}"
echo "  --- 命中项目明细（删除前留档）---"
QT "SELECT id, project_code, project_name, status, created_at
    FROM biz_project WHERE deleted=0 AND ($TEST_PRED_EXTENDED) ORDER BY created_at;"

echo ""
echo "  --- 清理其子表 ---"
for T in $TABLES; do
  run_delete "$T" "$T" \
    "project_id IN (SELECT id FROM biz_project WHERE deleted=0 AND ($TEST_PRED_EXTENDED))"
done

echo ""
echo "  --- 删除项目主表行 ---"
run_delete "biz_project(测试标记)" "biz_project" "deleted=0 AND ($TEST_PRED_EXTENDED)"

# ============================================================
# D-2：询价家族的测试残留（无 project_id，不随项目级联）
# ============================================================
# 审计 4.2 以 INFO 级报告「E2E_TEST_ 询价残留(租户1)」，因其不挂在项目下，
# D-1 的项目级联清不到。属于用户需求③「深度核对并删除垃圾数据」范围。
# 询价家族 6 表（见 00_schema.sql L1041-1156）：
#   biz_inquiry（主）
#     ├─ biz_inquiry_supplier / biz_inquiry_item / biz_bid_result  ← inquiry_id
#     └─ biz_quotation ← inquiry_id
#          └─ biz_quotation_detail ← quotation_id
# 删除顺序必须自叶向根：先 quotation_detail（否则删了 quotation 就丢失映射）。
echo ""
echo "=== D-2. 询价家族的测试残留 ==="
INQ_PRED="tenant_id=1 AND deleted=0 AND (title LIKE 'E2E_TEST_%' OR title REGEXP '_1[0-9]{12}')"

# 安全不变量③：绝不可命中演示种子询价 99101（钢材询价，带完整报价/定标链）
INQ_SEED_HIT=$(Q "SELECT COUNT(*) FROM biz_inquiry WHERE id=99101 AND ($INQ_PRED);")
if [ "${INQ_SEED_HIT:-1}" != "0" ]; then
  echo "FATAL: 询价判定式误命中种子询价 99101，已中止（未写库）。" >&2
  exit 4
fi
echo "  ✓ 不变量③：判定式未命中种子询价 99101"
echo "  命中询价单:"
QT "SELECT id, title, status, created_at FROM biz_inquiry WHERE $INQ_PRED ORDER BY id;"

run_delete "biz_quotation_detail" "biz_quotation_detail" \
  "quotation_id IN (SELECT id FROM biz_quotation WHERE inquiry_id IN (SELECT id FROM biz_inquiry WHERE $INQ_PRED))"
run_delete "biz_quotation" "biz_quotation" \
  "inquiry_id IN (SELECT id FROM biz_inquiry WHERE $INQ_PRED)"
run_delete "biz_bid_result" "biz_bid_result" \
  "inquiry_id IN (SELECT id FROM biz_inquiry WHERE $INQ_PRED)"
run_delete "biz_inquiry_supplier" "biz_inquiry_supplier" \
  "inquiry_id IN (SELECT id FROM biz_inquiry WHERE $INQ_PRED)"
run_delete "biz_inquiry_item" "biz_inquiry_item" \
  "inquiry_id IN (SELECT id FROM biz_inquiry WHERE $INQ_PRED)"
run_delete "biz_inquiry" "biz_inquiry" "$INQ_PRED"

# ============================================================
# 2. 保留清单核对（清理后仍存活的项目，供人工复核未误删）
# ============================================================
echo ""
echo "=== 2. 清理后仍存活的项目清单（人工复核：应只剩演示种子 + 真实业务项目）==="
if [ "$MODE" = "execute" ]; then
  QT "SELECT id, project_code, project_name, status, tenant_id FROM biz_project WHERE deleted=0 ORDER BY id;"
else
  QT "SELECT id, project_code, project_name, status, tenant_id
      FROM biz_project
      WHERE deleted=0 AND NOT ($TEST_PRED_EXTENDED) ORDER BY id;"
fi

# ============================================================
# 3. ACT_RU_TASK 残留：只报告，不删除
# ============================================================
echo ""
echo "=== 3. ACT_RU_TASK 残留（只报告，不在本脚本内删除）==="
TASK_CNT=$(Q "SELECT COUNT(*) FROM ACT_RU_TASK;")
echo "  当前运行中任务数: ${TASK_CNT:-0}"
echo "  不在本脚本内删除的理由："
echo "    Flowable 的 39 张 ACT_ 表之间存在外键与引擎内部状态机耦合，"
echo "    直接 DELETE 行会让流程引擎缓存与库状态不一致（下次审批即报错）。"
echo "    正确做法是经应用层 runtimeService.deleteProcessInstance(procInstId, reason)"
echo "    逐实例终止，由引擎自行级联清理 ACT_RU_* 与写 ACT_HI_*。"
echo "    该项在审计中为 INFO 级（R5-01），非 FAIL，不阻塞本轮验收。"

# ============================================================
# 4. 汇总
# ============================================================
echo ""
echo "############################################################"
if [ "$MODE" = "execute" ]; then
  echo "# 执行完成：累计删除 $TOTAL_DELETED 行"
  echo "# 备份文件：$BACKUP_FILE"
  echo "# 下一步：bash run-migration.sh 52_V2026_50__seed_reconcile_documents.sql"
  echo "#         然后重跑 keys/audit-data.ps1 验证归零"
else
  echo "# DRY-RUN 完成：预计删除 $TOTAL_DELETED 行（未写库）"
  echo "# 确认无误后执行： bash cleanup-garbage-data.sh --execute"
fi
echo "############################################################"
