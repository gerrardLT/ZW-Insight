#!/bin/bash
# 手动接管部署：CI 的 Deploy via SSH 在 vite "rendering chunks" 阶段因 5 分钟无输出被
# SSH 空闲断连（exit 255，连续两次），导致 compose stop 已执行但 up 未执行 → 服务停机。
# 本脚本用 nohup 在服务器后台执行 build + up，SSH 立即返回，不受空闲超时影响。
# 用法：nohup bash /root/zwi-deploy/manual-deploy.sh > /dev/null 2>&1 &
#       然后轮询 /tmp/zwi-manual-deploy.log
set -uo pipefail
LOG=/tmp/zwi-manual-deploy.log
cd /root/zw-insight/deploy || exit 2

{
  echo "=== START $(date '+%F %T') ==="
  echo "--- 资源基线 ---"
  free -m | head -2
  df -h / | tail -1
  nproc

  echo "=== [1/3] 清理 Flyway 失败记录（与 CI 步骤一致）==="
  docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight \
    -e "DELETE FROM flyway_schema_history WHERE success = 0;" 2>/dev/null || true

  echo "=== [2/3] build backend frontend ==="
  if docker compose -f docker-compose.deploy.yml build backend frontend; then
    echo "BUILD_OK $(date '+%F %T')"
    echo "=== [3/3] up -d backend frontend ==="
    docker compose -f docker-compose.deploy.yml up -d backend frontend
    echo "UP_OK $(date '+%F %T')"
    docker compose -f docker-compose.deploy.yml ps
  else
    echo "BUILD_FAILED $(date '+%F %T')"
    # 构建失败也要把服务拉起来（用现有镜像），避免长时间停机
    echo "--- 回退：用现有镜像启动 ---"
    docker compose -f docker-compose.deploy.yml up -d backend frontend || echo "FALLBACK_UP_FAILED"
    docker compose -f docker-compose.deploy.yml ps
  fi

  echo "=== 等待后端就绪（最多 180s）==="
  for i in $(seq 1 60); do
    code=$(curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:18080/api/v1/captcha/image 2>/dev/null)
    if [ "$code" = "200" ]; then
      echo "BACKEND_READY after ${i}x3s (HTTP $code)"
      break
    fi
    sleep 3
  done
  echo "final captcha code=$(curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:18080/api/v1/captcha/image 2>/dev/null)"

  echo "=== Flyway 已应用版本（最新 8 条）==="
  docker exec -i zwi-mysql mysql -uroot -pzwinsight123 -N -B zw_insight \
    -e "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 8;" 2>/dev/null

  echo "=== DONE $(date '+%F %T') ==="
} > "$LOG" 2>&1
