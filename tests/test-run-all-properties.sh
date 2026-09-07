#!/usr/bin/env bash
###############################################################################
# test-run-all-properties.sh — Property tests for run-all-tests.sh orchestration
#
# Property 9: --layers selects only specified layers; --fail-fast stops after first failure
# Property 7: JSON report invariant: summary.total == passed + failed + skipped
#
# Seam-based testing (tests the REAL main(), not a re-implementation):
#   1. source run-all-tests.sh — source-guard prevents auto-invoking main
#   2. override run_l1..run_l5 with env-var-driven mocks (no real mvn/playwright)
#   3. invoke the REAL main() inside a subshell (its `exit` won't kill this harness),
#      redirecting REPORT_FILE to a temp path
#   4. assert against the generated JSON report — the true observable artifact
#
# This validates the production parse_args / is_layer_selected / fail-fast abort /
# generate_json_report logic end-to-end without side effects.
###############################################################################
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET="$ROOT_DIR/tests/run-all-tests.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

TEST_COUNT=0
PASS_COUNT=0
FAIL_COUNT=0

# ---------------------------------------------------------------------------
# Assertions
# ---------------------------------------------------------------------------
assert_eq() {
    local expected="$1" actual="$2" label="$3"
    TEST_COUNT=$((TEST_COUNT + 1))
    if [[ "$expected" == "$actual" ]]; then
        PASS_COUNT=$((PASS_COUNT + 1)); echo "  PASS [$TEST_COUNT] $label"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1)); echo "  FAIL [$TEST_COUNT] $label (期望=$expected 实际=$actual)"
    fi
}

# assert_contains <haystack> <needle> <label> — ASCII-substring check (encoding-robust)
assert_contains() {
    local haystack="$1" needle="$2" label="$3"
    TEST_COUNT=$((TEST_COUNT + 1))
    if [[ "$haystack" == *"$needle"* ]]; then
        PASS_COUNT=$((PASS_COUNT + 1)); echo "  PASS [$TEST_COUNT] $label"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1)); echo "  FAIL [$TEST_COUNT] $label (期望包含=$needle 实际=$haystack)"
    fi
}

# assert_not_contains <haystack> <needle> <label>
assert_not_contains() {
    local haystack="$1" needle="$2" label="$3"
    TEST_COUNT=$((TEST_COUNT + 1))
    if [[ "$haystack" != *"$needle"* ]]; then
        PASS_COUNT=$((PASS_COUNT + 1)); echo "  PASS [$TEST_COUNT] $label"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1)); echo "  FAIL [$TEST_COUNT] $label (不应包含=$needle 实际=$haystack)"
    fi
}

# Extract a layer's field from the JSON report: json_field <file> <layerName> <field>
# Robust interpreter fallback: python3 (CI/Linux) → python (Windows) → node
# Path portability: on git-bash (MSYS) the temp path /tmp/... is invisible to native
# Windows python.exe/node.exe, so convert via `cygpath -m` (→ C:/...). On Linux cygpath
# is absent and the path is already native.
json_field() {
    local file="$1" layer="$2" field="$3"
    local prog=""
    if command -v python3 >/dev/null 2>&1 && python3 -c "print(1)" >/dev/null 2>&1; then
        prog="python3"
    elif command -v python >/dev/null 2>&1 && python -c "print(1)" >/dev/null 2>&1; then
        prog="python"
    fi
    # Convert MSYS path to a host-native path the interpreter can open
    local native_file="$file"
    if command -v cygpath >/dev/null 2>&1; then
        native_file="$(cygpath -m "$file" 2>/dev/null || echo "$file")"
    fi
    if [[ -n "$prog" ]]; then
        PYTHONIOENCODING=utf-8 "$prog" -c "
import json
d=json.load(open(r'$native_file',encoding='utf-8'))
if '$layer'=='summary':
    print(d['summary'].get('$field',''))
else:
    for L in d['layers']:
        if L['name']=='$layer':
            print(L.get('$field',''))
            break
" 2>/dev/null
    elif command -v node >/dev/null 2>&1; then
        node -e "
const d=require('$native_file');
if('$layer'==='summary'){console.log(d.summary['$field']??'');}
else{const L=d.layers.find(x=>x.name==='$layer');console.log(L?L['$field']??'':'');}
" 2>/dev/null
    else
        echo ""
    fi
}

# ---------------------------------------------------------------------------
# Source the target script (guard prevents main auto-run)
# ---------------------------------------------------------------------------
# shellcheck disable=SC1090
source "$TARGET"

# ---------------------------------------------------------------------------
# Env-var-driven mock layer functions: MOCK_<LAYER>_RESULT = pass|fail|skip
# Distinguishable from real skip (未执行): mock sets message "mock:<result>"
# ---------------------------------------------------------------------------
_mock_layer() {
    local layer="$1"
    local var="MOCK_${layer}_RESULT"
    local result="${!var:-pass}"
    LAYER_STATUS[$layer]="$result"
    case "$result" in
        pass) LAYER_PASSED[$layer]=10; LAYER_FAILED[$layer]=0; LAYER_SKIPPED[$layer]=0 ;;
        fail) LAYER_PASSED[$layer]=3;  LAYER_FAILED[$layer]=2; LAYER_SKIPPED[$layer]=1 ;;
        skip) LAYER_PASSED[$layer]=0;  LAYER_FAILED[$layer]=0; LAYER_SKIPPED[$layer]=1 ;;
    esac
    LAYER_DURATION[$layer]=1
    LAYER_MESSAGE[$layer]="mock:$result"
}
run_l1() { _mock_layer L1; }
run_l2() { _mock_layer L2; }
run_l3() { _mock_layer L3; }
run_l4() { _mock_layer L4; }
run_l5() { _mock_layer L5; }

# ---------------------------------------------------------------------------
# Scenario runner: invokes REAL main() in a subshell, returns temp report path
# Usage: report=$(run_main <report_path> <args...>)
# ---------------------------------------------------------------------------
run_main() {
    local report_path="$1"; shift
    (
        REPORT_FILE="$report_path"
        REPORT_DIR="$(dirname "$report_path")"
        # Reset globals main relies on (they persist across subshell from parent source)
        SELECTED_LAYERS="L1,L2,L3,L4,L5"
        FAIL_FAST=false
        TOTAL_PASSED=0; TOTAL_FAILED=0; TOTAL_SKIPPED=0
        for l in L1 L2 L3 L4 L5; do init_layer_result "$l"; done
        main "$@" >/dev/null 2>&1
    )
}

##############################################################################
echo ""
echo "=== Property 9: Layer Selection (--layers filters execution) ==="

# Scenario 9A: --layers=L1,L3 → L1/L3 run (mock:pass), L2/L4/L5 never called (未执行)
R="$TMP_DIR/9a.json"
MOCK_L1_RESULT=pass MOCK_L3_RESULT=pass run_main "$R" --layers=L1,L3
assert_eq "pass"   "$(json_field "$R" L1 status)"  "9A: L1 executed (selected)"
assert_eq "mock:pass" "$(json_field "$R" L1 message)" "9A: L1 ran the mock (not init-skip)"
assert_eq "skip"   "$(json_field "$R" L2 status)"  "9A: L2 skipped (not selected)"
# L2 was never selected → run_l2 never called → message stays init value (NOT a "mock:*" value)
assert_not_contains "$(json_field "$R" L2 message)" "mock" "9A: L2 never invoked run_l2 (message is init值, not mock)"
assert_eq "pass"   "$(json_field "$R" L3 status)"  "9A: L3 executed (selected)"
assert_eq "skip"   "$(json_field "$R" L4 status)"  "9A: L4 skipped (not selected)"
assert_eq "skip"   "$(json_field "$R" L5 status)"  "9A: L5 skipped (not selected)"

# Scenario 9B: --layers=L5 only
R="$TMP_DIR/9b.json"
MOCK_L5_RESULT=pass run_main "$R" --layers=L5
assert_eq "skip"      "$(json_field "$R" L1 status)" "9B: L1 skipped"
assert_eq "skip"      "$(json_field "$R" L2 status)" "9B: L2 skipped"
assert_eq "skip"      "$(json_field "$R" L3 status)" "9B: L3 skipped"
assert_eq "skip"      "$(json_field "$R" L4 status)" "9B: L4 skipped"
assert_eq "mock:pass" "$(json_field "$R" L5 message)" "9B: L5 executed"

# Scenario 9C: default (no --layers) runs all 5
R="$TMP_DIR/9c.json"
MOCK_L1_RESULT=pass MOCK_L2_RESULT=pass MOCK_L3_RESULT=pass MOCK_L4_RESULT=pass MOCK_L5_RESULT=pass run_main "$R"
assert_eq "mock:pass" "$(json_field "$R" L1 message)" "9C: default runs L1"
assert_eq "mock:pass" "$(json_field "$R" L3 message)" "9C: default runs L3"
assert_eq "mock:pass" "$(json_field "$R" L5 message)" "9C: default runs L5"

# Scenario 9D: case-insensitive layer matching (--layers=l1,l3 lowercase)
R="$TMP_DIR/9d.json"
MOCK_L1_RESULT=pass MOCK_L3_RESULT=pass run_main "$R" --layers=l1,l3
assert_eq "mock:pass" "$(json_field "$R" L1 message)" "9D: lowercase 'l1' matched (case-insensitive)"
assert_eq "mock:pass" "$(json_field "$R" L3 message)" "9D: lowercase 'l3' matched"

##############################################################################
echo ""
echo "=== Property 9: Fail-Fast Semantics (--fail-fast stops after first failure) ==="

# Scenario 9E: L1 fails + --fail-fast → L2/L3 skipped with fail-fast message
R="$TMP_DIR/9e.json"
MOCK_L1_RESULT=fail MOCK_L2_RESULT=pass MOCK_L3_RESULT=pass run_main "$R" --layers=L1,L2,L3 --fail-fast
assert_eq "fail" "$(json_field "$R" L1 status)" "9E: L1 failed"
assert_eq "skip" "$(json_field "$R" L2 status)" "9E: L2 skipped due to fail-fast"
# fail-fast abort sets message "因 --fail-fast 跳过"; assert on ASCII-stable substring (encoding-robust)
assert_contains "$(json_field "$R" L2 message)" "--fail-fast" "9E: L2 message confirms fail-fast abort"
assert_eq "skip" "$(json_field "$R" L3 status)" "9E: L3 skipped due to fail-fast"
assert_contains "$(json_field "$R" L3 message)" "--fail-fast" "9E: L3 message confirms fail-fast abort"

# Scenario 9F: L1 fails WITHOUT --fail-fast → L2/L3 STILL execute (control case)
R="$TMP_DIR/9f.json"
MOCK_L1_RESULT=fail MOCK_L2_RESULT=pass MOCK_L3_RESULT=pass run_main "$R" --layers=L1,L2,L3
assert_eq "fail"      "$(json_field "$R" L1 status)"  "9F: L1 failed"
assert_eq "mock:pass" "$(json_field "$R" L2 message)" "9F: L2 STILL ran (no fail-fast)"
assert_eq "mock:pass" "$(json_field "$R" L3 message)" "9F: L3 STILL ran (no fail-fast)"

# Scenario 9G: middle layer fails + fail-fast → only layers after it skipped
R="$TMP_DIR/9g.json"
MOCK_L1_RESULT=pass MOCK_L2_RESULT=fail MOCK_L3_RESULT=pass run_main "$R" --layers=L1,L2,L3 --fail-fast
assert_eq "mock:pass" "$(json_field "$R" L1 message)" "9G: L1 ran before failure"
assert_eq "fail"      "$(json_field "$R" L2 status)"  "9G: L2 failed"
assert_eq "skip"      "$(json_field "$R" L3 status)"  "9G: L3 skipped after L2 failure"

##############################################################################
echo ""
echo "=== Property 7: Report Numeric Invariant (total == passed+failed+skipped) ==="

# Scenario 7A: mixed results — verify summary arithmetic
R="$TMP_DIR/7a.json"
MOCK_L1_RESULT=pass MOCK_L2_RESULT=fail MOCK_L3_RESULT=skip MOCK_L4_RESULT=pass MOCK_L5_RESULT=fail \
    run_main "$R"
s_total="$(json_field "$R" summary total)"
s_passed="$(json_field "$R" summary passed)"
s_failed="$(json_field "$R" summary failed)"
s_skipped="$(json_field "$R" summary skipped)"
expected_total=$(( s_passed + s_failed + s_skipped ))
assert_eq "$expected_total" "$s_total" "7A: summary.total ($s_total) == passed($s_passed)+failed($s_failed)+skipped($s_skipped)"

# Scenario 7B: per-layer invariant — each layer's counts sum consistently with its status
# For a 'pass' layer, failed must be 0; for a 'fail' layer, failed must be > 0
R="$TMP_DIR/7b.json"
MOCK_L1_RESULT=pass MOCK_L2_RESULT=fail run_main "$R" --layers=L1,L2
l1_failed="$(json_field "$R" L1 failed)"
l2_failed="$(json_field "$R" L2 failed)"
assert_eq "0" "$l1_failed" "7B: pass layer L1 has failed=0"
TEST_COUNT=$((TEST_COUNT + 1))
if [[ "$l2_failed" -gt 0 ]]; then
    PASS_COUNT=$((PASS_COUNT + 1)); echo "  PASS [$TEST_COUNT] 7B: fail layer L2 has failed>0 ($l2_failed)"
else
    FAIL_COUNT=$((FAIL_COUNT + 1)); echo "  FAIL [$TEST_COUNT] 7B: fail layer L2 should have failed>0 (got $l2_failed)"
fi

# Scenario 7C: overall status reflects failures
R="$TMP_DIR/7c.json"
MOCK_L1_RESULT=fail run_main "$R" --layers=L1
assert_eq "fail" "$(json_field "$R" summary status)" "7C: any layer failure → summary.status=fail"
assert_eq "1" "$(json_field "$R" summary exitCode)" "7C: failure → summary.exitCode=1"

R="$TMP_DIR/7d.json"
MOCK_L1_RESULT=pass run_main "$R" --layers=L1
assert_eq "pass" "$(json_field "$R" summary status)" "7C: all pass → summary.status=pass"
assert_eq "0" "$(json_field "$R" summary exitCode)" "7C: all pass → summary.exitCode=0"

##############################################################################
echo ""
echo "======================================================="
echo "Property Test Summary (run-all-tests.sh orchestration)"
echo "======================================================="
echo "通过: $PASS_COUNT"
echo "失败: $FAIL_COUNT"
echo "总计: $TEST_COUNT"
echo "======================================================="

[[ $FAIL_COUNT -eq 0 ]] && exit 0 || exit 1
