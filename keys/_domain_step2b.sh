#!/usr/bin/env bash
# _domain_step2b.sh — 阶段2b：换 ZeroSSL CA 重试申请（绕过 Let's Encrypt 速率限制）
# 成功则装证书 + 443 + 强制跳转；失败保持 HTTP，不影响可用性
set -uo pipefail

CONF=/www/server/panel/vhost/nginx/zy.cn-zwny.com.conf
WEBROOT=/www/server/panel/vhost/nginx/well-known
CERTDIR=/www/server/panel/vhost/nginx/ssl/zy.cn-zwny.com
ACME="$HOME/.acme.sh/acme.sh"
mkdir -p "$WEBROOT" "$CERTDIR"

echo "=== 1. 用 ZeroSSL 重试申请（webroot）==="
"$ACME" --issue -d zy.cn-zwny.com --webroot "$WEBROOT" --server zerossl --force 2>&1 | tail -6
if [ ! -s "$HOME/.acme.sh/zy.cn-zwny.com/zy.cn-zwny.com.cer" ]; then
  echo "RESULT: zerossl issue FAILED -> 保持 HTTP，24h 后可重试 letsencrypt 或用面板申请"
  exit 1
fi
echo "RESULT: zerossl issue OK"

echo ""
echo "=== 2. 安装证书 ==="
"$ACME" --install-cert -d zy.cn-zwny.com --server zerossl \
  --key-file "$CERTDIR/privkey.pem" \
  --fullchain-file "$CERTDIR/fullchain.pem" \
  --reloadcmd "/www/server/nginx/sbin/nginx -s reload" 2>&1 | tail -3
[ -s "$CERTDIR/fullchain.pem" ] && [ -s "$CERTDIR/privkey.pem" ] || { echo "FATAL: cert missing"; exit 1; }
echo "cert files OK"

echo ""
echo "=== 3. 备份并写 nginx 配置（80 跳转 + 443 ssl 反代）==="
BAK="$CONF.bak-step2b-$(date +%Y%m%d%H%M%S)"
cp "$CONF" "$BAK"
cat > "$CONF" <<'NGINX_CONF'
server {
    listen 80;
    server_name zy.cn-zwny.com;
    location ~ /\.well-known {
        root /www/server/panel/vhost/nginx/well-known;
        allow all;
    }
    location / { return 301 https://$host$request_uri; }
}
server {
    listen 443 ssl;
    server_name zy.cn-zwny.com;
    ssl_certificate     /www/server/panel/vhost/nginx/ssl/zy.cn-zwny.com/fullchain.pem;
    ssl_certificate_key /www/server/panel/vhost/nginx/ssl/zy.cn-zwny.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    location / {
        proxy_pass http://127.0.0.1:18081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 50m;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_connect_timeout 60s;
        proxy_read_timeout 120s;
        proxy_send_timeout 60s;
    }
}
NGINX_CONF
echo "written (backup: $BAK)"

echo ""
echo "=== 4. nginx -t 预检（失败回滚不 reload）==="
if /www/server/nginx/sbin/nginx -t 2>&1; then echo "OK"; else
  echo "FATAL: nginx -t failed, rollback"; cp "$BAK" "$CONF"; exit 1; fi

echo ""
echo "=== 5. reload ==="
/www/server/nginx/sbin/nginx -s reload 2>&1 && echo reloaded || exit 1
sleep 2

echo ""
echo "=== 6. 验证 ==="
curl -s -o /dev/null -w 'https_root=%{http_code}\n'  https://zy.cn-zwny.com/
curl -s -o /dev/null -w 'https_api=%{http_code}\n'   https://zy.cn-zwny.com/api/v1/captcha/image
curl -s -o /dev/null -w 'https_cost=%{http_code}\n'  https://zy.cn-zwny.com/project-cost-control
curl -s -o /dev/null -w 'http_redirect=%{http_code} -> %{redirect_url}\n' http://zy.cn-zwny.com/
echo "--- 证书 ---"
echo | openssl s_client -connect zy.cn-zwny.com:443 -servername zy.cn-zwny.com 2>/dev/null \
  | openssl x509 -noout -issuer -subject -enddate 2>/dev/null || echo "openssl inspect failed"
echo "=== step2b done ==="
