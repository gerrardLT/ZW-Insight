#!/usr/bin/env bash
#
# cleanup-test-data.sh — 手动清理 tenant_id=9999 的所有测试数据
#
# 用法:
#   bash tests/cleanup-test-data.sh          # 交互模式（需确认）
#   bash tests/cleanup-test-data.sh --force  # 跳过确认（CI 使用）
#
# 环境要求:
#   - 需要 docker exec 权限
#   - MySQL 容器名: zwi-mysql
#   - Redis 容器名: zwi-redis
#   - 数据库: zw_insight
#

set -euo pipefail

# ==================== 配置 ====================

MYSQL_CONTAINER="zwi-mysql"
REDIS_CONTAINER="zwi-redis"
DB_NAME="zw_insight"
DB_USER="root"
DB_PASS="zwinsight123"
TENANT_ID=9999
REDIS_PATTERN="test:t9999:*"

# ==================== 参数解析 ====================

FORCE=false
if [[ "${1:-}" == "--force" ]]; then
    FORCE=true
fi

# ==================== 颜色输出 ====================

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
info() { echo -e "${GREEN}[INFO]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ==================== 确认提示 ====================

echo ""
echo -e "${RED}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║  ⚠️  警告：即将清理 tenant_id=${TENANT_ID} 的所有测试数据    ║${NC}"
echo -e "${RED}║  数据库: ${DB_NAME}                                          ║${NC}"
echo -e "${RED}║  此操作不可逆！                                              ║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

if [[ "$FORCE" != "true" ]]; then
    read -p "确认清理 tenant_id=${TENANT_ID} 的所有测试数据？[y/N] " answer
    case "$answer" in
        [yY]|[yY][eE][sS]) ;;
        *)
            echo "已取消。"
            exit 0
            ;;
    esac
fi

# ==================== 表清单（拓扑逆序，子表在前） ====================
# 与 TestConstants.TABLE_DELETE_ORDER 核心表一致（30 张最常用业务表）

TABLES=(
    # Level 3+: 最深子表
    "biz_quotation_detail"
    "biz_deposit_return"
    "biz_retention_return"
    "biz_reimbursement_detail"

    # Level 2: 中间子表
    "biz_contract_detail"
    "biz_budget_detail"
    "biz_purchase_contract_detail"
    "biz_material_inbound_detail"
    "biz_material_outbound_detail"
    "biz_labor_settlement"
    "biz_subcontract_settlement"
    "biz_machine_settlement"
    "biz_machine_work_log"
    "biz_schedule_feedback"
    "biz_rectification"

    # Level 1: 引用项目根表的业务表
    "biz_project_member"
    "biz_construction_contract"
    "biz_budget"
    "biz_purchase_contract"
    "biz_inquiry"
    "biz_material_inbound"
    "biz_material_outbound"
    "biz_material_inventory"
    "biz_machine_contract"
    "biz_machine_ledger"
    "biz_subcontract"
    "biz_schedule_plan"
    "biz_construction_log"
    "biz_inspection"
    "biz_payment_apply"

    # Level 0: 根表
    "biz_project"
)

# ==================== 执行 MySQL 清理 ====================

info "开始清理 MySQL 数据（共 ${#TABLES[@]} 张表）..."
echo ""

success_count=0
fail_count=0
skip_count=0

for table in "${TABLES[@]}"; do
    printf "  清理 %-45s" "${table}..."

    output=$(docker exec "$MYSQL_CONTAINER" mysql \
        -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" \
        -e "DELETE FROM \`${table}\` WHERE tenant_id = ${TENANT_ID};" 2>&1) && {
        # 成功
        echo -e " ${GREEN}✓${NC}"
        ((success_count++))
    } || {
        # 失败（表不存在或其他错误）
        if echo "$output" | grep -qi "doesn't exist"; then
            echo -e " ${YELLOW}SKIP (表不存在)${NC}"
            ((skip_count++))
        else
            echo -e " ${RED}FAIL${NC}"
            warn "  → $output"
            ((fail_count++))
        fi
    }
done

echo ""
info "MySQL 清理完成: 成功=${success_count}, 跳过=${skip_count}, 失败=${fail_count}"

# ==================== 执行 Redis 清理 ====================

echo ""
info "开始清理 Redis 键（匹配: ${REDIS_PATTERN}）..."

redis_keys=$(docker exec "$REDIS_CONTAINER" redis-cli KEYS "$REDIS_PATTERN" 2>/dev/null || true)

if [[ -z "$redis_keys" ]]; then
    info "未找到匹配的 Redis 键，跳过。"
else
    key_count=$(echo "$redis_keys" | wc -l)
    echo "$redis_keys" | xargs -r docker exec -i "$REDIS_CONTAINER" redis-cli DEL > /dev/null 2>&1 && {
        info "已清理 ${key_count} 个 Redis 键。"
    } || {
        warn "Redis 键清理过程中出现错误。"
    }
fi

# ==================== 汇总输出 ====================

echo ""
echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  清理完成！${NC}"
echo -e "${GREEN}  MySQL: ${success_count}/${#TABLES[@]} 张表清理成功${NC}"
if [[ $skip_count -gt 0 ]]; then
    echo -e "${YELLOW}  跳过: ${skip_count} 张表（不存在）${NC}"
fi
if [[ $fail_count -gt 0 ]]; then
    echo -e "${RED}  失败: ${fail_count} 张表${NC}"
fi
echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo ""
echo "提示: 建议执行 chmod +x tests/cleanup-test-data.sh 添加执行权限"
