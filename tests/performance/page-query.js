// page-query.js — 分页查询接口性能基线（测试成熟度阶段二 2.2.1）
//
// 【执行约束】夜间低峰（22:00-06:00）/ 并发 ≤20 VU / 单次 ≤5 分钟
// （run-k6.sh 负责时段与上限校验）
//
// 真实性：token 由 run-k6.sh 用真实登录流程预取（真实验证码链路），直接读
// 生产演示租户真实数据（project/contract/payment-apply 分页），无 mock。
// 指标：http_req_duration P95/P99（按接口名分 tag）。

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.K6_BASE || 'http://127.0.0.1:18080';
const TOKEN = __ENV.TOKEN;

export const options = {
  vus: 10,               // 约束：≤20
  duration: '3m',        // 约束：≤5m
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:GET /project/page}': ['p(95)<800', 'p(99)<1500'],
    'http_req_duration{name:GET /contract/page}': ['p(95)<800', 'p(99)<1500'],
    'http_req_duration{name:GET /payment-apply/page}': ['p(95)<800', 'p(99)<1500'],
  },
};

const PAGES = [
  { name: 'GET /project/page', url: `${BASE}/api/v1/project/page?page=1&size=10` },
  { name: 'GET /contract/page', url: `${BASE}/api/v1/contract/page?page=1&size=10` },
  { name: 'GET /payment-apply/page', url: `${BASE}/api/v1/finance/payment-apply/page?page=1&size=10` },
];

export default function () {
  if (!TOKEN) {
    console.error('缺少 TOKEN：应由 run-k6.sh 用真实登录预取后传入');
    return;
  }
  const target = PAGES[Math.floor(Math.random() * PAGES.length)];
  const res = http.get(target.url, {
    headers: { Authorization: `Bearer ${TOKEN}` },
    tags: { name: target.name },
  });
  check(res, {
    'HTTP 200': (r) => r.status === 200,
    '分页结构含 records': (r) => {
      try {
        const body = JSON.parse(r.body);
        const data = body.data || body;
        return Array.isArray(data.records);
      } catch (e) {
        return false;
      }
    },
  });
}
