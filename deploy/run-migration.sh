#!/bin/bash
# ============================================================
# run-migration.sh — db-init 迁移 SQL 标准执行脚本
#
# 背景：45/47 号迁移手动执行时 mysql 客户端连接字符集非 utf8mb4，
# 中文文本双重编码成乱码写入生产（见 48_V2026_46__mojibake_fix.sql 订正）。
# 本脚本将连接字符集固化为 utf8mb4，杜绝同类复发。
#
# 用法（在服务器部署目录内执行）：
#   bash run-migration.sh <迁移SQL文件名>
#   例：bash run-migration.sh 48_V2026_46__mojibake_fix.sql
#
# 前置：zwi-mysql 容器运行中；脚本与 db-init 目录同级（deploy/）。
# ============================================================
set -euo pipefail

MYSQL_CT="${MYSQL_CT:-zwi-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PW="${MYSQL_PW:-zwinsight123}"
DB="${MYSQL_DATABASE:-zw_insight}"

if [ $# -lt 1 ]; then
    echo "用法: bash run-migration.sh <迁移SQL文件名>" >&2
    exit 1
fi

SQL_FILE="$1"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_PATH="$SCRIPT_DIR/db-init/$SQL_FILE"

if [ ! -f "$SQL_PATH" ]; then
    echo "错误: 找不到迁移文件 $SQL_PATH" >&2
    exit 1
fi

# 关键加固：--default-character-set=utf8mb4（中文文本防双重编码）
docker exec -i "$MYSQL_CT" mysql -u"$MYSQL_USER" -p"$MYSQL_PW" \
    --default-character-set=utf8mb4 "$DB" < "$SQL_PATH"

echo "迁移执行完成: $SQL_FILE"
