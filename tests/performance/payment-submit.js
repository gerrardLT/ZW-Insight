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
//   - 提交后立即 terminate 回收审批流（2026-08-13 堵增量）：
//     旧策略「提交后不回滚」致 admin 待办 4 天堆积 6 万条（ACT_RU_TASK
//     60K/ACT_RU_VARIABLE 300K），拖垮 /todo 接口与本场景自身。
//     terminate 发 ApprovalRejectEvent → 单据置 REJECTED（不回写累计金额）
//     且流程实例被引擎删除，运行态零残留；回收请求不计入阈值指标。
// 指标：http_req_duration P95/P99（submit 为核心写路径）。

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.K6_BASE || 'http://127.0.0.1:18080';
const TOKEN = __ENV.TOKEN;

export const options = {
  vus: 3,                // 写路径 + 真实审批流，保守并发（约束 ≤20）
  duration: '2m',        // 约束：≤5m
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

  // 3. 回收：定位本笔待办并 terminate（单据转 REJECTED，流程实例删除，
  //    运行态零残留；未打 name 标签不计入阈值指标）。失败不阻断——
  //    下次全量清理兑底，但会记 check 失败供监控。
  reclaimApproval(headers, id);
}

function reclaimApproval(headers, businessId) {
  const todo = http.get(`${BASE}/api/v1/workflow/approval/todo?page=1&size=20`, {
    headers,
    tags: { name: 'GET /approval/todo (reclaim)' },
  });
  let taskId = null;
  try {
    const body = JSON.parse(todo.body);
    const records = (body.data && body.data.records) || [];
    const hit = records.find((r) => String(r.businessId) === String(businessId));
    taskId = hit ? hit.taskId : null;
  } catch (e) {
    return;
  }
  if (!taskId) return;
  http.post(
    `${BASE}/api/v1/workflow/approval/terminate`,
    JSON.stringify({ taskId, comment: 'k6性能基线回收' }),
    { headers, tags: { name: 'POST /approval/terminate (reclaim)' } }
  );
}
