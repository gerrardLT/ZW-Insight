#!/usr/bin/env bash
###############################################################################
# affected-modules.sh — 增量测试模块检测器
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BASE_REV="${1:-HEAD~1}"
OUTPUT_FORMAT="${2:-text}"

declare -A MODULE_MAP=(
    ["zw-app"]="zw-app" ["zw-archive"]="zw-archive" ["zw-basedata"]="zw-basedata"
    ["zw-budget"]="zw-budget" ["zw-common"]="zw-common" ["zw-contract"]="zw-contract"
    ["zw-dashboard"]="zw-dashboard" ["zw-file"]="zw-file" ["zw-finance"]="zw-finance"
    ["zw-hr"]="zw-hr" ["zw-labor"]="zw-labor" ["zw-machine"]="zw-machine"
    ["zw-material"]="zw-material" ["zw-message"]="zw-message" ["zw-project"]="zw-project"
    ["zw-purchase"]="zw-purchase" ["zw-security"]="zw-security" ["zw-site"]="zw-site"
    ["zw-subcontract"]="zw-subcontract" ["zw-system"]="zw-system" ["zw-tender"]="zw-tender"
    ["zw-workflow"]="zw-workflow"
)

echo "=== ZW-Insight 增量测试模块检测器 ==="
echo "基准 commit: $BASE_REV"

changed_files=$(git -C "$PROJECT_ROOT" diff-tree --no-commit-id --name-only -r "$BASE_REV" 2>/dev/null)

[ -z "$changed_files" ] && echo "" && exit 0

modules=()
while IFS= read -r file; do
    [[ ! "$file" =~ ^zw-insight-server/ ]] && continue
    module_path="${file#zw-insight-server/}"
    [[ "$module_path" == "pom.xml" || "$module_path" =~ /target/ ]] && continue
    
    for prefix in "${!MODULE_MAP[@]}"; do
        if [[ "$file" == "$prefix/"* ]]; then
            detected_module="${MODULE_MAP[$prefix]}"
            [[ ${modules[*]:-} =~ "$detected_module" ]] && continue
            
            if [ "$detected_module" == "zw-common" ]; then
                echo "检测到 zw-common 变更，触发全量测试"
                modules=($(printf '%s\n' "${!MODULE_MAP[@]}" | sort))
                break 2
            fi
            
            modules+=("$detected_module")
        fi
    done
done <<< "$changed_files"

if [ ${#modules[@]} -eq 0 ]; then
    echo ""
    exit 0
fi

IFS=$'\n' sorted_modules=($(sort <<<"${modules[*]}")); unset IFS

if [ "$OUTPUT_FORMAT" == "json" ]; then
    echo "{"
    echo "  \"base\": \"$BASE_REV\","
    echo "  \"count\": ${#sorted_modules[@]},"
    echo "  \"modules\": [$(printf '"%s",' "${sorted_modules[@]}" | sed 's/,$//')]"
    echo "}"
else
    echo "${sorted_modules[*]}" | tr ' ' ','
fi
