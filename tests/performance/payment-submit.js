// payment-submit.js — 付款提交写路径性能基线（测试成熟度阶段二 2.2.1）
//
// 【执行约束】夜间低峰（22:00-06:00）/ 并发 ≤20 VU / 单次 ≤5 分钟
// （run-k6.sh 负责时段与上限校验）
//
// 真实性：token 由 run-k6.sh 真实登录预取；走真实业务链路
//   POST /finance/payment-apply（创建草稿）→ POST /{id}/submit（提交进审批流）
// 数据策略（2026-08-24 租户口径修复，用户决策「租户 9999 自建数据」）：
//   - 旧版硬编码租户 1 种子合同 91501/项目 90001，与 run-k6.sh 默认账号
//     t9999admin（租户 9999）错位：创建草稿成功但 submit 被「关联合同不存在」
//     拒绝（跨租户），业务码 check 从未通过（被 k6 exit code 绿灯掩盖）。
//   - 现改为 setup() 在租户 9999 内走真实接口自建数据（幂等复用）：
//     项目报备（submit 直接 FILED 无审批）→ OTHER_EXPENSE 其他支出合同
//     （创建时直接携带累计结算，PaymentApplyService 按 contractCategory
//     路由 biz_other_contract，与 L4 阶段 9D 同口径）→ 1000 元小额付款。
//   - 付款校验链（PaymentApplyService.submit）：合同存在 + 付款额 ≤
//     累计结算-累计已付；withdraw 驳回不回写累计已付，故余量只减于审批
//     通过（k6 每次撤回，余量实际不消耗），setup 余量阈值仅为防呆。
//   - 提交后立即调 withdraw-by-business 端点回收审批流（2026-08-13 堵增量，
//     二次升级）：businessKey O(1) 定位撤回（与提交速率无关），单据置
//     REJECTED（不回写累计金额）且流程实例被引擎删除，运行态零残留。
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

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function authHeaders() {
  return { Authorization: `Bearer ${TOKEN}`, ...JSON_HEADERS };
}

/** R<T> 解包：业务码非 200 抛错（setup 阶段不允许静默降级） */
function unwrap(res, action) {
  let body;
  try {
    body = JSON.parse(res.body);
  } catch (e) {
    throw new Error(`${action}：响应非 JSON（HTTP ${res.status}）`);
  }
  if (res.status !== 200 || body.code !== 200) {
    throw new Error(`${action}：HTTP ${res.status} / 业务码 ${body.code} / ${body.message}`);
  }
  return body.data;
}

/** 分页查第一条记录 ID（创建接口返回 R<Void> 不带 ID，与 L3 方案B同法） */
function firstRecordId(url, action) {
  const data = unwrap(http.get(url, { headers: authHeaders() }), action);
  if (!data || !data.records || data.records.length === 0) {
    throw new Error(`${action}：分页查询无记录`);
  }
  return data.records[0];
}

/**
 * setup（单次）：确保租户 9999 内存在余量充足的 OTHER_EXPENSE 合同。
 * 幂等复用：跨运行直接沿用已建项目/合同；被 L4 兜底清理后自动重建。
 */
export function setup() {
  if (!TOKEN) {
    throw new Error('缺少 TOKEN：应由 run-k6.sh 用真实登录预取后传入');
  }

  // 1. 复用已有合同：余量（累计结算-累计已付）≥ 20 万（k6 每晚 ~121 笔 × 1000 元，
  //    撤回不回写累计已付，余量实际不消耗，阈值仅为防呆）
  const list = unwrap(
    http.get(`${BASE}/api/v1/contract/other?page=1&size=50&contractCategory=OTHER_EXPENSE`, {
      headers: authHeaders(),
      tags: { name: 'setup:GET /contract/other' },
    }),
    'setup:查询其他支出合同',
  );
  const reusable = (list.records || []).find(
    (c) => Number(c.cumulativeSettlement || 0) - Number(c.cumulativePaid || 0) >= 200000,
  );
  if (reusable) {
    return { projectId: reusable.projectId, contractId: reusable.id };
  }

  // 2. 无可用合同 → 真实链路自建：项目报备 → 其他支出合同（含累计结算）
  unwrap(
    http.post(
      `${BASE}/api/v1/project`,
      JSON.stringify({
        projectName: '[k6]性能基线项目',
        projectNature: '新建',
        projectType: '公共建筑',
        ownerCompanyName: 'k6性能基线业主',
        signingCompanyName: 'k6性能基线承包商',
        projectOverview: 'k6 付款提交性能基线专用项目（租户 9999 自动化测试数据）',
        projectAddress: '广州市天河区',
        contactName: 'k6基线',
        contactPhone: '13800009999',
        needTender: 0,
        budgetAmount: 10000000.0,
      }),
      { headers: authHeaders(), tags: { name: 'setup:POST /project' } },
    ),
    'setup:创建项目',
  );
  const project = firstRecordId(
    `${BASE}/api/v1/project/page?page=1&size=1&projectName=${encodeURIComponent('[k6]性能基线项目')}`,
    'setup:定位项目',
  );
  // 立项提交：ProjectService.submit 无审批流，直接 FILED
  unwrap(
    http.post(`${BASE}/api/v1/project/${project.id}/submit`, null, {
      headers: authHeaders(),
      tags: { name: 'setup:POST /project/submit' },
    }),
    'setup:项目立项提交',
  );

  unwrap(
    http.post(
      `${BASE}/api/v1/contract/other`,
      JSON.stringify({
        projectId: project.id,
        contractName: '[k6]性能基线其他支出合同',
        contractCategory: 'OTHER_EXPENSE',
        partyBName: 'k6性能基线供应商',
        contractAmount: 10000000.0,
        cumulativeSettlement: 10000000.0, // 其他合同无独立结算流程，创建时直接携带（与 L4 9D 同口径）
      }),
      { headers: authHeaders(), tags: { name: 'setup:POST /contract/other' } },
    ),
    'setup:创建其他支出合同',
  );
  const contract = firstRecordId(
    `${BASE}/api/v1/contract/other?page=1&size=1&projectId=${project.id}&contractCategory=OTHER_EXPENSE`,
    'setup:定位其他支出合同',
  );
  return { projectId: project.id, contractId: contract.id };
}

// 创建接口返回 R<Void> 不带 ID：按 createdAt DESC 取最新草稿定位（与 L3 方案B同法）
function latestDraftId() {
  const res = http.get(`${BASE}/api/v1/finance/payment-apply/page?page=1&size=1&status=DRAFT`, {
    headers: authHeaders(),
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

export default function (data) {
  const headers = authHeaders();

  // 1. 创建草稿（setup 在租户 9999 自建的 OTHER_EXPENSE 合同）
  const created = http.post(
    `${BASE}/api/v1/finance/payment-apply`,
    JSON.stringify({
      projectId: data.projectId,
      contractId: data.contractId,
      contractCategory: 'OTHER_EXPENSE',
      supplierName: 'k6性能基线供应商',
      paymentAmount: 1000.0,
      paymentDate: '2025-03-15',
    }),
    { headers, tags: { name: 'POST /payment-apply' } }
  );
  const createOk = check(created, { '创建 HTTP 200': (r) => r.status === 200 });
  if (!createOk) return;

  // 2. 定位刚创建的草稿并提交
  const id = latestDraftId();
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
