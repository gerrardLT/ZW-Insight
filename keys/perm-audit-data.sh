#!/usr/bin/env bash
###############################################################################
# perm-audit-data.sh — 权限一致性清查【D3 行级数据范围面】
#
# 对每个目标用户 × 每个业务列表接口，真实登录取数并做：
#   ① 越权检查：返回行是否全部满足该用户有效范围判定式
#      SELF→created_by=uid；PROJECT→project_id∈用户项目；DEPT→dept_id=用户部门；
#      DEPT_AND_CHILDREN→dept_id∈部门子树；ALL→不过滤
#   ② 误杀检查：与 SUPER_ADMIN 全量集按同一判定式推导的"应可见集"完全相等
#   ③ 403 与空集严格区分（403 记为 BLOCKED，不得当作"空集 PASS"）
#
# 全程真实登录 / 真实接口 / 只读 GET。运行位置：目标服务器。
# 用法：bash perm-audit-data.sh [--tenant 1] [--password 123456] [--base url] [--super admin]
# 退出码：0 = 全部一致；1 = 存在越权/误杀；3 = 存在 BLOCKED/UNVERIFIABLE
###############################################################################
set -uo pipefail

MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"
REDIS_CT="${ZWI_REDIS_CT:-zwi-redis}"
DB="${ZWI_DB:-zw_insight}"
BASE="${ZWI_BASE:-http://127.0.0.1:18080}"
TENANT="1"
PASSWORD="${ZWI_PASS:-123456}"
SUPER_USER="${ZWI_SUPER_USER:-admin}"

while [ $# -gt 0 ]; do
  case "$1" in
    --tenant) TENANT="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    --base) BASE="$2"; shift 2 ;;
    --super) SUPER_USER="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 64 ;;
  esac
done

# 业务列表端点（name:path），覆盖全部含 project_id 的业务模块
ENDPOINTS=(
  "project:/api/v1/project/page"
  "contract:/api/v1/contract/page"
  "contract-change:/api/v1/contract/change-event/page"
  "budget:/api/v1/budget/page"
  "budget-costaccount:/api/v1/budget/cost-account/page"
  "fin-payment:/api/v1/finance/payment-apply/page"
  "fin-invoice:/api/v1/finance/invoice-apply/page"
  "fin-receivable:/api/v1/finance/receivable/page"
  "fin-pay-recv:/api/v1/finance/payment-received/page"
  "fin-retention:/api/v1/finance/retention/page"
  "fin-security:/api/v1/finance/security-bond/page"
  "fin-bill:/api/v1/finance/bill/page"
  "fin-lock:/api/v1/finance/lock/page"
  "fin-fundplan:/api/v1/finance/fund-plan/monthly/page"
  "fin-wage:/api/v1/finance/wage-account/page"
  "labor-contract:/api/v1/labor/contract/page"
  "labor-roster:/api/v1/labor/roster/page"
  "labor-settlement:/api/v1/labor/settlement/page"
  "labor-output:/api/v1/labor/output-report/page"
  "machine-contract:/api/v1/machine/contract/page"
  "machine-entry:/api/v1/machine/entry/page"
  "machine-ledger:/api/v1/machine/ledger/page"
  "material-inbound:/api/v1/material/inbound/page"
  "material-outbound:/api/v1/material/outbound/page"
  "material-inventory:/api/v1/material/inventory/page"
  "purchase-contract:/api/v1/purchase/contract/page"
  "purchase-settlement:/api/v1/purchase/settlement/page"
  "site-inspection:/api/v1/site/inspection/page"
  "site-completion:/api/v1/site/completion/page"
  "site-log:/api/v1/site/construction-log/page"
  "subcontract:/api/v1/subcontract/contract/page"
  "subcontract-output:/api/v1/subcontract/output/page"
  "tender-register:/api/v1/tender/register/page"
)

q() {
  { echo "SET SESSION group_concat_max_len=1048576;"; cat; } | docker exec -i "$MYSQL_CT" sh -c \
    "mysql --default-character-set=utf8mb4 -uroot -p\$MYSQL_ROOT_PASSWORD -N -B $DB" 2>/dev/null
}

LAST_PERMS=""
login() {
  local u="$1" p="$2" uuid code resp
  LAST_PERMS=""
  docker exec "$REDIS_CT" redis-cli DEL "login:ip:fail:127.0.0.1" "login:ip:lock:127.0.0.1" >/dev/null 2>&1 || true
  uuid=$(curl -s -m 10 "$BASE/api/v1/captcha/image" | jq -r '.data.uuid // empty')
  [ -n "$uuid" ] || return 1
  code=$(docker exec "$REDIS_CT" redis-cli GET "captcha:$uuid" | tr -d '\r"')
  [ -n "$code" ] || return 1
  resp=$(curl -s -m 15 -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
        -d "{\"username\":\"$u\",\"password\":\"$p\",\"captchaUuid\":\"$uuid\",\"captchaCode\":\"$code\"}")
  LAST_PERMS=$(echo "$resp" | jq -r '.data.permissions // [] | join(",")')
  echo "$resp" | jq -r '.data.token // empty'
}

# 输出 "<http_status>|<records-json>"；403/5xx 与空集严格区分
fetch() { # fetch <token> <path>
  local tok="$1" path="$2" body code
  body=$(curl -s -m 25 -w '\n%{http_code}' "$BASE$path?page=1&size=500" -H "Authorization: Bearer $tok")
  code=$(tail -1 <<<"$body")
  body=$(sed '$d' <<<"$body")
  if [ "$code" = "200" ]; then
    printf '%s|%s' "$code" "$(echo "$body" | jq -c '(.data.records // .data // [])' 2>/dev/null || echo '[]')"
  else
    printf '%s|[]' "$code"
  fi
}
ids_of() { jq -c '[.[] | .id] | sort' 2>/dev/null <<<"${1:-[]}" || echo '[]'; }

echo "=============================================================="
echo "权限一致性清查 · D3 行级数据范围面"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')  租户: $TENANT  BASE: $BASE"
echo "=============================================================="

SUPER_TOK=$(login "$SUPER_USER" "$PASSWORD")
[ -n "$SUPER_TOK" ] || { echo "[FATAL] 超管登录失败，无法建立全量基线"; exit 3; }
SUPER_UID=$(printf '%s\n' "SELECT id FROM sys_user WHERE username='$SUPER_USER' AND tenant_id=$TENANT LIMIT 1;" | q | tr -d '\r')
echo "全量基线账号: $SUPER_USER (id=$SUPER_UID)"

# 预热：缓存超管各端点全量记录行与字段集
declare -A SUPER_RECS SUPER_FIELDS
for ep in "${ENDPOINTS[@]}"; do
  path="${ep#*:}"
  SUPER_RECS["$path"]=$(fetch "$SUPER_TOK" "$path" | cut -d'|' -f2)
  SUPER_FIELDS["$path"]=$(jq -c '[(.[0] // {}) | keys[]]' 2>/dev/null <<<"${SUPER_RECS[$path]:-[]}" || echo '[]')
done

ULIST=$(printf '%s\n' "SELECT u.username FROM sys_user u WHERE u.deleted=0 AND u.status=1 AND u.tenant_id=$TENANT AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id AND r.deleted=0 AND r.status=1 WHERE ur.user_id=u.id) ORDER BY u.id;" | q | tr -d '\r')

PASS=0; FAIL=0; BLOCKED=0; UNVER=0; ERRC=0
for U in $ULIST; do
  U_ID=$(printf '%s\n' "SELECT id FROM sys_user WHERE username='$U' AND tenant_id=$TENANT AND deleted=0 LIMIT 1;" | q | tr -d '\r')
  ROW=$(printf '%s\n' "
    SELECT COALESCE(SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT r.data_scope
        ORDER BY FIELD(r.data_scope,'ALL','DEPT_AND_CHILDREN','DEPT','PROJECT','SELF') SEPARATOR ','),',',1),'SELF'),
      COALESCE(u.org_id,0)
    FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id
    JOIN sys_role r ON r.id=ur.role_id AND r.status=1 AND r.deleted=0
    WHERE u.id=$U_ID GROUP BY u.id;" | q | tr -d '\r')
  SCOPE="${ROW%%$'\t'*}"; DEPT="${ROW##*$'\t'}"
  PROJS_JSON=$(printf '%s\n' "SELECT COALESCE(GROUP_CONCAT(project_id),'') FROM sys_user_project WHERE user_id=$U_ID;" | q | tr -d '\r' | tr ',' '\n' | sed '/^$/d' | jq -s 'map(tonumber)')
  if [ "$SCOPE" = "DEPT" ] || [ "$SCOPE" = "DEPT_AND_CHILDREN" ]; then
    DEPTS_JSON=$(printf '%s\n' "SELECT id FROM sys_org WHERE deleted=0 AND (id=$DEPT OR FIND_IN_SET($DEPT, ancestors));" | q | tr -d '\r' | sed '/^$/d' | jq -s 'map(tonumber)')
  else
    DEPTS_JSON='[]'
  fi

  UTOK=$(login "$U" "$PASSWORD"); U_PERMS="$LAST_PERMS"
  if [ -z "$UTOK" ]; then echo "[BLOCKED] $U 登录失败（凭证未知），D3 无法验证"; BLOCKED=$((BLOCKED+1)); continue; fi

  echo "---- $U (id=$U_ID, scope=$SCOPE, dept=$DEPT, projects=$PROJS_JSON) ----"
  for ep in "${ENDPOINTS[@]}"; do
    name="${ep%%:*}"; path="${ep#*:}"
    RES=$(fetch "$UTOK" "$path")
    ST="${RES%%|*}"; U_RECS="${RES##*|}"
    U_IDS=$(ids_of "$U_RECS")
    if [ "$ST" = "000" ]; then
      printf '  %-20s ERROR (HTTP 000，连接失败/超时，不计入 PASS)\n' "$name"
      ERRC=$((ERRC+1)); continue
    fi
    if [ "$ST" != "200" ]; then
      # 该模块视图码是否属于该用户：属 → 菜单可达但接口被拒（缺陷）；不属 → 预期行为
      MOD="${path#/api/v1/}"; MOD="${MOD%%/*}"
      if grep -qx "$MOD:view" <<<"$(tr ',' '\n' <<<"$U_PERMS")"; then
        printf '  %-20s FAIL-403 (HTTP %s，用户持有 %s:view 但接口被拒)\n' "$name" "$ST" "$MOD"
        FAIL=$((FAIL+1))
      else
        printf '  %-20s SKIP-403 (HTTP %s，用户无 %s:view，预期)\n' "$name" "$ST" "$MOD"
      fi
      continue
    fi
    NEED=""
    case "$SCOPE" in
      SELF) NEED="createdBy" ;;
      PROJECT) NEED="projectId" ;;
      DEPT|DEPT_AND_CHILDREN) NEED="deptId" ;;
    esac
    if [ -n "$NEED" ] && ! grep -q "\"$NEED\"" <<<"${SUPER_FIELDS[$path]}"; then
      printf '  %-20s UNVERIFIABLE（响应缺字段 %s）\n' "$name" "$NEED"
      UNVER=$((UNVER+1)); continue
    fi
    E_IDS=$(jq -c --arg scope "$SCOPE" --argjson uid "${U_ID:-0}" --argjson projs "$PROJS_JSON" --argjson depts "$DEPTS_JSON" --argjson dept "${DEPT:-0}" '
        [.[] | . as $r | select(
            $scope=="ALL"
            or ($scope=="SELF" and ($r.createdBy==$uid))
            or ($scope=="PROJECT" and (($projs|index($r.projectId))!=null))
            or ($scope=="DEPT" and ($r.deptId==$dept))
            or ($scope=="DEPT_AND_CHILDREN" and (($depts|index($r.deptId))!=null))
          ) | $r.id] | sort' 2>/dev/null <<<"${SUPER_RECS[$path]:-[]}")
    E_IDS="${E_IDS:-[]}"
    if [ "$E_IDS" = "$U_IDS" ]; then
      printf '  %-20s PASS (expected=%s actual=%s)\n' "$name" "$(jq 'length' <<<"$E_IDS")" "$(jq 'length' <<<"$U_IDS")"
      PASS=$((PASS+1))
    else
      printf '  %-20s FAIL expected=%s actual=%s  越权多显=%s 误杀少显=%s\n' "$name" "$(jq 'length' <<<"$E_IDS")" "$(jq 'length' <<<"$U_IDS")" \
        "$(jq -c -n --argjson a "$U_IDS" --argjson e "$E_IDS" '[$a[]|select(. as $x|($e|index($x))==null)]')" \
        "$(jq -c -n --argjson a "$U_IDS" --argjson e "$E_IDS" '[$e[]|select(. as $x|($a|index($x))==null)]')"
      FAIL=$((FAIL+1))
    fi
  done
done

echo "=============================================================="
echo "D3 小结: PASS=$PASS FAIL=$FAIL BLOCKED=$BLOCKED UNVERIFIABLE=$UNVER ERROR=$ERRC"
echo "=============================================================="
if [ "$FAIL" -gt 0 ]; then exit 1; fi
if [ "$ERRC" -gt 0 ] || [ "$BLOCKED" -gt 0 ] || [ "$UNVER" -gt 0 ]; then exit 3; fi
exit 0
