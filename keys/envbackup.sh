#!/usr/bin/env bash
# 任务 3.1：环境标识确认（联调/非生产）+ 备份前置（mysqldump 导出，显式 utf8mb4）
# 口令仅以变量引用，不回显明文
PW=zwinsight123
DB=zw_insight
BK=/root/zwi-deploy/backups

echo "===A_COMPOSE_PROJECT_LABELS==="
docker inspect -f '{{ index .Config.Labels "com.docker.compose.project"}}' zwi-backend zwi-mysql zwi-redis 2>/dev/null

echo "===B_BACKEND_SPRING_PROFILE==="
docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' zwi-backend 2>/dev/null | grep -iE 'SPRING_PROFILES_ACTIVE|PROFILE' | sed 's/PASSWORD=.*/PASSWORD=<redacted>/I'

echo "===C_COMPOSE_FILE_HINT==="
docker inspect -f '{{ index .Config.Labels "com.docker.compose.project.config_files"}}' zwi-backend 2>/dev/null

echo "===D_EXISTING_BACKUPS==="
ls -lh "$BK" 2>/dev/null || echo "no_backup_dir_yet"

echo "===E_DO_BACKUP==="
mkdir -p "$BK"
TS=$(date +%Y%m%d-%H%M%S)
OUT="$BK/zw_insight-$TS.sql.gz"
if docker exec -i zwi-mysql sh -c "mysqldump -uroot -p$PW --default-character-set=utf8mb4 --single-transaction --routines --triggers $DB" 2>/dev/null | gzip > "$OUT"; then
  echo "BACKUP_FILE=$OUT"
  ls -lh "$OUT"
  echo "GZIP_INTEGRITY=$(gzip -t "$OUT" && echo OK || echo FAIL)"
else
  echo "BACKUP_FAILED"
fi

echo "===F_BACKUP_ROWCHECK==="
# 校验备份内含关键种子表 INSERT（不回显数据内容，只数行）
echo "sys_menu_inserts=$(zcat "$OUT" 2>/dev/null | grep -c 'INSERT INTO `sys_menu`')"
echo "sys_dict_inserts=$(zcat "$OUT" 2>/dev/null | grep -c 'INSERT INTO `sys_dict`')"

echo "===DONE==="
