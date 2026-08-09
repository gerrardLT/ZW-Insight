// login.js — 登录接口性能基线（测试成熟度阶段二 2.2.1）
//
// 【执行约束】夜间低峰（22:00-06:00）/ 并发 ≤20 VU / 单次 ≤5 分钟
// （run-k6.sh 负责时段与上限校验，本脚本 options 亦不超限）
//
// 真实性：每迭代经 captcha-bridge 取真实验证码（后端 captcha/image + Redis
// captcha:<uuid>），完成真实登录；无 mock、无假 token。
// 指标：http_req_duration P95/P99（含取码 RTT，代表真实用户登录全链路）。

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.K6_BASE || 'http://127.0.0.1:18080';
const BRIDGE = __ENV.K6_BRIDGE || 'http://127.0.0.1:19191';
// 默认测试租户 9999 专用账号：高频登录会触发 security.max-devices 设备淘汰，
// 用专用账号避免拉黑 admin 等业务账号的活跃 token（run-k6.sh 会透传 ZWI_USER/ZWI_PASS）
const USERNAME = __ENV.ZWI_USER || 't9999admin';
const PASSWORD = __ENV.ZWI_PASS || '123456';

export const options = {
  vus: 5,                // 约束：≤20
  duration: '2m',        // 约束：≤5m
  thresholds: {
    http_req_failed: ['rate<0.1'],
    http_req_duration: ['p(95)<3000', 'p(99)<5000'], // 首轮基线阈值，跑完按实测回填
  },
};

export default function () {
  // 1. 经桥取真实验证码（每次都是后端新发 + Redis 真实值）
  const cap = http.get(`${BRIDGE}/captcha`);
  const capOk = check(cap, { 'bridge 200': (r) => r.status === 200 });
  if (!capOk) return;
  const { uuid, code } = JSON.parse(cap.body);

  // 2. 真实登录
  const res = http.post(
    `${BASE}/api/v1/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD, captchaUuid: uuid, captchaCode: code }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /auth/login' } }
  );

  check(res, {
    'login HTTP 200': (r) => r.status === 200,
    '返回 accessToken': (r) => {
      try {
        const body = JSON.parse(r.body);
        const data = body.data || body;
        return Boolean(data.accessToken || data.token);
      } catch (e) {
        return false;
      }
    },
  });
}
