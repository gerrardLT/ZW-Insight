#!/usr/bin/env bash
###############################################################################
# run-all-tests.sh  —  ZW-Insight 全层次测试统一编排脚本
#
# 按 L1→L2→L3→L4→L5 顺序依次执行测试金字塔各层级，并生成汇总报告。
#
# 用法：
#   bash tests/run-all-tests.sh                          # 执行所有层级
#   bash tests/run-all-tests.sh --layers=L1,L3           # 仅执行 L1 和 L3
#   bash tests/run-all-tests.sh --fail-fast              # 首个失败层级后停止
#   bash tests/run-all-tests.sh --layers=L1,L2 --fail-fast
#
# 层级定义：
#   L1: 单元测试        — cd zw-insight-server && mvn test
#   L2: 集成测试        — cd zw-insight-server && mvn verify -Pintegration-test
#   L3: API 接口测试    — 遍历执行 keys/test-api-*.sh
#   L4: 端到端业务流    — bash keys/lifecycle-sim-v2.sh
#   L5: 前端 E2E        — cd zw-insight-web && npx playwright test --project=e2e-real
#
# 报告输出：
#   - 终端格式化汇总表格（ASCII art）
#   - JSON 格式报告：tests/reports/all-tests-report.json
#
# 设计依据：full-layer-test-suite spec
#   - 需求 9.1：统一编排脚本，按顺序依次执行
#   - 需求 9.2：某层级全部通过后继续下一层级
#   - 需求 9.3：--fail-fast 选项控制失败后行为
#   - 需求 9.4：汇总报告（每层级通过数/失败数/跳过数/耗时）
#   - 需求 9.5：--layers 参数选择执行特定层级
###############################################################################
set -uo pipefail

# ===========================================================================
# 全局变量
# ===========================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

REPORT_DIR="$SCRIPT_DIR/reports"
REPORT_FILE="$REPORT_DIR/all-tests-report.json"

# 默认所有层级
ALL_LAYERS="L1,L2,L3,L4,L5"
SELECTED_LAYERS="$ALL_LAYERS"
FAIL_FAST=false

# 层级执行结果（使用关联数组）
declare -A LAYER_STATUS     # pass / fail / skip
declare -A LAYER_PASSED     # 通过数
declare -A LAYER_FAILED     # 失败数
declare -A LAYER_SKIPPED    # 跳过数
declare -A LAYER_DURATION   # 耗时（秒）
declare -A LAYER_MESSAGE    # 附加信息

# 全局统计
TOTAL_PASSED=0
TOTAL_FAILED=0
TOTAL_SKIPPED=0
OVERALL_START=0

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
divider() { echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"; }

# ===========================================================================
# 参数解析
# ===========================================================================
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --layers=*)
                SELECTED_LAYERS="${1#--layers=}"
                shift
                ;;
            --fail-fast)
                FAIL_FAST=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

show_help() {
    cat << 'EOF'
用法: bash tests/run-all-tests.sh [选项]

选项:
  --layers=L1,L2,...   选择执行的层级（默认: L1,L2,L3,L4,L5）
  --fail-fast          首个失败层级后停止执行
  --help, -h           显示帮助信息

层级:
  L1  单元测试（mvn test）
  L2  集成测试（mvn verify -Pintegration-test）
  L3  API 接口测试（keys/test-api-*.sh）
  L4  端到端业务流（keys/lifecycle-sim-v2.sh）
  L5  前端 E2E（Playwright e2e-real）

示例:
  bash tests/run-all-tests.sh                        # 全部层级
  bash tests/run-all-tests.sh --layers=L1,L3         # 仅 L1 和 L3
  bash tests/run-all-tests.sh --fail-fast            # 失败即停
  bash tests/run-all-tests.sh --layers=L1 --fail-fast
EOF
}

# ===========================================================================
# 工具函数
# ===========================================================================

# 检查某个层级是否被选中
is_layer_selected() {
    local layer="$1"
    echo ",$SELECTED_LAYERS," | grep -qi ",$layer,"
}

# 获取当前时间戳（秒）
now_seconds() {
    date +%s
}

# 格式化耗时（秒 → 可读字符串）
format_duration() {
    local seconds="$1"
    if [[ $seconds -ge 3600 ]]; then
        printf "%dh %dm %ds" $((seconds/3600)) $((seconds%3600/60)) $((seconds%60))
    elif [[ $seconds -ge 60 ]]; then
        printf "%dm %ds" $((seconds/60)) $((seconds%60))
    else
        printf "%ds" "$seconds"
    fi
}

# 初始化层级结果为跳过
init_layer_result() {
    local layer="$1"
    LAYER_STATUS[$layer]="skip"
    LAYER_PASSED[$layer]=0
    LAYER_FAILED[$layer]=0
    LAYER_SKIPPED[$layer]=0
    LAYER_DURATION[$layer]=0
    LAYER_MESSAGE[$layer]="未执行"
}

# ===========================================================================
# L1: 单元测试
# ===========================================================================
run_l1() {
    local start_time
    start_time=$(now_seconds)
    local server_dir="$PROJECT_ROOT/zw-insight-server"

    header "  [L1] 单元测试 — mvn test"

    if [[ ! -f "$server_dir/pom.xml" ]]; then
        warn "未找到 zw-insight-server/pom.xml，跳过 L1"
        LAYER_STATUS[L1]="skip"
        LAYER_SKIPPED[L1]=1
        LAYER_MESSAGE[L1]="pom.xml 不存在"
        LAYER_DURATION[L1]=$(( $(now_seconds) - start_time ))
        return 0
    fi

    if ! command -v mvn &>/dev/null; then
        warn "mvn 命令不可用，跳过 L1"
        LAYER_STATUS[L1]="skip"
        LAYER_SKIPPED[L1]=1
        LAYER_MESSAGE[L1]="mvn 未安装"
        LAYER_DURATION[L1]=$(( $(now_seconds) - start_time ))
        return 0
    fi

    local output_file="$REPORT_DIR/l1-output.log"
    info "执行: cd zw-insight-server && mvn test -B"

    local exit_code=0
    (cd "$server_dir" && mvn test -B 2>&1) > "$output_file" || exit_code=$?

    # 解析 Maven Surefire 输出中的测试统计
    local tests_run failures errors skipped
    tests_run=$(grep -oP 'Tests run: \K\d+' "$output_file" | awk '{s+=$1} END {print s+0}')
    failures=$(grep -oP 'Failures: \K\d+' "$output_file" | awk '{s+=$1} END {print s+0}')
    errors=$(grep -oP 'Errors: \K\d+' "$output_file" | awk '{s+=$1} END {print s+0}')
    skipped=$(grep -oP 'Skipped: \K\d+' "$output_file" | awk '{s+=$1} END {print s+0}')

    LAYER_PASSED[L1]=$(( tests_run - failures - errors - skipped ))
    LAYER_FAILED[L1]=$(( failures + errors ))
    LAYER_SKIPPED[L1]=$skipped

    if [[ $exit_code -eq 0 && $(( failures + errors )) -eq 0 ]]; then
        LAYER_STATUS[L1]="pass"
        LAYER_MESSAGE[L1]="Tests: ${tests_run}, Failures: ${failures}, Errors: ${errors}"
    else
        LAYER_STATUS[L1]="fail"
        LAYER_MESSAGE[L1]="EXIT=$exit_code Tests: ${tests_run}, Failures: ${failures}, Errors: ${errors}"
        # 如果没有解析到统计数据但退出码非零，至少标记 1 个失败
        if [[ ${LAYER_FAILED[L1]} -eq 0 && $exit_code -ne 0 ]]; then
            LAYER_FAILED[L1]=1
        fi
    fi

    LAYER_DURATION[L1]=$(( $(now_seconds) - start_time ))
}

# ===========================================================================
# L2: 集成测试
# ===========================================================================
run_l2() {
    local start_time
    start_time=$(now_seconds)
    local server_dir="$PROJECT_ROOT/zw-insight-server"

    header "  [L2] 集成测试 — mvn verify -Pintegration-test"

    if [[ ! -f "$server_dir/pom.xml" ]]; then
        warn "未找到 zw-insight-server/pom.xml，跳过 L2"
        LAYER_STATUS[L2]="skip"
        LAYER_SKIPPED[L2]=1
        LAYER_MESSAGE[L2]="pom.xml 不存在"
        LAYER_DURATION[L2]=$(( $(now_seconds) - start_time ))
        return 0
    fi

    if ! command -v mvn &>/dev/null; then
        warn "mvn 命令不可用，跳过 L2"
        LAYER_STATUS[L2]="skip"
        LAYER_SKIPPED[L2]=1
        LAYER_MESSAGE[L2]="mvn 未安装"
        LAYER_DURATION[L2]=$(( $(now_seconds) - start_time ))
        return 0
    fi

    local output_file="$REPORT_DIR/l2-output.log"
    info "执行: cd zw-insight-server && mvn verify -Pintegration-test -B"

    local exit_code=0
    (cd "$server_dir" && mvn verify -Pintegration-test -B 2>&1) > "$output_file" || exit_code=$?

    # 解析 Failsafe 输出
    local tests_run failures errors skipped
    tests_run=$(grep -oP 'Tests run: \K\d+' "$output_file" | awk '{s+=$1} END {print s+0}')
    failures=$(grep -oP 'Failures: \K\d+' "$output_file" | awk '{s+=$1} END {print s+0}')
    errors=$(grep -oP 'Errors: \K\d+' "$output_file" | awk '{s+=$1} END {print s+0}')
    skipped=$(grep -oP 'Skipped: \K\d+' "$output_file" | awk '{s+=$1} END {print s+0}')

    LAYER_PASSED[L2]=$(( tests_run - failures - errors - skipped ))
    LAYER_FAILED[L2]=$(( failures + errors ))
    LAYER_SKIPPED[L2]=$skipped

    if [[ $exit_code -eq 0 && $(( failures + errors )) -eq 0 ]]; then
        LAYER_STATUS[L2]="pass"
        LAYER_MESSAGE[L2]="Tests: ${tests_run}, Failures: ${failures}, Errors: ${errors}"
    else
        LAYER_STATUS[L2]="fail"
        LAYER_MESSAGE[L2]="EXIT=$exit_code Tests: ${tests_run}, Failures: ${failures}, Errors: ${errors}"
        if [[ ${LAYER_FAILED[L2]} -eq 0 && $exit_code -ne 0 ]]; then
            LAYER_FAILED[L2]=1
        fi
    fi

    LAYER_DURATION[L2]=$(( $(now_seconds) - start_time ))
}

# ===========================================================================
# L3: API 接口测试
# ===========================================================================
run_l3() {
    local start_time
    start_time=$(now_seconds)
    local keys_dir="$PROJECT_ROOT/keys"

    header "  [L3] API 接口测试 — keys/test-api-*.sh"

    # 查找所有 API 测试脚本
    local scripts=()
    while IFS= read -r -d '' script; do
        scripts+=("$script")
    done < <(find "$keys_dir" -maxdepth 1 -name "test-api-*.sh" -print0 2>/dev/null | sort -z)

    if [[ ${#scripts[@]} -eq 0 ]]; then
        warn "未找到 keys/test-api-*.sh 脚本，跳过 L3"
        LAYER_STATUS[L3]="skip"
        LAYER_SKIPPED[L3]=1
        LAYER_MESSAGE[L3]="无 test-api-*.sh 脚本"
        LAYER_DURATION[L3]=$(( $(now_seconds) - start_time ))
        return 0
    fi

    local l3_passed=0
    local l3_failed=0
    local failed_scripts=""

    for script in "${scripts[@]}"; do
        local script_name
        script_name=$(basename "$script")
        printf "  执行 %-40s" "$script_name..."

        if [[ ! -x "$script" ]]; then
            chmod +x "$script" 2>/dev/null || true
        fi

        local script_output="$REPORT_DIR/l3-${script_name}.log"
        if bash "$script" > "$script_output" 2>&1; then
            echo -e " ${GREEN}PASS${NC}"
            ((l3_passed++))
        else
            echo -e " ${RED}FAIL${NC}"
            ((l3_failed++))
            failed_scripts="${failed_scripts}${script_name}, "
        fi
    done

    LAYER_PASSED[L3]=$l3_passed
    LAYER_FAILED[L3]=$l3_failed
    LAYER_SKIPPED[L3]=0

    if [[ $l3_failed -eq 0 ]]; then
        LAYER_STATUS[L3]="pass"
        LAYER_MESSAGE[L3]="${#scripts[@]} 个脚本全部通过"
    else
        LAYER_STATUS[L3]="fail"
        LAYER_MESSAGE[L3]="失败: ${failed_scripts%, }"
    fi

    LAYER_DURATION[L3]=$(( $(now_seconds) - start_time ))
}

# ===========================================================================
# L4: 端到端业务流
# ===========================================================================
run_l4() {
    local start_time
    start_time=$(now_seconds)
    local sim_script="$PROJECT_ROOT/keys/lifecycle-sim-v2.sh"

    header "  [L4] 端到端业务流 — lifecycle-sim-v2.sh"

    if [[ ! -f "$sim_script" ]]; then
        warn "未找到 keys/lifecycle-sim-v2.sh，跳过 L4"
        LAYER_STATUS[L4]="skip"
        LAYER_SKIPPED[L4]=1
        LAYER_MESSAGE[L4]="lifecycle-sim-v2.sh 不存在"
        LAYER_DURATION[L4]=$(( $(now_seconds) - start_time ))
        return 0
    fi

    local output_file="$REPORT_DIR/l4-output.log"
    info "执行: bash keys/lifecycle-sim-v2.sh"

    local exit_code=0
    bash "$sim_script" > "$output_file" 2>&1 || exit_code=$?

    # 尝试从 lifecycle-sim 的 JSON 报告中读取统计
    local sim_report="$SCRIPT_DIR/reports/lifecycle-sim-report.json"
    if [[ -f "$sim_report" ]]; then
        local passed failed skipped
        passed=$(grep -oP '"passed"\s*:\s*\K\d+' "$sim_report" | head -1 || echo "0")
        failed=$(grep -oP '"failed"\s*:\s*\K\d+' "$sim_report" | head -1 || echo "0")
        skipped=$(grep -oP '"skipped"\s*:\s*\K\d+' "$sim_report" | head -1 || echo "0")
        LAYER_PASSED[L4]=${passed:-0}
        LAYER_FAILED[L4]=${failed:-0}
        LAYER_SKIPPED[L4]=${skipped:-0}
    else
        # 无 JSON 报告，根据退出码判断
        if [[ $exit_code -eq 0 ]]; then
            LAYER_PASSED[L4]=1
            LAYER_FAILED[L4]=0
        else
            LAYER_PASSED[L4]=0
            LAYER_FAILED[L4]=1
        fi
        LAYER_SKIPPED[L4]=0
    fi

    if [[ $exit_code -eq 0 ]]; then
        LAYER_STATUS[L4]="pass"
        LAYER_MESSAGE[L4]="业务流模拟通过"
    else
        LAYER_STATUS[L4]="fail"
        LAYER_MESSAGE[L4]="EXIT=$exit_code，查看 l4-output.log"
    fi

    LAYER_DURATION[L4]=$(( $(now_seconds) - start_time ))
}

# ===========================================================================
# L5: 前端 E2E
# ===========================================================================
run_l5() {
    local start_time
    start_time=$(now_seconds)
    local web_dir="$PROJECT_ROOT/zw-insight-web"

    header "  [L5] 前端 E2E — Playwright e2e-real"

    if [[ ! -f "$web_dir/package.json" ]]; then
        warn "未找到 zw-insight-web/package.json，跳过 L5"
        LAYER_STATUS[L5]="skip"
        LAYER_SKIPPED[L5]=1
        LAYER_MESSAGE[L5]="package.json 不存在"
        LAYER_DURATION[L5]=$(( $(now_seconds) - start_time ))
        return 0
    fi

    if ! command -v npx &>/dev/null; then
        warn "npx 命令不可用，跳过 L5"
        LAYER_STATUS[L5]="skip"
        LAYER_SKIPPED[L5]=1
        LAYER_MESSAGE[L5]="npx 未安装"
        LAYER_DURATION[L5]=$(( $(now_seconds) - start_time ))
        return 0
    fi

    if [[ ! -d "$web_dir/node_modules/@playwright" ]]; then
        warn "Playwright 未安装，跳过 L5"
        LAYER_STATUS[L5]="skip"
        LAYER_SKIPPED[L5]=1
        LAYER_MESSAGE[L5]="Playwright 未安装"
        LAYER_DURATION[L5]=$(( $(now_seconds) - start_time ))
        return 0
    fi

    local output_file="$REPORT_DIR/l5-output.log"
    info "执行: cd zw-insight-web && npx playwright test --project=e2e-real"

    local exit_code=0
    (cd "$web_dir" && npx playwright test --project=e2e-real 2>&1) > "$output_file" || exit_code=$?

    # 解析 Playwright 输出："X passed", "Y failed", "Z skipped"
    local passed failed skipped
    passed=$(grep -oP '\d+(?= passed)' "$output_file" | tail -1)
    failed=$(grep -oP '\d+(?= failed)' "$output_file" | tail -1)
    skipped=$(grep -oP '\d+(?= skipped)' "$output_file" | tail -1)

    LAYER_PASSED[L5]=${passed:-0}
    LAYER_FAILED[L5]=${failed:-0}
    LAYER_SKIPPED[L5]=${skipped:-0}

    if [[ $exit_code -eq 0 ]]; then
        LAYER_STATUS[L5]="pass"
        LAYER_MESSAGE[L5]="passed=${passed:-0}, failed=${failed:-0}, skipped=${skipped:-0}"
    else
        LAYER_STATUS[L5]="fail"
        LAYER_MESSAGE[L5]="EXIT=$exit_code passed=${passed:-0}, failed=${failed:-0}"
        # 确保失败时至少有 1 个失败计数
        if [[ ${LAYER_FAILED[L5]} -eq 0 ]]; then
            LAYER_FAILED[L5]=1
        fi
    fi

    LAYER_DURATION[L5]=$(( $(now_seconds) - start_time ))
}

# ===========================================================================
# 汇总报告输出（格式化 ASCII 表格）
# ===========================================================================
print_summary() {
    local overall_duration=$(( $(now_seconds) - OVERALL_START ))

    echo ""
    echo ""
    echo -e "${BOLD}╔══════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}║               ZW-Insight 全层次测试汇总报告                             ║${NC}"
    echo -e "${BOLD}╠══════════╦═══════════╦══════════╦══════════╦══════════╦═════════════════╣${NC}"
    echo -e "${BOLD}║  层级    ║   状态    ║  通过    ║  失败    ║  跳过    ║  耗时           ║${NC}"
    echo -e "${BOLD}╠══════════╬═══════════╬══════════╬══════════╬══════════╬═════════════════╣${NC}"

    for layer in L1 L2 L3 L4 L5; do
        local status="${LAYER_STATUS[$layer]:-skip}"
        local passed="${LAYER_PASSED[$layer]:-0}"
        local failed="${LAYER_FAILED[$layer]:-0}"
        local skipped="${LAYER_SKIPPED[$layer]:-0}"
        local duration="${LAYER_DURATION[$layer]:-0}"
        local dur_str
        dur_str=$(format_duration "$duration")

        # 状态显示（带颜色）
        local status_display
        case "$status" in
            pass) status_display="${GREEN}  PASS  ${NC}" ;;
            fail) status_display="${RED}  FAIL  ${NC}" ;;
            skip) status_display="${YELLOW}  SKIP  ${NC}" ;;
            *)    status_display="  $status  " ;;
        esac

        printf "║  %-7s ║ %b ║  %-7s ║  %-7s ║  %-7s ║  %-14s ║\n" \
            "$layer" "$status_display" "$passed" "$failed" "$skipped" "$dur_str"

        # 累加全局统计
        TOTAL_PASSED=$(( TOTAL_PASSED + passed ))
        TOTAL_FAILED=$(( TOTAL_FAILED + failed ))
        TOTAL_SKIPPED=$(( TOTAL_SKIPPED + skipped ))
    done

    echo -e "${BOLD}╠══════════╬═══════════╬══════════╬══════════╬══════════╬═════════════════╣${NC}"

    # 总计行
    local total_status_display
    if [[ $TOTAL_FAILED -gt 0 ]]; then
        total_status_display="${RED}  FAIL  ${NC}"
    else
        total_status_display="${GREEN}  PASS  ${NC}"
    fi

    printf "║  %-7s ║ %b ║  %-7s ║  %-7s ║  %-7s ║  %-14s ║\n" \
        "总计" "$total_status_display" "$TOTAL_PASSED" "$TOTAL_FAILED" "$TOTAL_SKIPPED" "$(format_duration $overall_duration)"

    echo -e "${BOLD}╚══════════╩═══════════╩══════════╩══════════╩══════════╩═════════════════╝${NC}"
    echo ""

    # 总结行
    local total_tests=$((TOTAL_PASSED + TOTAL_FAILED + TOTAL_SKIPPED))
    echo -e "  总计 ${BOLD}${total_tests}${NC} 项  |  ${GREEN}通过 ${TOTAL_PASSED}${NC}  |  ${RED}失败 ${TOTAL_FAILED}${NC}  |  ${YELLOW}跳过 ${TOTAL_SKIPPED}${NC}  |  耗时 $(format_duration $overall_duration)"
    echo ""

    if [[ $TOTAL_FAILED -gt 0 ]]; then
        echo -e "  ${RED}═══ 存在失败项，将以退出码 1 结束 ═══${NC}"
    else
        echo -e "  ${GREEN}═══ 全部通过！ ═══${NC}"
    fi
    echo ""
}

# ===========================================================================
# 生成 JSON 格式报告
# ===========================================================================
generate_json_report() {
    local overall_duration=$(( $(now_seconds) - OVERALL_START ))
    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date +"%Y-%m-%dT%H:%M:%S")
    local total_tests=$((TOTAL_PASSED + TOTAL_FAILED + TOTAL_SKIPPED))
    local overall_status="pass"
    [[ $TOTAL_FAILED -gt 0 ]] && overall_status="fail"

    mkdir -p "$REPORT_DIR"

    cat > "$REPORT_FILE" << EOF
{
  "report": "ZW-Insight All-Layer Test Report",
  "timestamp": "${timestamp}",
  "configuration": {
    "selectedLayers": "${SELECTED_LAYERS}",
    "failFast": ${FAIL_FAST}
  },
  "summary": {
    "total": ${total_tests},
    "passed": ${TOTAL_PASSED},
    "failed": ${TOTAL_FAILED},
    "skipped": ${TOTAL_SKIPPED},
    "duration": ${overall_duration},
    "status": "${overall_status}",
    "exitCode": $([ $TOTAL_FAILED -gt 0 ] && echo 1 || echo 0)
  },
  "layers": [
    {
      "name": "L1",
      "description": "单元测试",
      "status": "${LAYER_STATUS[L1]:-skip}",
      "passed": ${LAYER_PASSED[L1]:-0},
      "failed": ${LAYER_FAILED[L1]:-0},
      "skipped": ${LAYER_SKIPPED[L1]:-0},
      "duration": ${LAYER_DURATION[L1]:-0},
      "message": "${LAYER_MESSAGE[L1]:-未执行}"
    },
    {
      "name": "L2",
      "description": "集成测试",
      "status": "${LAYER_STATUS[L2]:-skip}",
      "passed": ${LAYER_PASSED[L2]:-0},
      "failed": ${LAYER_FAILED[L2]:-0},
      "skipped": ${LAYER_SKIPPED[L2]:-0},
      "duration": ${LAYER_DURATION[L2]:-0},
      "message": "${LAYER_MESSAGE[L2]:-未执行}"
    },
    {
      "name": "L3",
      "description": "API接口测试",
      "status": "${LAYER_STATUS[L3]:-skip}",
      "passed": ${LAYER_PASSED[L3]:-0},
      "failed": ${LAYER_FAILED[L3]:-0},
      "skipped": ${LAYER_SKIPPED[L3]:-0},
      "duration": ${LAYER_DURATION[L3]:-0},
      "message": "${LAYER_MESSAGE[L3]:-未执行}"
    },
    {
      "name": "L4",
      "description": "端到端业务流",
      "status": "${LAYER_STATUS[L4]:-skip}",
      "passed": ${LAYER_PASSED[L4]:-0},
      "failed": ${LAYER_FAILED[L4]:-0},
      "skipped": ${LAYER_SKIPPED[L4]:-0},
      "duration": ${LAYER_DURATION[L4]:-0},
      "message": "${LAYER_MESSAGE[L4]:-未执行}"
    },
    {
      "name": "L5",
      "description": "前端E2E",
      "status": "${LAYER_STATUS[L5]:-skip}",
      "passed": ${LAYER_PASSED[L5]:-0},
      "failed": ${LAYER_FAILED[L5]:-0},
      "skipped": ${LAYER_SKIPPED[L5]:-0},
      "duration": ${LAYER_DURATION[L5]:-0},
      "message": "${LAYER_MESSAGE[L5]:-未执行}"
    }
  ]
}
EOF

    info "JSON 报告已生成: $REPORT_FILE"
}

# ===========================================================================
# 主流程
# ===========================================================================
main() {
    parse_args "$@"

    OVERALL_START=$(now_seconds)

    echo ""
    divider
    header "  ZW-Insight 全层次测试编排"
    header "  选中层级: $SELECTED_LAYERS"
    header "  Fail-Fast: $FAIL_FAST"
    divider
    echo ""

    # 创建报告目录
    mkdir -p "$REPORT_DIR"

    # 初始化所有层级结果
    for layer in L1 L2 L3 L4 L5; do
        init_layer_result "$layer"
    done

    # 按 L1→L2→L3→L4→L5 顺序执行
    local abort=false
    for layer in L1 L2 L3 L4 L5; do
        # --fail-fast 中止后续层级
        if [[ "$abort" == "true" ]]; then
            LAYER_STATUS[$layer]="skip"
            LAYER_MESSAGE[$layer]="因 --fail-fast 跳过"
            LAYER_SKIPPED[$layer]=1
            continue
        fi

        # 未选中的层级直接跳过
        if ! is_layer_selected "$layer"; then
            continue
        fi

        echo ""
        divider

        case "$layer" in
            L1) run_l1 ;;
            L2) run_l2 ;;
            L3) run_l3 ;;
            L4) run_l4 ;;
            L5) run_l5 ;;
        esac

        # 输出层级结果
        local status="${LAYER_STATUS[$layer]}"
        local duration="${LAYER_DURATION[$layer]}"
        case "$status" in
            pass) info "$layer 通过 ($(format_duration "$duration"))" ;;
            fail) error "$layer 失败 ($(format_duration "$duration"))" ;;
            skip) warn "$layer 跳过" ;;
        esac

        # --fail-fast 逻辑：首个失败后标记中止
        if [[ "$FAIL_FAST" == "true" && "$status" == "fail" ]]; then
            warn "检测到失败且 --fail-fast 已启用，停止后续层级"
            abort=true
        fi
    done

    # 输出格式化汇总表格
    print_summary

    # 生成 JSON 报告
    generate_json_report

    # 退出码：有任何失败则非零
    if [[ $TOTAL_FAILED -gt 0 ]]; then
        exit 1
    else
        exit 0
    fi
}

# ===========================================================================
# 入口
# ===========================================================================
main "$@"
