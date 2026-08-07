#!/usr/bin/env python3
"""
captcha-bridge.py — 真实验证码桥（测试成熟度阶段二 2.2）

职责：把「GET 后端 /api/v1/captcha/image 取 uuid → docker exec zwi-redis 读
captcha:<uuid> 取真实 code」这一真实链路以本地 HTTP 形式暴露给 k6 容器
（k6 沙箱内无法执行 docker 命令）。

真实性保证：
  - 每次请求都向后端真实申请新验证码（不缓存、不复用、不伪造）
  - code 直接来自 Redis 组件真实存储值（与 verify-base.sh 需求 5.1 同一机制）
  - 无 mock、无假数据兜底；取码失败原样返回 502 由 k6 侧 check 判失败

仅监听 127.0.0.1（服务器本机），无鉴权风险；由 run-k6.sh 拉起并随其退出清理。
"""
import argparse
import json
import re
import subprocess
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer

BASE = "http://127.0.0.1:18080"
REDIS_CT = "zwi-redis"


def get_real_captcha():
    """真实取码：后端 captcha/image + Redis captcha:<uuid>（同 verify-base.sh）"""
    with urllib.request.urlopen(f"{BASE}/api/v1/captcha/image", timeout=10) as resp:
        body = resp.read().decode("utf-8")
    m = re.search(r'"uuid"\s*:\s*"([^"]+)"', body)
    if not m:
        raise RuntimeError("captcha/image 响应无 uuid")
    uuid = m.group(1)
    out = subprocess.run(
        ["docker", "exec", REDIS_CT, "redis-cli", "GET", f"captcha:{uuid}"],
        capture_output=True, text=True, timeout=10,
    ).stdout.strip().strip('"')
    if not out:
        raise RuntimeError(f"Redis 无 captcha:{uuid}（过期或不存在）")
    return {"uuid": uuid, "code": out}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"status": "ok"})
            return
        if self.path == "/captcha":
            try:
                self._send(200, get_real_captcha())
            except Exception as e:
                self._send(502, {"error": str(e)})
            return
        self._send(404, {"error": "not found"})

    def _send(self, code, obj):
        data = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):  # 静默访问日志
        pass


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:18080")
    ap.add_argument("--redis-ct", default="zwi-redis")
    ap.add_argument("--port", type=int, default=19191)
    args = ap.parse_args()
    BASE = args.base
    REDIS_CT = args.redis_ct
    print(f"captcha-bridge listening on 127.0.0.1:{args.port} (base={BASE}, redis={REDIS_CT})")
    HTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
