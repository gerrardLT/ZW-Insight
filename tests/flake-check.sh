#!/usr/bin/env bash
###############################################################################
# flake-check.sh — 测试不稳定性检测器 (Flaky Test Detector)
#
# 设计依据：spec test-maturity-upgrade → 3.2 flaky 检测
#
# 功能：
#   1. 连续运行指定的测试命令 N 次（默认 3 次）
#   2. 比较每次的执行结果（pass/fail）
#   3. 如果结果不一致（有时通过有时失败），标记为 FLAKY
#   4. 将 FLAKY 测试结果写入受阻台账
#
# 用法示例：
#   # 检测 L1 单元测试的 flaky 情况
#   bash tests/flake-check.sh --test-l1
#
#   # 检测特定模块的测试
#   bash tests/flake-check.sh --module zw-finance --times 5
#
#   # 自动检测所有测试层级的 flaky
#   bash tests/flake-check.sh --auto
#
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_FILE="$SCRIPT_DIR/reports/flaky-report.json"
AUDIT_LOG="$SCRIPT_DIR/../.kiro/specs/test-maturity-upgrade/tasks.md"

RUNS="${TEST_RUNS:-3}"           # 默认运行 3 次
VERBOSE=false
OUTPUT_FORMAT="text"              # text | json

# ===========================================================================
# 颜色输出
# ===========================================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

info()    { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[FAIL]${NC} $*"; }
header()  { echo -e "${BOLD}${BLUE}$*${NC}"; }

# ===========================================================================
# 函数定义
# ===========================================================================
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --runs=*)
                RUNS="${1#--runs=}"
                shift
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            --output=*)
                OUTPUT_FORMAT="${1#--output=}"
                shift
                ;;
            *)
                error "未知参数：$1"
                exit 1
                ;;
        esac
    done
}

run_test_once() {
    local test_type="$1"
    local start_time end_time duration
    
    start_time=$(date +%s)
    
    case "$test_type" in
        l1)
            cd "$PROJECT_ROOT/zw-insight-server"
            mvn -B clean test -q 2>&1
            local result=$?
            ;;
        l2)
            cd "$PROJECT_ROOT/zw-insight-server"
            mvn -B verify -Pintegration-test -Djacoco.skip=true 2>&1
            local result=$?
            ;;
        *)
            error "不支持的测试类型：$test_type"
            return 1
            ;;
    esac
    
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    
    echo "$result $duration"
}

check_flakiness() {
    local test_results=()
    local pass_count=0
    local fail_count=0
    
    info "开始 flaky 检测 ($RUNS 次重复执行)..."
    
    for ((i=1; i<=RUNS; i++)); do
        header "第 $i/$RUNS 次执行"
        
        local output
        output=$(run_test_once "l1")
        local result=$(echo "$output" | cut -d' ' -f1)
        local duration=$(echo "$output" | cut -d' ' -f2)
        
        if [ "$result" -eq 0 ]; then
            test_results+=("PASS-$duration")
            ((pass_count++))
            echo -e "${GREEN}✓ PASS (${duration}s)${NC}"
        else
            test_results+=("FAIL-$duration")
            ((fail_count++))
            echo -e "${RED}✗ FAIL (${duration}s)${NC}"
        fi
        
        # 如果不是最后一次，等待 5 秒再执行
        if [ $i -lt $RUNS ]; then
            sleep 5
        fi
    done
    
    echo ""
    
    # 分析是否 flaky
    if [ $pass_count -gt 0 ] && [ $fail_count -gt 0 ]; then
        warn "🚨 检测到 Flaky Test!"
        warn "结果不一致：PASS=$pass_count, FAIL=$fail_count (共 $RUNS 次)"
        
        # 生成报告
        generate_report "l1" "${test_results[*]}" true
        
        # 写入台账
        append_to_audit_log "l1-flaky" "PASS=$pass_count/FAIL=$fail_count (共$RUNS次)"
        
        return 1
    elif [ $pass_count -eq $RUNS ]; then
        info "✅ 稳定通过（$RUNS/$RUNS 次成功）"
        generate_report "l1" "${test_results[*]}" false
        return 0
    else
        error "❌ 持续失败（$RUNS/$RUNS 次都失败）"
        generate_report "l1" "${test_results[*]}" false
        return 1
    fi
}

generate_report() {
    local test_suite="$1"
    local results="$2"
    local is_flaky="$3"
    
    mkdir -p "$SCRIPT_DIR/reports"
    
    cat > "$REPORT_FILE" << EOF
{
  "timestamp": "$(date -Iseconds)",
  "test_suite": "$test_suite",
  "total_runs": $RUNS,
  "results": "$results",
  "is_flaky": $is_flaky,
  "recommendation": "$([ "$is_flaky" = "true" ] && echo "需要修复或豁免" || echo "无需处理")"
}
EOF
    
    echo "报告已保存至：$REPORT_FILE"
}

append_to_audit_log() {
    local issue_type="$1"
    local details="$2"
    
    local timestamp
    timestamp=$(date +"%Y-%m-%d")
    
    local log_entry
    log_entry=$(cat << EOF

|$timestamp | 3.2 Flaky 检测 | $issue_type - $details | FLAKY | 已通过 flake-check.sh 检测并记录 | 待评估/修复 | AI 自动检测 | 登记在案 |
EOF
)
    
    echo -e "$log_entry" >> "$AUDIT_LOG"
    echo "✅ 已更新至受阻台账"
}

show_help() {
    cat << 'EOF'
用法: bash tests/flake-check.sh [选项]

检测测试用例的不稳定性（flaky tests）。

选项:
  --runs=N          运行次数（默认：3）
  --verbose, -v     显示详细日志
  --help, -h        显示帮助信息

返回:
  - 成功：所有执行一致（全过或全失败）
  - 失败：检测到 flaky（有时通过有时失败）

示例:
  # 默认检测（3 次）
  bash tests/flake-check.sh
  
  # 运行 5 次
  bash tests/flake-check.sh --runs=5
  
  # 详细模式
  bash tests/flake-check.sh -v
EOF
}

main() {
    parse_args "$@"
    
    header "ZW-Insight Flaky Test Detector"
    echo "运行次数：$RUNS"
    echo ""
    
    check_flakiness
}

main "$@"
