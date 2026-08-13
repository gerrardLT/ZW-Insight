// payment-submit.js — 付款申请提交性能基线（测试成熟度阶段二 2.2.1）
//
// 【执行约束】夜间低峰（22:00-06:00）/ 并发 ≤20 VU / 单次 ≤5 分钟
// （run-k6.sh 负责时段与上限校验）
//
// 真实性：token 由 run-k6.sh 真实登录预取；走真实业务链路
//   POST /finance/payment-apply（创建草稿）→ POST /{id}/submit（提交进审批流）
// 数据策略（与 L3 test-api-finance.sh 同口径，真实演示租户）：
//   - 关联真实种子采购合同 91501（项目 90001），单笔 1000 元小额，
//     避免累计付款快速逼近合同额触发预算/应付 BLOCK
//   - 提交后立即调 withdraw-by-business 端点回收审批流（2026-08-13 堵增量，
//     二次升级）：旧版基于待办分页扫描的回收在高速提交下（系统健康后迭代
//     速率从 0.4/s 飙到 32/s）窗口失配大量失效，20 分钟堆 7000+ 任务。
//     现改为 businessKey O(1) 定位撤回（与提交速率无关），单据置 REJECTED
//     （不回写累计金额）且流程实例被引擎删除，运行态零残留。
//   - 恒定到达率限速（1 迭代/秒）：基线目的是测单请求延迟而非吃满吞吐，
//     无上限 vus/duration 模式在系统变快后迭代数失控（实证 49→3916）。
// 指标：http_req_duration P95/P99（submit 为核心写路径）。

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.K6_BASE || 'http://127.0.0.1:18080';
const TOKEN = __ENV.TOKEN;

export const options = {
  scenarios: {
    payment_submit: {
      executor: 'constant-arrival-rate',
      rate: 1,               // 恒定 1 迭代/秒：迭代总数有界（~120），不随系统变快失控
      timeUnit: '1s',
      duration: '2m',        // 约束：≤5m（run-k6.sh 校验本字段）
      preAllocatedVUs: 3,
      maxVUs: 5,             // 约束：≤20
    },
  },
  thresholds: {
    'http_req_duration{name:POST /payment-apply/submit}': ['p(95)<2000', 'p(99)<4000'],
    'http_req_duration{name:POST /payment-apply}': ['p(95)<1500', 'p(99)<3000'],
  },
};

// 创建接口返回 R<Void> 不带 ID：按 createdAt DESC 取最新草稿定位（与 L3 方案B同法）
function latestDraftId(headers) {
  const res = http.get(`${BASE}/api/v1/finance/payment-apply/page?page=1&size=1&status=DRAFT`, {
    headers,
    tags: { name: 'GET /payment-apply/page' },
  });
  try {
    const body = JSON.parse(res.body);
    const data = body.data || body;
    return data.records && data.records.length > 0 ? data.records[0].id : null;
  } catch (e) {
    return null;
  }
}

export default function () {
  if (!TOKEN) {
    console.error('缺少 TOKEN：应由 run-k6.sh 用真实登录预取后传入');
    return;
  }
  const headers = {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json',
  };

  // 1. 创建草稿（真实种子合同 91501 / 项目 90001）
  const created = http.post(
    `${BASE}/api/v1/finance/payment-apply`,
    JSON.stringify({
      projectId: 90001,
      contractId: 91501,
      contractCategory: 'PURCHASE',
      supplierName: 'k6性能基线供应商',
      paymentAmount: 1000.0,
      paymentDate: '2025-03-15',
    }),
    { headers, tags: { name: 'POST /payment-apply' } }
  );
  const createOk = check(created, { '创建 HTTP 200': (r) => r.status === 200 });
  if (!createOk) return;

  // 2. 定位刚创建的草稿并提交
  const id = latestDraftId(headers);
  if (!id) return;
  const submitted = http.post(`${BASE}/api/v1/finance/payment-apply/${id}/submit`, null, {
    headers,
    tags: { name: 'POST /payment-apply/submit' },
  });
  check(submitted, {
    '提交 HTTP 200': (r) => r.status === 200,
    '业务码 200': (r) => {
      try {
        return JSON.parse(r.body).code === 200;
      } catch (e) {
        return false;
      }
    },
  });

  // 3. 回收：businessKey O(1) 定位撤回（单据转 REJECTED，流程实例删除，
  //    运行态零残留；未打 name 标签不计入阈值指标）。幂等：无流程返回 false。
  reclaimApproval(headers, id);
}

function reclaimApproval(headers, businessId) {
  http.post(
    `${BASE}/api/v1/workflow/approval/withdraw-by-business?businessType=PAYMENT_APPLY&businessId=${businessId}`,
    null,
    { headers, tags: { name: 'POST /approval/withdraw-by-business (reclaim)' } }
  );
}
