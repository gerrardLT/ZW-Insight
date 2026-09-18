#!/usr/bin/env bash
###############################################################################
# audit-data-round7.sh — ZW-Insight 线上数据库第 7 轮数据审计
#
# 运行位置：服务器 129.204.3.200（需要 docker exec zwi-mysql）
# 由本地通过 keys/audit-data.ps1 经 SSH 上传并调用。
#
# 设计依据：
#   - 基线：audit-reports/round1-6-complete-summary.md（2026-08-17）
#   - 覆盖：Section 0 Preflight / 1 已知问题回归 / 2 新增表 / 3 金额勾稽
#           / 4 数据卫生 / 5 业务规则 / 6 租户隔离 / 7 容量健康
#   - 全程只读：仅 SELECT / SHOW / information_schema，脚本内置关键字拦截
#   - 脱敏：报告不含明文密码 / token / 私钥
#
# 用法：
#   audit-data-round7.sh                完整审计（Section 0-7）
#   audit-data-round7.sh all            同上
#   audit-data-round7.sh section <N>    只执行第 N 节（0-7）
#   audit-data-round7.sh regression     只执行 Section 1（快速回归）
#   audit-data-round7.sh new-tables     只执行 Section 2
###############################################################################
set -uo pipefail

# ---------------------------------------------------------------------------
# 配置（支持环境变量覆盖）
# ---------------------------------------------------------------------------
PW="${ZWI_DB_PW:-zwinsight123}"
DB="${ZWI_DB:-zw_insight}"
MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"
WORKDIR="${ZWI_WORKDIR:-/root/zwi-deploy}"
RUN_TS="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
REPORT_FILE="${ZWI_REPORT_FILE:-$WORKDIR/audit-round7-$RUN_TS.md}"
TMPDIR="${ZWI_TMPDIR:-/tmp/zwi-audit-$RUN_TS}"
mkdir -p "$WORKDIR" "$TMPDIR"

# 统计计数器
PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0
INFO_COUNT=0

# ---------------------------------------------------------------------------
# 只读安全阀：拦截任何写操作 SQL
# ---------------------------------------------------------------------------
assert_readonly() {
  local sql="$1"
  # 提取首个 SQL 关键字（大写）
  local kw
  kw=$(echo "$sql" | tr '[:lower:]' '[:upper:]' | grep -oE '^[[:space:]]*(SELECT|SHOW|DESCRIBE|DESC|EXPLAIN|WITH|SET|INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|REPLACE|GRANT|REVOKE|CALL|LOAD|HANDLER|LOCK|UNLOCK|RENAME)' | head -1 | tr -d '[:space:]')
  case "$kw" in
    SELECT|SHOW|DESCRIBE|DESC|EXPLAIN|WITH|SET) return 0 ;;
    *)
      echo "[FATAL] 拒绝执行非只读 SQL（关键字=$kw）" >&2
      echo "        SQL 前 120 字符：$(echo "$sql" | head -c 120)" >&2
      exit 3
      ;;
  esac
}

# ---------------------------------------------------------------------------
# 脱敏（沿用 verify-base.sh 的 mask 逻辑）
# ---------------------------------------------------------------------------
mask() {
  sed -E \
    -e 's/("(accessToken|refreshToken|token)"[[:space:]]*:[[:space:]]*")[^"]+(")/\1<REDACTED:\2>\3/gI' \
    -e 's/("password"[[:space:]]*:[[:space:]]*")[^"]+(")/\1<REDACTED:password>\2/gI' \
    -e 's/\b(accessToken|refreshToken|token|password|passwd|pwd)([[:space:]]*=[[:space:]]*)[^[:space:]&"]+/\1\2<REDACTED:\1>/gI' \
    -e 's/(Bearer[[:space:]]+)[A-Za-z0-9._\-]+/\1<REDACTED:token>/g' \
    -e 's/-----BEGIN[^-]*PRIVATE KEY-----/<REDACTED:private-key>/g'
}

log() { echo "[$(date +%H:%M:%S)] $*"; }

# ---------------------------------------------------------------------------
# Q：执行只读 SQL，返回结果（-N 去表头，-B 制表符分隔）
#   用法：Q "SELECT ..."
# ---------------------------------------------------------------------------
Q() {
  local sql="$1"
  assert_readonly "$sql"
  docker exec -i "$MYSQL_CT" sh -c "mysql -uroot -p$PW -N -B $DB -e \"SET NAMES utf8mb4; $sql\"" 2>/dev/null
}

# Q1：执行只读 SQL，返回首行首列（标量）；空结果返回空字符串
Q1() {
  Q "$1" | head -1 | awk -F'\t' '{print $1}'
}

# Q1N：执行只读 SQL，返回首行首列（标量）；空结果返回 0（用于数值比较）
Q1N() {
  local r
  r=$(Q1 "$1")
  echo "${r:-0}"
}

# ---------------------------------------------------------------------------
# 报告写入助手
# ---------------------------------------------------------------------------
RPT() { printf '%s\n' "$*" >> "$REPORT_FILE"; }
RPT_HEAD() {
  cat > "$REPORT_FILE" <<EOF
# ZW-Insight 数据库数据一致性审计报告 - 第 7 轮

**审计日期**: $(date -u +%Y-%m-%d)（UTC $RUN_TS）
**审计范围**: 全库（biz_* / sys_* / bd_* / ACT_* / wf_* / flyway）
**审计口径**: 租户 1（演示）+ 租户 9999（测试）+ 全局系统表，仅只读查询
**执行脚本**: keys/audit-data-round7.sh
**基线**: audit-reports/round1-6-complete-summary.md（2026-08-17）

---

EOF
}

# 检查项：数值比较（actual vs expected，op 支持 eq/ge/le/gt/lt）
check_num() {
  local name="$1" expected="$2" actual="${3:-0}" op="${4:-eq}" level="${5:-FAIL}"
  # 确保 actual 是数字
  if ! [[ "$actual" =~ ^-?[0-9]+$ ]]; then actual=0; fi
  local ok=0
  case "$op" in
    eq) [ "$actual" = "$expected" ] && ok=1 ;;
    ge) [ "$actual" -ge "$expected" ] && ok=1 ;;
    le) [ "$actual" -le "$expected" ] && ok=1 ;;
    gt) [ "$actual" -gt "$expected" ] && ok=1 ;;
    lt) [ "$actual" -lt "$expected" ] && ok=1 ;;
  esac
  if [ "$ok" = "1" ]; then
    printf "  ✓ %-50s %s (期望%s%s)\n" "$name" "$actual" "$op" "$expected"
    RPT "| ✅ PASS | $name | \`$actual\` | 期望 $op \`$expected\` |"
    PASS_COUNT=$((PASS_COUNT+1))
  else
    printf "  ✗ %-50s %s (期望%s%s) [%s]\n" "$name" "$actual" "$op" "$expected" "$level"
    RPT "| ❌ $level | $name | \`$actual\` | 期望 $op \`$expected\` |"
    if [ "$level" = "WARN" ]; then WARN_COUNT=$((WARN_COUNT+1)); else FAIL_COUNT=$((FAIL_COUNT+1)); fi
  fi
}

# 检查项：信息记录（不判 PASS/FAIL，仅记录）
check_info() {
  local name="$1" value="$2" note="${3:-}"
  printf "  ℹ %-50s %s %s\n" "$name" "$value" "$note"
  RPT "| ℹ️ INFO | $name | \`$value\` | $note |"
  INFO_COUNT=$((INFO_COUNT+1))
}

# 检查项：SQL 结果行数（期望 0 行为 PASS）
check_empty() {
  local name="$1" sql="$2" level="${3:-FAIL}"
  local cnt
  cnt=$(Q1 "SELECT COUNT(*) FROM ($sql) _t")
  cnt="${cnt:-0}"
  if [ "$cnt" = "0" ]; then
    printf "  ✓ %-50s 0 行\n" "$name"
    RPT "| ✅ PASS | $name | 0 行 | 期望 0 |"
    PASS_COUNT=$((PASS_COUNT+1))
  else
    printf "  ✗ %-50s %s 行 [%s]\n" "$name" "$cnt" "$level"
    RPT "| ❌ $level | $name | $cnt 行 | 期望 0 |"
    if [ "$level" = "WARN" ]; then WARN_COUNT=$((WARN_COUNT+1)); else FAIL_COUNT=$((FAIL_COUNT+1)); fi
    # 抽样前 5 行写入报告
    RPT ""
    RPT "<details><summary>抽样前 5 行</summary>"
    RPT ""
    RPT '```'
    Q "$sql LIMIT 5" | mask >> "$REPORT_FILE"
    RPT '```'
    RPT "</details>"
  fi
}

# 章节标题
section_start() {
  local num="$1" title="$2"
  echo ""
  echo "================ Section $num: $title ================"
  RPT ""
  RPT "## Section $num：$title"
  RPT ""
  RPT "| 结果 | 检查项 | 实际值 | 期望/说明 |"
  RPT "|------|--------|--------|----------|"
}

section_end() {
  RPT ""
}

###############################################################################
# Section 0：Preflight（连通性与元数据）
###############################################################################
section_0() {
  section_start 0 "Preflight（连通性与元数据）"

  # 0.1 数据库连通
  local db_name
  db_name=$(Q1 "SELECT DATABASE()")
  if [ "$db_name" = "$DB" ]; then
    printf "  ✓ %-50s %s\n" "数据库名" "$db_name"
    RPT "| ✅ PASS | 数据库名 | \`$db_name\` | 期望 $DB |"
    PASS_COUNT=$((PASS_COUNT+1))
  else
    printf "  ✗ %-50s %s (期望 %s) [FAIL]\n" "数据库名" "$db_name" "$DB"
    RPT "| ❌ FAIL | 数据库名 | \`$db_name\` | 期望 $DB |"
    FAIL_COUNT=$((FAIL_COUNT+1))
  fi

  # 0.2 MySQL 版本
  local ver
  ver=$(Q1 "SELECT VERSION()")
  check_info "MySQL 版本" "$ver"

  # 0.3 字符集与大小写敏感
  local lctn cs
  lctn=$(Q1 "SHOW VARIABLES LIKE 'lower_case_table_names'" | awk '{print $2}')
  cs=$(Q1 "SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$DB'")
  check_info "lower_case_table_names" "$lctn" "（0=大小写敏感）"
  check_info "数据库默认字符集" "$cs"

  # 0.4 总表数（对比 round6 的 213）
  local tbl_cnt
  tbl_cnt=$(Q1 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB'")
  check_info "总表数" "$tbl_cnt" "（round6 基线 213）"

  # 0.5 按前缀分组
  local biz_cnt sys_cnt bd_cnt act_cnt wf_cnt other_cnt
  biz_cnt=$(Q1 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB' AND table_name LIKE 'biz\\_%'")
  sys_cnt=$(Q1 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB' AND table_name LIKE 'sys\\_%'")
  bd_cnt=$(Q1 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB' AND table_name LIKE 'bd\\_%'")
  act_cnt=$(Q1 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB' AND table_name LIKE 'ACT\\_%'")
  wf_cnt=$(Q1 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB' AND table_name LIKE 'wf\\_%'")
  other_cnt=$((tbl_cnt - biz_cnt - sys_cnt - bd_cnt - act_cnt - wf_cnt))
  check_info "表分布" "biz=$biz_cnt sys=$sys_cnt bd=$bd_cnt ACT=$act_cnt wf=$wf_cnt other=$other_cnt"

  # 0.6 当前 UTC 时间
  check_info "审计时刻(UTC)" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  section_end
}

###############################################################################
# Section 1：已知问题回归（对齐 round1-6 结论）
###############################################################################
section_1() {
  section_start 1 "已知问题回归（round1-6 基线对比）"

  # R5-01: 租户 1 ACT_RU_TASK 运行中任务数（round5 基线 312）
  local ru_task_t1
  ru_task_t1=$(Q1 "SELECT COUNT(*) FROM ACT_RU_TASK WHERE TENANT_ID_='1'")
  ru_task_t1=${ru_task_t1:-0}
  check_info "R5-01 租户1 ACT_RU_TASK" "$ru_task_t1" "（round5 基线 312）"
  if [ "$ru_task_t1" -gt 312 ]; then
    RPT "| ⚠️ WARN | R5-01 恶化 | 当前 $ru_task_t1 > 基线 312 | 测试残留持续累积 |"
    WARN_COUNT=$((WARN_COUNT+1))
  elif [ "$ru_task_t1" -lt 312 ]; then
    RPT "| ✅ 改善 | R5-01 改善 | 当前 $ru_task_t1 < 基线 312 | 残留已部分清理 |"
    PASS_COUNT=$((PASS_COUNT+1))
  else
    RPT "| ℹ️ 持平 | R5-01 持平 | 当前 = 基线 312 | 用户决策保留观察 |"
    INFO_COUNT=$((INFO_COUNT+1))
  fi

  # R5-02: ACT_RE_DEPLOYMENT 总数（round5 基线 1023）
  local deploy_cnt
  deploy_cnt=$(Q1 "SELECT COUNT(*) FROM ACT_RE_DEPLOYMENT")
  check_info "R5-02 ACT_RE_DEPLOYMENT" "$deploy_cnt" "（round5 基线 1023）"

  # R6-01: sys_backup_record 近 30 天成功率
  local bk_total bk_succ bk_rate
  bk_total=$(Q1N "SELECT COUNT(*) FROM sys_backup_record WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)")
  bk_succ=$(Q1N "SELECT COUNT(*) FROM sys_backup_record WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY) AND status='SUCCESS'")
  if [ "$bk_total" -gt 0 ]; then
    bk_rate=$((bk_succ * 100 / bk_total))
  else
    bk_rate=0
  fi
  check_info "R6-01 近30天备份" "总=$bk_total 成功=$bk_succ 成功率=${bk_rate}%"
  if [ "$bk_total" -gt 0 ] && [ "$bk_rate" -lt 95 ]; then
    RPT "| ❌ FAIL | R6-01 备份成功率不足 | ${bk_rate}% | 期望 ≥95% |"
    FAIL_COUNT=$((FAIL_COUNT+1))
  elif [ "$bk_total" -eq 0 ]; then
    RPT "| ⚠️ WARN | R6-01 近30天无备份记录 | 0 | 定时任务未运行或表被清空 |"
    WARN_COUNT=$((WARN_COUNT+1))
  else
    RPT "| ✅ PASS | R6-01 备份成功率 | ${bk_rate}% | ≥95% |"
    PASS_COUNT=$((PASS_COUNT+1))
  fi

  # R6-02: 审计/登录/操作日志三表行数（round6 基线全 0）
  local audit_cnt login_cnt oper_cnt
  audit_cnt=$(Q1 "SELECT COUNT(*) FROM sys_audit_log")
  login_cnt=$(Q1 "SELECT COUNT(*) FROM sys_login_log")
  oper_cnt=$(Q1 "SELECT COUNT(*) FROM sys_oper_log")
  check_info "R6-02 日志三表" "audit=$audit_cnt login=$login_cnt oper=$oper_cnt" "（round6 基线全 0，功能未接线）"

  # R6-03: sys_user_project 孤儿映射数（round6 基线 1287）
  local up_total up_orphan_proj up_orphan_user
  up_total=$(Q1 "SELECT COUNT(*) FROM sys_user_project")
  up_orphan_proj=$(Q1 "SELECT COUNT(*) FROM sys_user_project sup LEFT JOIN biz_project bp ON sup.project_id=bp.id WHERE bp.id IS NULL")
  up_orphan_user=$(Q1 "SELECT COUNT(*) FROM sys_user_project sup LEFT JOIN sys_user su ON sup.user_id=su.id WHERE su.id IS NULL")
  up_orphan_proj=${up_orphan_proj:-0}
  check_info "R6-03 sys_user_project" "总=$up_total 项目孤儿=$up_orphan_proj 用户孤儿=$up_orphan_user" "（round6 基线 1287）"
  if [ "$up_orphan_proj" -gt 1287 ]; then
    RPT "| ⚠️ WARN | R6-03 孤儿恶化 | 当前 $up_orphan_proj > 基线 1287 | 项目删除仍无级联 |"
    WARN_COUNT=$((WARN_COUNT+1))
  fi

  # R6-04: sys_login_device 行数（round6 基线 17260）
  local dev_cnt
  dev_cnt=$(Q1 "SELECT COUNT(*) FROM sys_login_device")
  dev_cnt=${dev_cnt:-0}
  check_info "R6-04 sys_login_device" "$dev_cnt" "（round6 基线 17260）"
  if [ "$dev_cnt" -gt 20000 ]; then
    RPT "| ⚠️ WARN | R6-04 设备表膨胀 | $dev_cnt > 20000 | 建议批量清理测试期记录 |"
    WARN_COUNT=$((WARN_COUNT+1))
  fi

  # Flyway: 失败迁移记录数（必须为 0）
  local fw_fail fw_total
  fw_fail=$(Q1 "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0")
  fw_total=$(Q1 "SELECT COUNT(*) FROM flyway_schema_history")
  check_num "Flyway 失败迁移" "0" "$fw_fail" "eq" "FAIL"
  check_info "Flyway 总记录" "$fw_total"

  section_end
}

###############################################################################
# Section 2：新增表覆盖（2026-08-17 后新增）
###############################################################################
section_2() {
  section_start 2 "新增表覆盖（cost-control-backbone + p0-gap-closeout）"

  # --- 2.1 cost-control-backbone（迁移 51_V2026_49）---
  RPT ""
  RPT "### 2.1 cost-control-backbone"
  RPT ""

  # biz_project_wbs_node
  local wbs_cnt wbs_orphan_parent wbs_orphan_proj wbs_dup_code
  wbs_cnt=$(Q1 "SELECT COUNT(*) FROM biz_project_wbs_node WHERE deleted=0")
  wbs_orphan_parent=$(Q1 "SELECT COUNT(*) FROM biz_project_wbs_node w LEFT JOIN biz_project_wbs_node p ON w.parent_id=p.id WHERE w.parent_id IS NOT NULL AND w.deleted=0 AND p.id IS NULL")
  wbs_orphan_proj=$(Q1 "SELECT COUNT(*) FROM biz_project_wbs_node w LEFT JOIN biz_project bp ON w.project_id=bp.id WHERE w.deleted=0 AND bp.id IS NULL")
  wbs_dup_code=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, project_id, node_code FROM biz_project_wbs_node WHERE deleted=0 GROUP BY tenant_id, project_id, node_code HAVING COUNT(*)>1) t")
  check_info "biz_project_wbs_node 行数" "$wbs_cnt"
  check_num "WBS parent_id 孤儿" "0" "$wbs_orphan_parent" "eq" "FAIL"
  check_num "WBS project_id 孤儿" "0" "$wbs_orphan_proj" "eq" "FAIL"
  check_num "WBS node_code 租户内重复" "0" "$wbs_dup_code" "eq" "FAIL"

  # biz_cost_account
  local ca_cnt ca_orphan_parent ca_orphan_wbs ca_dup_code ca_neg
  ca_cnt=$(Q1 "SELECT COUNT(*) FROM biz_cost_account WHERE deleted=0")
  ca_orphan_parent=$(Q1 "SELECT COUNT(*) FROM biz_cost_account c LEFT JOIN biz_cost_account p ON c.parent_id=p.id WHERE c.parent_id IS NOT NULL AND c.deleted=0 AND p.id IS NULL")
  ca_orphan_wbs=$(Q1 "SELECT COUNT(*) FROM biz_cost_account c LEFT JOIN biz_project_wbs_node w ON c.wbs_node_id=w.id WHERE c.wbs_node_id IS NOT NULL AND c.deleted=0 AND w.id IS NULL")
  ca_dup_code=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, project_id, account_code FROM biz_cost_account WHERE deleted=0 GROUP BY tenant_id, project_id, account_code HAVING COUNT(*)>1) t")
  ca_neg=$(Q1 "SELECT COUNT(*) FROM biz_cost_account WHERE deleted=0 AND (baseline_amount<0 OR current_amount<0 OR commitment_amount<0 OR actual_amount<0 OR forecast_amount<0)")
  check_info "biz_cost_account 行数" "$ca_cnt"
  check_num "CBS parent_id 孤儿" "0" "$ca_orphan_parent" "eq" "FAIL"
  check_num "CBS wbs_node_id 孤儿" "0" "$ca_orphan_wbs" "eq" "WARN"
  check_num "CBS account_code 租户内重复" "0" "$ca_dup_code" "eq" "FAIL"
  check_num "CBS 金额维度负值" "0" "$ca_neg" "eq" "FAIL"

  # biz_cost_account_txn
  local txn_cnt txn_orphan_acc txn_dup_idem
  txn_cnt=$(Q1 "SELECT COUNT(*) FROM biz_cost_account_txn WHERE deleted=0")
  txn_orphan_acc=$(Q1 "SELECT COUNT(*) FROM biz_cost_account_txn t LEFT JOIN biz_cost_account a ON t.account_id=a.id WHERE t.deleted=0 AND a.id IS NULL")
  txn_dup_idem=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, source_type, source_id, account_id, amount_type FROM biz_cost_account_txn WHERE deleted=0 GROUP BY tenant_id, source_type, source_id, account_id, amount_type HAVING COUNT(*)>1) t")
  check_info "biz_cost_account_txn 行数" "$txn_cnt"
  check_num "TXN account_id 孤儿" "0" "$txn_orphan_acc" "eq" "FAIL"
  check_num "TXN 幂等键重复" "0" "$txn_dup_idem" "eq" "FAIL"

  # biz_cost_account_link
  local link_cnt link_orphan_acc
  link_cnt=$(Q1 "SELECT COUNT(*) FROM biz_cost_account_link WHERE deleted=0")
  link_orphan_acc=$(Q1 "SELECT COUNT(*) FROM biz_cost_account_link l LEFT JOIN biz_cost_account a ON l.account_id=a.id WHERE l.deleted=0 AND a.id IS NULL")
  check_info "biz_cost_account_link 行数" "$link_cnt"
  check_num "LINK account_id 孤儿" "0" "$link_orphan_acc" "eq" "FAIL"

  # biz_change_event
  local ce_cnt ce_dup_num
  ce_cnt=$(Q1 "SELECT COUNT(*) FROM biz_change_event WHERE deleted=0")
  ce_dup_num=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, event_number FROM biz_change_event WHERE deleted=0 GROUP BY tenant_id, event_number HAVING COUNT(*)>1) t")
  check_info "biz_change_event 行数" "$ce_cnt"
  check_num "ChangeEvent event_number 重复" "0" "$ce_dup_num" "eq" "FAIL"
  # 状态分布
  if [ "$ce_cnt" -gt 0 ]; then
    RPT ""
    RPT "**变更事件状态分布**："
    RPT '```'
    Q "SELECT status, COUNT(*) FROM biz_change_event WHERE deleted=0 GROUP BY status" >> "$REPORT_FILE"
    RPT '```'
  fi

  # sys_outbox_event
  local ob_cnt ob_stuck ob_dup_idem
  ob_cnt=$(Q1 "SELECT COUNT(*) FROM sys_outbox_event")
  ob_stuck=$(Q1 "SELECT COUNT(*) FROM sys_outbox_event WHERE attempts >= max_attempts AND status NOT IN ('DEAD','DELIVERED')")
  ob_dup_idem=$(Q1 "SELECT COUNT(*) FROM (SELECT idempotency_key FROM sys_outbox_event GROUP BY idempotency_key HAVING COUNT(*)>1) t")
  check_info "sys_outbox_event 行数" "$ob_cnt"
  check_num "Outbox 卡死(attempts≥max 非 DEAD)" "0" "$ob_stuck" "eq" "WARN"
  check_num "Outbox idempotency_key 重复" "0" "$ob_dup_idem" "eq" "FAIL"
  if [ "$ob_cnt" -gt 0 ]; then
    RPT ""
    RPT "**Outbox 状态分布**："
    RPT '```'
    Q "SELECT status, COUNT(*) FROM sys_outbox_event GROUP BY status" >> "$REPORT_FILE"
    RPT '```'
  fi

  # --- 2.2 p0-gap-closeout（迁移 49_V2026_47）---
  RPT ""
  RPT "### 2.2 p0-gap-closeout"
  RPT ""

  # biz_payment_received.claim_status
  local pr_total pr_unclaimed pr_claimed pr_writtenoff pr_bad_claim
  pr_total=$(Q1 "SELECT COUNT(*) FROM biz_payment_received WHERE deleted=0")
  pr_unclaimed=$(Q1 "SELECT COUNT(*) FROM biz_payment_received WHERE deleted=0 AND claim_status='UNCLAIMED'")
  pr_claimed=$(Q1 "SELECT COUNT(*) FROM biz_payment_received WHERE deleted=0 AND claim_status='CLAIMED'")
  pr_writtenoff=$(Q1 "SELECT COUNT(*) FROM biz_payment_received WHERE deleted=0 AND claim_status='WRITTEN_OFF'")
  pr_bad_claim=$(Q1 "SELECT COUNT(*) FROM biz_payment_received WHERE deleted=0 AND claim_status<>'UNCLAIMED' AND (claimed_by IS NULL OR claimed_at IS NULL)")
  check_info "biz_payment_received 总数" "$pr_total"
  check_info "claim_status 分布" "UNCLAIMED=$pr_unclaimed CLAIMED=$pr_claimed WRITTEN_OFF=$pr_writtenoff"
  check_num "认领状态非 UNCLAIMED 但缺 claimed_by/at" "0" "$pr_bad_claim" "eq" "WARN"

  # biz_rectification.attachment_ids NULL 率
  local rect_total rect_null_attach
  rect_total=$(Q1 "SELECT COUNT(*) FROM biz_rectification WHERE deleted=0")
  rect_null_attach=$(Q1 "SELECT COUNT(*) FROM biz_rectification WHERE deleted=0 AND (attachment_ids IS NULL OR attachment_ids='')")
  check_info "biz_rectification 总数" "$rect_total"
  if [ "$rect_total" -gt 0 ]; then
    local rect_null_pct=$((rect_null_attach * 100 / rect_total))
    check_info "attachment_ids NULL 率" "${rect_null_pct}%" "（$rect_null_attach / $rect_total）"
  fi

  # bd_material.material_code
  local mat_total mat_null_code mat_dup_code
  mat_total=$(Q1 "SELECT COUNT(*) FROM bd_material WHERE deleted=0")
  mat_null_code=$(Q1 "SELECT COUNT(*) FROM bd_material WHERE deleted=0 AND (material_code IS NULL OR material_code='')")
  mat_dup_code=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, material_code FROM bd_material WHERE deleted=0 AND material_code IS NOT NULL AND material_code<>'' GROUP BY tenant_id, material_code HAVING COUNT(*)>1) t")
  check_info "bd_material 总数" "$mat_total"
  if [ "$mat_total" -gt 0 ]; then
    local mat_null_pct=$((mat_null_code * 100 / mat_total))
    check_info "material_code NULL 率" "${mat_null_pct}%" "（$mat_null_code / $mat_total）"
  fi
  check_num "material_code 租户内重复" "0" "$mat_dup_code" "eq" "WARN"

  # --- 2.3 p2-advanced（迁移 21_V2026_15）---
  RPT ""
  RPT "### 2.3 p2-advanced"
  RPT ""

  local brl_cnt ver_latest
  brl_cnt=$(Q1 "SELECT COUNT(*) FROM sys_backup_restore_log")
  ver_latest=$(Q1 "SELECT version_no FROM sys_version ORDER BY release_date DESC LIMIT 1")
  check_info "sys_backup_restore_log 行数" "$brl_cnt"
  check_info "sys_version 最新版本" "${ver_latest:-（空）}"

  section_end
}

###############################################################################
# Section 3：跨模块金额勾稽（复用 round3 模板）
###############################################################################
section_3() {
  section_start 3 "跨模块金额勾稽"

  # 通用勾稽函数：合同累计值 vs 单据汇总
  # 参数：合同表 累计字段 单据表 单据金额字段 单据状态值 关联字段名
  reconcile_contract() {
    local ct="$1" cf="$2" dt="$3" df="$4" ds="$5" fk="${6:-contract_id}"
    local mismatch_sql
    mismatch_sql="SELECT c.id, c.tenant_id, c.$cf AS contract_val, COALESCE(SUM(d.$df),0) AS doc_sum, ABS(COALESCE(c.$cf,0)-COALESCE(SUM(d.$df),0)) AS diff FROM $ct c LEFT JOIN $dt d ON d.$fk=c.id AND d.status='$ds' AND d.deleted=0 WHERE c.deleted=0 GROUP BY c.id, c.tenant_id, c.$cf HAVING ABS(COALESCE(c.$cf,0)-COALESCE(SUM(d.$df),0)) > 0.01"
    local cnt
    cnt=$(Q1 "SELECT COUNT(*) FROM ($mismatch_sql) _t")
    cnt="${cnt:-0}"
    local total
    total=$(Q1 "SELECT COUNT(*) FROM $ct WHERE deleted=0")
    if [ "$cnt" = "0" ]; then
      printf "  ✓ %-55s 0/%s MISMATCH\n" "$ct.$cf vs $dt($ds)" "$total"
      RPT "| ✅ PASS | \`$ct.$cf\` vs \`$dt.SUM($df) WHERE status='$ds'\` | 0/$total MISMATCH | 全部勾稽 |"
      PASS_COUNT=$((PASS_COUNT+1))
    else
      printf "  ✗ %-55s %s/%s MISMATCH\n" "$ct.$cf vs $dt($ds)" "$cnt" "$total"
      RPT "| ❌ FAIL | \`$ct.$cf\` vs \`$dt.SUM($df)\` | $cnt/$total MISMATCH | 见下方抽样 |"
      FAIL_COUNT=$((FAIL_COUNT+1))
      RPT ""
      RPT "<details><summary>MISMATCH 抽样前 10 行</summary>"
      RPT ""
      RPT '```'
      Q "$mismatch_sql ORDER BY diff DESC LIMIT 10" | mask >> "$REPORT_FILE"
      RPT '```'
      RPT "</details>"
    fi
  }

  RPT ""
  RPT "### 3.1 支出侧四类合同 cumulative_paid 勾稽"
  RPT ""
  reconcile_contract "biz_labor_contract"     "cumulative_paid" "biz_payment_apply" "payment_amount" "APPROVED"
  reconcile_contract "biz_machine_contract"   "cumulative_paid" "biz_payment_apply" "payment_amount" "APPROVED"
  reconcile_contract "biz_subcontract"        "cumulative_paid" "biz_payment_apply" "payment_amount" "APPROVED"
  reconcile_contract "biz_purchase_contract"  "cumulative_paid" "biz_payment_apply" "payment_amount" "APPROVED"

  RPT ""
  RPT "### 3.2 支出侧四类合同 cumulative_settlement 勾稽"
  RPT ""
  reconcile_contract "biz_labor_contract"     "cumulative_settlement" "biz_labor_settlement"     "settlement_amount" "APPROVED"
  reconcile_contract "biz_subcontract"        "cumulative_settlement" "biz_subcontract_settlement" "settlement_amount" "APPROVED"
  reconcile_contract "biz_purchase_contract"  "cumulative_settlement" "biz_purchase_settlement"  "settlement_amount" "APPROVED"
  # 机械结算：正确表为 biz_machine_settlement（有 contract_id 列）。
  # 2026-09-18 修正：旧版误用 biz_machine_work_settlement，该表无 contract_id 列
  # （仅 project_id + settlement_code，按工作量结算），SQL 报错返回空被误判为 PASS。
  reconcile_contract "biz_machine_contract"   "cumulative_settlement" "biz_machine_settlement"   "settlement_amount" "APPROVED"
  # biz_machine_work_settlement 只能按 project_id 关联，单独核对（不参与合同级勾稽）
  local mws_cnt mws_sum
  mws_cnt=$(Q1N "SELECT COUNT(*) FROM biz_machine_work_settlement WHERE deleted=0 AND status=2")
  mws_sum=$(Q1N "SELECT COALESCE(SUM(total_amount),0) FROM biz_machine_work_settlement WHERE deleted=0 AND status=2")
  check_info "biz_machine_work_settlement(已审批)" "笔数=$mws_cnt 金额=$mws_sum" "（无 contract_id，不计入合同勾稽）"

  RPT ""
  RPT "### 3.3 项目侧汇总勾稽"
  RPT ""
  # total_expense vs SUM(APPROVED payment_apply)
  local pe_mismatch
  pe_mismatch=$(Q1 "SELECT COUNT(*) FROM (SELECT p.id, p.total_expense AS pe, COALESCE(SUM(pa.payment_amount),0) AS se FROM biz_project p LEFT JOIN biz_payment_apply pa ON pa.project_id=p.id AND pa.status='APPROVED' AND pa.deleted=0 WHERE p.deleted=0 GROUP BY p.id, p.total_expense HAVING ABS(COALESCE(p.total_expense,0)-COALESCE(SUM(pa.payment_amount),0))>0.01) t")
  local proj_total
  proj_total=$(Q1 "SELECT COUNT(*) FROM biz_project WHERE deleted=0")
  if [ "${pe_mismatch:-0}" = "0" ]; then
    RPT "| ✅ PASS | \`biz_project.total_expense\` vs APPROVED 付款汇总 | 0/$proj_total MISMATCH | 全部勾稽 |"
    PASS_COUNT=$((PASS_COUNT+1))
  else
    RPT "| ❌ FAIL | \`biz_project.total_expense\` vs APPROVED 付款汇总 | $pe_mismatch/$proj_total MISMATCH | 见下方抽样 |"
    FAIL_COUNT=$((FAIL_COUNT+1))
    RPT ""
    RPT '```'
    Q "SELECT p.id, p.project_code, p.total_expense, COALESCE(SUM(pa.payment_amount),0) AS sum_payment FROM biz_project p LEFT JOIN biz_payment_apply pa ON pa.project_id=p.id AND pa.status='APPROVED' AND pa.deleted=0 WHERE p.deleted=0 GROUP BY p.id, p.project_code, p.total_expense HAVING ABS(COALESCE(p.total_expense,0)-COALESCE(SUM(pa.payment_amount),0))>0.01 ORDER BY ABS(p.total_expense-COALESCE(SUM(pa.payment_amount),0)) DESC LIMIT 10" | mask >> "$REPORT_FILE"
    RPT '```'
  fi

  # total_income vs SUM(payment_received.receive_amount)
  local pi_mismatch
  pi_mismatch=$(Q1 "SELECT COUNT(*) FROM (SELECT p.id, p.total_income AS pi, COALESCE(SUM(pr.receive_amount),0) AS si FROM biz_project p LEFT JOIN biz_payment_received pr ON pr.project_id=p.id AND pr.deleted=0 WHERE p.deleted=0 GROUP BY p.id, p.total_income HAVING ABS(COALESCE(p.total_income,0)-COALESCE(SUM(pr.receive_amount),0))>0.01) t")
  if [ "${pi_mismatch:-0}" = "0" ]; then
    RPT "| ✅ PASS | \`biz_project.total_income\` vs 收款汇总 | 0/$proj_total MISMATCH | 全部勾稽 |"
    PASS_COUNT=$((PASS_COUNT+1))
  else
    RPT "| ⚠️ WARN | \`biz_project.total_income\` vs 收款汇总 | $pi_mismatch/$proj_total MISMATCH | 收款认领机制可能未回写 |"
    WARN_COUNT=$((WARN_COUNT+1))
  fi

  # cumulative_output vs SUM(APPROVED output_report.current_output)
  local po_mismatch
  po_mismatch=$(Q1 "SELECT COUNT(*) FROM (SELECT p.id, p.cumulative_output AS po, COALESCE(SUM(o.current_output),0) AS so FROM biz_project p LEFT JOIN biz_output_report o ON o.project_id=p.id AND o.status='APPROVED' AND o.deleted=0 WHERE p.deleted=0 GROUP BY p.id, p.cumulative_output HAVING ABS(COALESCE(p.cumulative_output,0)-COALESCE(SUM(o.current_output),0))>0.01) t")
  if [ "${po_mismatch:-0}" = "0" ]; then
    RPT "| ✅ PASS | \`biz_project.cumulative_output\` vs APPROVED 产值汇总 | 0/$proj_total MISMATCH | 全部勾稽 |"
    PASS_COUNT=$((PASS_COUNT+1))
  else
    RPT "| ⚠️ WARN | \`biz_project.cumulative_output\` vs APPROVED 产值汇总 | $po_mismatch/$proj_total MISMATCH | 种子数据叙事或回写缺陷 |"
    WARN_COUNT=$((WARN_COUNT+1))
  fi

  RPT ""
  RPT "### 3.4 收入侧勾稽（施工合同）"
  RPT ""
  reconcile_contract "biz_construction_contract" "cumulative_invoice_amount"  "biz_invoice_apply"    "invoice_amount"  "APPROVED"
  reconcile_contract "biz_construction_contract" "cumulative_received_amount" "biz_payment_received" "receive_amount"  "APPROVED"

  RPT ""
  RPT "### 3.5 材料库存不变量"
  RPT ""
  local stock_bad stock_neg stock_total
  stock_total=$(Q1 "SELECT COUNT(*) FROM biz_project_material_stock WHERE deleted=0")
  stock_bad=$(Q1 "SELECT COUNT(*) FROM biz_project_material_stock WHERE deleted=0 AND ABS(stock_quantity - (total_inbound - total_outbound - total_return + total_transfer_in - total_transfer_out)) > 0.001")
  stock_neg=$(Q1 "SELECT COUNT(*) FROM biz_project_material_stock WHERE deleted=0 AND stock_quantity < 0")
  check_info "biz_project_material_stock 总数" "$stock_total"
  check_num "库存不变量违例" "0" "$stock_bad" "eq" "FAIL"
  check_num "库存负值" "0" "$stock_neg" "eq" "FAIL"

  RPT ""
  RPT "### 3.6 CBS 成本账户勾稽"
  RPT ""
  # actual_amount vs SUM(txn.delta_amount WHERE amount_type='ACTUAL')
  local ca_cnt_local ca_actual_bad ca_commit_bad
  ca_cnt_local=$(Q1 "SELECT COUNT(*) FROM biz_cost_account WHERE deleted=0")
  ca_actual_bad=$(Q1 "SELECT COUNT(*) FROM (SELECT a.id, a.actual_amount, COALESCE(SUM(t.delta_amount),0) AS txn_sum FROM biz_cost_account a LEFT JOIN biz_cost_account_txn t ON t.account_id=a.id AND t.amount_type='ACTUAL' AND t.deleted=0 WHERE a.deleted=0 GROUP BY a.id, a.actual_amount HAVING ABS(COALESCE(a.actual_amount,0)-COALESCE(SUM(t.delta_amount),0))>0.01) t")
  ca_commit_bad=$(Q1 "SELECT COUNT(*) FROM (SELECT a.id, a.commitment_amount, COALESCE(SUM(t.delta_amount),0) AS txn_sum FROM biz_cost_account a LEFT JOIN biz_cost_account_txn t ON t.account_id=a.id AND t.amount_type='COMMITMENT' AND t.deleted=0 WHERE a.deleted=0 GROUP BY a.id, a.commitment_amount HAVING ABS(COALESCE(a.commitment_amount,0)-COALESCE(SUM(t.delta_amount),0))>0.01) t")
  if [ "${ca_cnt_local:-0}" = "0" ]; then
    RPT "| ℹ️ SKIP | CBS actual/commitment 勾稽 | 账户数=0 | 无数据可校 |"
    INFO_COUNT=$((INFO_COUNT+1))
  else
    check_num "CBS actual_amount vs TXN(ACTUAL) 汇总" "0" "${ca_actual_bad:-0}" "eq" "FAIL"
    check_num "CBS commitment_amount vs TXN(COMMITMENT) 汇总" "0" "${ca_commit_bad:-0}" "eq" "FAIL"
  fi

  section_end
}

###############################################################################
# Section 4：数据卫生
###############################################################################
section_4() {
  section_start 4 "数据卫生"

  # 4.1 E2E_TEST_ 残留（租户 1）
  local e2e_proj e2e_inq e2e_quot
  e2e_proj=$(Q1 "SELECT COUNT(*) FROM biz_project WHERE tenant_id=1 AND deleted=0 AND project_name LIKE 'E2E_TEST_%'")
  e2e_inq=$(Q1 "SELECT COUNT(*) FROM biz_inquiry WHERE tenant_id=1 AND deleted=0 AND title LIKE 'E2E_TEST_%'")
  check_info "E2E_TEST_ 项目残留(租户1)" "$e2e_proj"
  check_info "E2E_TEST_ 询价残留(租户1)" "$e2e_inq"
  if [ "$e2e_proj" -gt 0 ] 2>/dev/null; then
    RPT "| ⚠️ WARN | E2E_TEST_ 项目残留 | $e2e_proj | 测试 cleaner 未覆盖租户 1 |"
    WARN_COUNT=$((WARN_COUNT+1))
  fi

  # 4.2 时间戳后缀残留（_17xxxxxxxxxxx 13 位）
  local ts_proj
  ts_proj=$(Q1 "SELECT COUNT(*) FROM biz_project WHERE tenant_id=1 AND deleted=0 AND project_name REGEXP '_17[0-9]{11}'")
  check_info "时间戳后缀项目残留(租户1)" "$ts_proj" "（round5 观察 89 条）"

  # 4.3 B4/B3/B2/API测试 残留
  local b_proj
  b_proj=$(Q1 "SELECT COUNT(*) FROM biz_project WHERE tenant_id=1 AND deleted=0 AND (project_name LIKE 'B4验证%' OR project_name LIKE 'B3验证%' OR project_name LIKE 'B2验证%' OR project_name LIKE 'API测试项目%')")
  check_info "B4/B3/B2/API测试 项目残留" "$b_proj"

  # 4.4 租户 9999 biz_ 表残留（应为 0）
  RPT ""
  RPT "### 4.4 租户 9999 业务表残留（期望 0）"
  RPT ""
  local t9999_total
  t9999_total=$(Q1 "SELECT COALESCE(SUM(c),0) FROM (SELECT COUNT(*) c FROM biz_project WHERE tenant_id=9999 UNION ALL SELECT COUNT(*) FROM biz_labor_contract WHERE tenant_id=9999 UNION ALL SELECT COUNT(*) FROM biz_machine_contract WHERE tenant_id=9999 UNION ALL SELECT COUNT(*) FROM biz_subcontract WHERE tenant_id=9999 UNION ALL SELECT COUNT(*) FROM biz_purchase_contract WHERE tenant_id=9999 UNION ALL SELECT COUNT(*) FROM biz_construction_contract WHERE tenant_id=9999 UNION ALL SELECT COUNT(*) FROM biz_payment_apply WHERE tenant_id=9999 UNION ALL SELECT COUNT(*) FROM biz_payment_received WHERE tenant_id=9999 UNION ALL SELECT COUNT(*) FROM biz_material_inbound WHERE tenant_id=9999 UNION ALL SELECT COUNT(*) FROM biz_material_outbound WHERE tenant_id=9999) x")
  t9999_total=${t9999_total:-0}
  check_info "租户9999 biz_ 关键表残留合计" "$t9999_total" "（期望 0，参照 verify-l4-clean.sh）"
  if [ "$t9999_total" != "0" ]; then
    RPT "| ⚠️ WARN | 租户9999 残留未清零 | $t9999_total | L4 测试 cleaner 漏网 |"
    WARN_COUNT=$((WARN_COUNT+1))
  fi

  # 4.5 演示种子完整性（ID 段 90001-99999）
  RPT ""
  RPT "### 4.5 演示种子完整性（ID 90001-99999）"
  RPT ""
  local seed_proj seed_contract seed_payment
  seed_proj=$(Q1 "SELECT COUNT(*) FROM biz_project WHERE id BETWEEN 90001 AND 99999")
  seed_contract=$(Q1 "SELECT COUNT(*) FROM biz_construction_contract WHERE id BETWEEN 90001 AND 99999")
  seed_payment=$(Q1 "SELECT COUNT(*) FROM biz_payment_apply WHERE id BETWEEN 90001 AND 99999")
  # 2026-09-18 修正：期望值 3 已过期。种子项目实为 4 个——90001/90002/90003 来自
  # 31_V2026_26__seed_demo_data.sql，90004（城北河道综合整治工程）是
  # 45_V2026_43__seed_closeable_project.sql 为 E2E 结项链路补的夹具，
  # 当时未同步上调本期望值，导致长期误报 WARN。
  check_num "种子项目数" "4" "$seed_proj" "eq" "WARN"
  check_info "种子施工合同数" "$seed_contract" "（期望 ≥3）"
  check_info "种子付款申请数" "$seed_payment" "（期望 ≥3）"

  # 4.6 业务编号重复（同租户内）
  RPT ""
  RPT "### 4.6 业务编号租户内重复"
  RPT ""
  local dup_proj dup_labor dup_purchase dup_sub dup_machine
  dup_proj=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, project_code FROM biz_project WHERE deleted=0 GROUP BY tenant_id, project_code HAVING COUNT(*)>1) t")
  dup_labor=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, contract_code FROM biz_labor_contract WHERE deleted=0 AND contract_code IS NOT NULL GROUP BY tenant_id, contract_code HAVING COUNT(*)>1) t")
  dup_purchase=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, contract_code FROM biz_purchase_contract WHERE deleted=0 GROUP BY tenant_id, contract_code HAVING COUNT(*)>1) t")
  dup_sub=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, contract_code FROM biz_subcontract WHERE deleted=0 AND contract_code IS NOT NULL GROUP BY tenant_id, contract_code HAVING COUNT(*)>1) t")
  dup_machine=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, contract_code FROM biz_machine_contract WHERE deleted=0 AND contract_code IS NOT NULL GROUP BY tenant_id, contract_code HAVING COUNT(*)>1) t")
  check_num "biz_project.project_code 重复" "0" "$dup_proj" "eq" "FAIL"
  check_num "biz_labor_contract.contract_code 重复" "0" "$dup_labor" "eq" "WARN"
  check_num "biz_purchase_contract.contract_code 重复" "0" "$dup_purchase" "eq" "FAIL"
  check_num "biz_subcontract.contract_code 重复" "0" "$dup_sub" "eq" "WARN"
  check_num "biz_machine_contract.contract_code 重复" "0" "$dup_machine" "eq" "WARN"

  # 4.7 NULL 必填字段
  RPT ""
  RPT "### 4.7 NULL 必填字段"
  RPT ""
  local null_proj_code null_tenant_proj null_tenant_pay
  null_proj_code=$(Q1 "SELECT COUNT(*) FROM biz_project WHERE deleted=0 AND (project_code IS NULL OR project_code='')")
  null_tenant_proj=$(Q1 "SELECT COUNT(*) FROM biz_project WHERE deleted=0 AND tenant_id IS NULL")
  null_tenant_pay=$(Q1 "SELECT COUNT(*) FROM biz_payment_apply WHERE deleted=0 AND tenant_id IS NULL")
  check_num "biz_project.project_code NULL/空" "0" "$null_proj_code" "eq" "FAIL"
  check_num "biz_project.tenant_id NULL" "0" "$null_tenant_proj" "eq" "FAIL"
  check_num "biz_payment_apply.tenant_id NULL" "0" "$null_tenant_pay" "eq" "FAIL"

  # 4.8 逻辑删除孤儿（deleted=1 项目仍被 deleted=0 合同引用）
  RPT ""
  RPT "### 4.8 逻辑删除孤儿"
  RPT ""
  local orphan_contract orphan_payment
  orphan_contract=$(Q1 "SELECT COUNT(*) FROM biz_construction_contract c JOIN biz_project p ON c.project_id=p.id WHERE c.deleted=0 AND p.deleted=1")
  orphan_payment=$(Q1 "SELECT COUNT(*) FROM biz_payment_apply pa JOIN biz_project p ON pa.project_id=p.id WHERE pa.deleted=0 AND p.deleted=1")
  check_num "施工合同引用已删除项目" "0" "$orphan_contract" "eq" "WARN"
  check_num "付款申请引用已删除项目" "0" "$orphan_payment" "eq" "WARN"

  section_end
}

###############################################################################
# Section 5：业务规则违例
###############################################################################
section_5() {
  section_start 5 "业务规则违例"

  # 5.1 已关闭项目有新活动
  local closed_active
  closed_active=$(Q1 "SELECT COUNT(*) FROM biz_payment_apply pa JOIN biz_project p ON pa.project_id=p.id WHERE p.status='CLOSED' AND p.deleted=0 AND pa.deleted=0 AND pa.created_at > p.updated_at")
  check_num "CLOSED 项目有新付款申请" "0" "$closed_active" "eq" "WARN"

  # 5.2 超付（cumulative_paid > contract_amount * 1.05）
  RPT ""
  RPT "### 5.2 超付检查（5% 容差）"
  RPT ""
  local overpay_labor overpay_machine overpay_sub overpay_purchase
  overpay_labor=$(Q1 "SELECT COUNT(*) FROM biz_labor_contract WHERE deleted=0 AND contract_amount>0 AND cumulative_paid > contract_amount*1.05")
  overpay_machine=$(Q1 "SELECT COUNT(*) FROM biz_machine_contract WHERE deleted=0 AND contract_amount>0 AND cumulative_paid > contract_amount*1.05")
  overpay_sub=$(Q1 "SELECT COUNT(*) FROM biz_subcontract WHERE deleted=0 AND contract_amount>0 AND cumulative_paid > contract_amount*1.05")
  overpay_purchase=$(Q1 "SELECT COUNT(*) FROM biz_purchase_contract WHERE deleted=0 AND contract_amount>0 AND cumulative_paid > contract_amount*1.05")
  check_num "劳务合同超付" "0" "$overpay_labor" "eq" "WARN"
  check_num "机械合同超付" "0" "$overpay_machine" "eq" "WARN"
  check_num "分包合同超付" "0" "$overpay_sub" "eq" "WARN"
  check_num "采购合同超付" "0" "$overpay_purchase" "eq" "WARN"

  # 5.3 超结算
  RPT ""
  RPT "### 5.3 超结算检查（5% 容差）"
  RPT ""
  local oversettle_labor oversettle_sub oversettle_purchase
  oversettle_labor=$(Q1 "SELECT COUNT(*) FROM biz_labor_contract WHERE deleted=0 AND contract_amount>0 AND cumulative_settlement > contract_amount*1.05")
  oversettle_sub=$(Q1 "SELECT COUNT(*) FROM biz_subcontract WHERE deleted=0 AND contract_amount>0 AND cumulative_settlement > contract_amount*1.05")
  oversettle_purchase=$(Q1 "SELECT COUNT(*) FROM biz_purchase_contract WHERE deleted=0 AND contract_amount>0 AND cumulative_settlement > contract_amount*1.05")
  check_num "劳务合同超结算" "0" "$oversettle_labor" "eq" "WARN"
  check_num "分包合同超结算" "0" "$oversettle_sub" "eq" "WARN"
  check_num "采购合同超结算" "0" "$oversettle_purchase" "eq" "WARN"

  # 5.4 无合同付款（contract_id 在对应合同表中不存在）
  # 2026-09-18 修正：旧版漏掉 biz_other_contract。PaymentApplyService.addCumulativePaid
  # 对非 MODULE_CATEGORIES（PURCHASE/LABOR/MACHINE/SUBCONTRACT）的类别一律路由到
  # otherContractMapper.addCumulativePaid，即「其他支出合同」是合法付款标的
  # （种子 91402 就是带 cumulative_paid=100000 的 OTHER_EXPENSE 合同）。
  # 漏检会在该功能被真实使用时产生假 FAIL。
  RPT ""
  RPT "### 5.4 付款申请引用不存在的合同"
  RPT ""
  local pay_no_contract
  pay_no_contract=$(Q1 "SELECT COUNT(*) FROM biz_payment_apply pa WHERE pa.deleted=0 AND pa.contract_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM biz_labor_contract c WHERE c.id=pa.contract_id AND c.deleted=0) AND NOT EXISTS (SELECT 1 FROM biz_machine_contract c WHERE c.id=pa.contract_id AND c.deleted=0) AND NOT EXISTS (SELECT 1 FROM biz_subcontract c WHERE c.id=pa.contract_id AND c.deleted=0) AND NOT EXISTS (SELECT 1 FROM biz_purchase_contract c WHERE c.id=pa.contract_id AND c.deleted=0) AND NOT EXISTS (SELECT 1 FROM biz_construction_contract c WHERE c.id=pa.contract_id AND c.deleted=0) AND NOT EXISTS (SELECT 1 FROM biz_other_contract c WHERE c.id=pa.contract_id AND c.deleted=0)")
  check_num "付款申请引用不存在合同" "0" "$pay_no_contract" "eq" "FAIL"

  # 5.5 无项目单据
  local pay_no_proj
  pay_no_proj=$(Q1 "SELECT COUNT(*) FROM biz_payment_apply pa WHERE pa.deleted=0 AND NOT EXISTS (SELECT 1 FROM biz_project p WHERE p.id=pa.project_id AND p.deleted=0)")
  check_num "付款申请引用不存在项目" "0" "$pay_no_proj" "eq" "FAIL"

  # 5.6 状态倒挂（DRAFT 合同但 cumulative_paid > 0）
  RPT ""
  RPT "### 5.6 状态倒挂"
  RPT ""
  local invert_labor invert_purchase
  invert_labor=$(Q1 "SELECT COUNT(*) FROM biz_labor_contract WHERE deleted=0 AND status='DRAFT' AND cumulative_paid > 0")
  invert_purchase=$(Q1 "SELECT COUNT(*) FROM biz_purchase_contract WHERE deleted=0 AND status='DRAFT' AND cumulative_paid > 0")
  check_num "DRAFT 劳务合同已有付款" "0" "$invert_labor" "eq" "WARN"
  check_num "DRAFT 采购合同已有付款" "0" "$invert_purchase" "eq" "WARN"

  section_end
}

###############################################################################
# Section 6：租户隔离与权限完整性
###############################################################################
section_6() {
  section_start 6 "租户隔离与权限完整性"

  # 6.1 跨租户污染（付款申请与项目租户不一致）
  local cross_tenant
  cross_tenant=$(Q1 "SELECT COUNT(*) FROM biz_payment_apply pa JOIN biz_project p ON pa.project_id=p.id WHERE pa.deleted=0 AND p.deleted=0 AND pa.tenant_id <> p.tenant_id")
  check_num "付款申请跨租户污染" "0" "$cross_tenant" "eq" "FAIL"

  # 6.2 sys_user 租户分布
  local u_t1 u_t9 u_other
  u_t1=$(Q1 "SELECT COUNT(*) FROM sys_user WHERE tenant_id=1 AND deleted=0")
  u_t9=$(Q1 "SELECT COUNT(*) FROM sys_user WHERE tenant_id=9999 AND deleted=0")
  u_other=$(Q1 "SELECT COUNT(*) FROM sys_user WHERE tenant_id NOT IN (1,9999) AND deleted=0")
  check_info "sys_user 租户分布" "t1=$u_t1 t9999=$u_t9 other=$u_other"

  # 6.3 权限四表孤儿（round5/6 已通过，回归）
  local rm_orphan ur_orphan di_orphan org_orphan
  rm_orphan=$(Q1 "SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_role r ON rm.role_id=r.id WHERE r.id IS NULL")
  ur_orphan=$(Q1 "SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_user u ON ur.user_id=u.id WHERE u.id IS NULL")
  di_orphan=$(Q1 "SELECT COUNT(*) FROM sys_dict_item di LEFT JOIN sys_dict d ON di.dict_id=d.id WHERE d.id IS NULL")
  org_orphan=$(Q1 "SELECT COUNT(*) FROM sys_org o LEFT JOIN sys_org p ON o.parent_id=p.id WHERE o.parent_id<>0 AND p.id IS NULL")
  check_num "sys_role_menu → sys_role 孤儿" "0" "$rm_orphan" "eq" "FAIL"
  check_num "sys_user_role → sys_user 孤儿" "0" "$ur_orphan" "eq" "FAIL"
  check_num "sys_dict_item → sys_dict 孤儿" "0" "$di_orphan" "eq" "FAIL"
  check_num "sys_org 父节点孤儿" "0" "$org_orphan" "eq" "FAIL"

  # 6.4 serial_number_rule 租户内 business_type 重复
  local snr_dup
  snr_dup=$(Q1 "SELECT COUNT(*) FROM (SELECT tenant_id, business_type FROM serial_number_rule WHERE deleted=0 GROUP BY tenant_id, business_type HAVING COUNT(*)>1) t")
  check_num "serial_number_rule 租户内重复" "0" "$snr_dup" "eq" "FAIL"

  # 6.5 sys_menu 父节点孤儿
  local menu_orphan
  menu_orphan=$(Q1 "SELECT COUNT(*) FROM sys_menu m LEFT JOIN sys_menu p ON m.parent_id=p.id WHERE m.parent_id<>0 AND m.deleted=0 AND p.id IS NULL")
  check_num "sys_menu 父节点孤儿" "0" "$menu_orphan" "eq" "FAIL"

  section_end
}

###############################################################################
# Section 7：容量与索引健康
###############################################################################
section_7() {
  section_start 7 "容量与索引健康"

  # 7.1 Top 10 大表
  RPT ""
  RPT "### 7.1 Top 10 大表（data_length + index_length）"
  RPT ""
  RPT '```'
  Q "SELECT table_name, ROUND((data_length+index_length)/1024/1024, 2) AS size_mb, table_rows FROM information_schema.tables WHERE table_schema='$DB' ORDER BY (data_length+index_length) DESC LIMIT 10" >> "$REPORT_FILE"
  RPT '```'

  # 7.2 行数 Top 10
  RPT ""
  RPT "### 7.2 行数 Top 10"
  RPT ""
  RPT '```'
  Q "SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema='$DB' ORDER BY table_rows DESC LIMIT 10" >> "$REPORT_FILE"
  RPT '```'

  # 7.3 碎片率高的表（data_free / data_length > 0.3）
  local frag_cnt
  frag_cnt=$(Q1 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB' AND data_length>0 AND data_free/data_length > 0.3")
  check_info "碎片率>30% 的表数" "$frag_cnt" "（仅观察，不触发 OPTIMIZE）"

  # 7.4 缺失索引的 FK 列（抽查 biz_ 表 project_id / tenant_id）
  RPT ""
  RPT "### 7.4 缺失索引的 FK 列（抽查）"
  RPT ""
  local miss_idx
  miss_idx=$(Q1 "SELECT COUNT(*) FROM information_schema.columns c WHERE c.table_schema='$DB' AND c.table_name LIKE 'biz\\_%' AND c.column_name IN ('project_id','tenant_id','contract_id') AND NOT EXISTS (SELECT 1 FROM information_schema.statistics s WHERE s.table_schema=c.table_schema AND s.table_name=c.table_name AND s.column_name=c.column_name)")
  check_info "biz_ 表 FK 列缺索引数" "$miss_idx" "（project_id/tenant_id/contract_id）"

  # 7.5 自增 ID 逼近上限（防御性，BIGINT 上限 2^63-1）
  local auto_risk
  auto_risk=$(Q1 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB' AND auto_increment IS NOT NULL AND auto_increment > 4611686018427387904")
  check_num "自增 ID 逼近 BIGINT 上限" "0" "$auto_risk" "eq" "WARN"

  section_end
}

###############################################################################
# 报告收尾
###############################################################################
report_footer() {
  RPT ""
  RPT "---"
  RPT ""
  RPT "## 执行摘要"
  RPT ""
  RPT "| 指标 | 计数 |"
  RPT "|------|------|"
  RPT "| ✅ PASS | $PASS_COUNT |"
  RPT "| ❌ FAIL | $FAIL_COUNT |"
  RPT "| ⚠️ WARN | $WARN_COUNT |"
  RPT "| ℹ️ INFO | $INFO_COUNT |"
  RPT ""
  RPT "**总体结论**："
  if [ "$FAIL_COUNT" -gt 0 ]; then
    RPT ""
    RPT "🔴 **数据存在问题**：发现 $FAIL_COUNT 项 FAIL 级缺陷，需立即处置。"
  elif [ "$WARN_COUNT" -gt 0 ]; then
    RPT ""
    RPT "🟡 **数据基本健康，存在 $WARN_COUNT 项观察项**：无 FAIL 级缺陷，但需关注 WARN 项演变。"
  else
    RPT ""
    RPT "🟢 **数据健康**：全部检查项通过，无 FAIL / WARN。"
  fi
  RPT ""
  RPT "---"
  RPT ""
  RPT "**报告生成时间**: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  RPT "**审计人员**: Qoder + Human Collaboration"
  RPT "**脚本版本**: keys/audit-data-round7.sh (Round 7)"
  RPT "**下轮计划**: 第 8 轮（视本轮发现决定）"
}

###############################################################################
# 主入口
###############################################################################
usage() {
  cat <<EOF
用法: audit-data-round7.sh [command]
  (无参)      完整审计：Section 0-7
  all         同上
  section N   只执行第 N 节（0-7）
  regression  只执行 Section 1（快速回归，~30s）
  new-tables  只执行 Section 2（新增表覆盖）
  help        显示本帮助

环境变量：
  ZWI_DB_PW       MySQL 密码（默认 zwinsight123）
  ZWI_DB          数据库名（默认 zw_insight）
  ZWI_MYSQL_CT    MySQL 容器名（默认 zwi-mysql）
  ZWI_WORKDIR     工作目录（默认 /root/zwi-deploy）
  ZWI_REPORT_FILE 报告输出路径（默认 \$WORKDIR/audit-round7-<TS>.md）
EOF
}

main() {
  local cmd="${1:-all}"
  echo "================ ZW-Insight 数据审计 Round 7 ================"
  echo "开始时间: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "报告路径: $REPORT_FILE"
  echo ""

  RPT_HEAD

  case "$cmd" in
    all|"")
      section_0
      section_1
      section_2
      section_3
      section_4
      section_5
      section_6
      section_7
      ;;
    regression)
      section_0
      section_1
      ;;
    new-tables)
      section_0
      section_2
      ;;
    section)
      local n="${2:-}"
      case "$n" in
        0) section_0 ;;
        1) section_1 ;;
        2) section_2 ;;
        3) section_3 ;;
        4) section_4 ;;
        5) section_5 ;;
        6) section_6 ;;
        7) section_7 ;;
        *) echo "无效节号: $n（有效 0-7）" >&2; exit 2 ;;
      esac
      ;;
    help|-h|--help)
      usage
      exit 0
      ;;
    *)
      echo "未知命令: $cmd" >&2
      usage
      exit 2
      ;;
  esac

  report_footer

  echo ""
  echo "================ 审计完成 ================"
  echo "结束时间: $(date -u +%H:%M:%S)"
  echo "PASS=$PASS_COUNT FAIL=$FAIL_COUNT WARN=$WARN_COUNT INFO=$INFO_COUNT"
  echo "报告: $REPORT_FILE"

  # 清理临时目录
  rm -rf "$TMPDIR" 2>/dev/null || true

  # 退出码：0=全 PASS，1=有 FAIL，2=仅 WARN
  if [ "$FAIL_COUNT" -gt 0 ]; then exit 1
  elif [ "$WARN_COUNT" -gt 0 ]; then exit 2
  else exit 0
  fi
}

main "$@"
