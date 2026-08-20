/**
 * 真实模式 E2E：B-3 劳务管理账本补测（账本全量补齐 M4，2026-08）
 *
 * @matrix B-14-x 劳务合同提交直批 EFFECTIVE+非草稿重提交拦截 /
 *   B-17-x 用工单后端合计=预览公式+提交直批 APPROVED+APPROVED 可删现状 /
 *   B-L-X1 班组→用工单链路 / B-L-X2/B-18-x 工资单自动汇总已审批用工单+直批 /
 *   B-L-X3/B-19-x 工资单审批后薪资统计可见+恒等式+compare null 分支 /
 *   B-15-7 班组删除引用守卫
 *
 * 实证（探测 2026-08-20）：
 *   - 劳务合同 submit 直批 DRAFT→EFFECTIVE（无 BPMN，workflowInstanceId=null）；
 *     项目预算管控自置 WARN_ONLY（默认 BLOCK 会拦支出合同 submit）
 *   - 用工单 submit 直批 DRAFT→APPROVED；totalAmount 后端计算
 *     = hours×hourlyRate + overtime×overtimeRate（8×30+2×45=330 实证一致）
 *   - APPROVED 用工单可删除（code=200，守卫缺失现状钉住，不做负向断言）
 *   - 工资单生成 totalSettlement 自动汇总周期内 APPROVED 用工单（330 实证）；
 *     payroll submit 直批 APPROVED；APPROVED 工资单「仅草稿状态可删除」
 *   - salary/stats：当月无已审批工资单 → HTTP 200 + code=500
 *     message「该月份暂无已审批的薪资数据」；payroll APPROVED 后 code=200 数据可见，
 *     totalActual=totalPayable-totalDeduction 恒等；compare momRate/yoyRate=null
 *     （无上月数据，前端「暂无数据」分支）
 *   - 班组删除 @ReferenceCheck：被工资单引用 → 400「该记录被 N 条数据引用，无法删除」
 *
 * 残留声明：APPROVED 工资单×1 + 被其引用的班组×1 删不掉（仅草稿可删/引用守卫），
 * 均 E2E_TEST_ 前缀可识别（巡检兜底）；用工单/劳务合同/配置/项目 afterAll 逆序清理。
 *
 * 纯前端守卫用例（B14-B19 共 36 例 vitest）见
 * src/__tests__/labor-matrix / labor-salary-stats-matrix 组件测试。
 */
import { test, expect } from '@playwright/test'
import { authedApiContext, e2ePrefix, todayStr, type AuthedContext } from './real-helper'

test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
const PREFIX = e2ePrefix()
const TODAY = todayStr()
const MONTH = TODAY.slice(0, 7)

let authed: AuthedContext | null = null
let projectId = ''
let cfgId = ''
let teamId = ''
let laborContractId = ''
let workOrderId = ''
let payrollId = ''
const TEAM_NAME = `${PREFIX}_木工一班`

async function apiJson(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, data?: any) {
  const resp = method === 'GET'
    ? await authed!.get(`${API_BASE}${path}`)
    : method === 'DELETE'
      ? await authed!.delete(`${API_BASE}${path}`)
      : method === 'PUT'
        ? await authed!.put(`${API_BASE}${path}`, { data })
        : await authed!.post(`${API_BASE}${path}`, { data })
  return { status: resp.status(), body: await resp.json().catch(() => null) }
}

test.beforeAll(async () => {
  authed = await authedApiContext()
  const cr = await apiJson('POST', '/api/v1/project', { projectName: `${PREFIX}_劳务项目`, projectNature: '新建', projectType: '市政工程' })
  expect(cr.body?.code, '创建承载项目').toBe(200)
  const pg = await apiJson('GET', `/api/v1/project/page?pageNum=1&pageSize=5&projectName=${encodeURIComponent(`${PREFIX}_劳务项目`)}`)
  projectId = String(pg.body?.data?.records?.[0]?.id || '')
  expect(projectId, '承载项目应可定位').toBeTruthy()
  // WARN_ONLY 预算管控（默认 BLOCK 会拦支出合同 submit，api-tests 09-labor 实证范式）
  const cfg = await apiJson('POST', '/api/v1/budget-control-configs', { projectId, controlMode: 'WARN_ONLY', warningThreshold: 80 })
  expect(cfg.body?.code, '创建预算管控配置').toBe(200)
  const eff = await apiJson('GET', `/api/v1/budget-control-configs/project/${projectId}`)
  cfgId = String(eff.body?.data?.id || '')
  // 班组（B-L-X1 链路起点，花名册/用工单/工资单关联源）
  const team = await apiJson('POST', '/api/v1/labor/team', { projectId, teamName: TEAM_NAME, leaderName: `${PREFIX}_组长`, workType: '木工', memberCount: 5 })
  expect(team.body?.code, '创建班组').toBe(200)
  const teamPg = await apiJson('GET', `/api/v1/labor/team/page?pageNum=1&pageSize=10&teamName=${encodeURIComponent(TEAM_NAME)}`)
  teamId = String((teamPg.body?.data?.records || [])[0]?.id || '')
  expect(teamId, '班组应可定位').toBeTruthy()
})

test.afterAll(async () => {
  if (!authed) return
  // 逆序清理；APPROVED 工资单与被引用班组预期删不掉（残留声明见文件头）
  if (workOrderId) await authed.delete(`${API_BASE}/api/v1/labor/work-order/${workOrderId}`).catch(() => {})
  if (payrollId) await authed.delete(`${API_BASE}/api/v1/labor/payroll/${payrollId}`).catch(() => {})
  if (teamId) await authed.delete(`${API_BASE}/api/v1/labor/team/${teamId}`).catch(() => {})
  if (laborContractId) await authed.delete(`${API_BASE}/api/v1/labor/contract/${laborContractId}`).catch(() => {})
  if (cfgId) await authed.delete(`${API_BASE}/api/v1/budget-control-configs/${cfgId}`).catch(() => {})
  await authed.delete(`${API_BASE}/api/v1/project/${projectId}`).catch(() => {})
  await authed.dispose()
})

test.describe('B-3 劳务合同（直批生效）', () => {
  test('@matrix B-14-x 合同创建 DRAFT→提交直批 EFFECTIVE→非草稿重提交拦截', async () => {
    const cr = await apiJson('POST', '/api/v1/labor/contract', {
      projectId, contractName: `${PREFIX}_劳务合同`, teamName: TEAM_NAME,
      contractAmount: 50000, startDate: TODAY, endDate: '2026-12-31',
    })
    expect(cr.body?.code, '创建劳务合同').toBe(200)
    const pg = await apiJson('GET', `/api/v1/labor/contract/page?pageNum=1&pageSize=10&contractName=${encodeURIComponent(`${PREFIX}_劳务合同`)}`)
    const contract = (pg.body?.data?.records || [])[0]
    expect(contract, '合同应可按名称定位').toBeTruthy()
    expect(contract.status).toBe('DRAFT')
    laborContractId = String(contract.id)
    const sub = await apiJson('POST', `/api/v1/labor/contract/${laborContractId}/submit`)
    expect(sub.body?.code, '提交直批').toBe(200)
    const detail = await apiJson('GET', `/api/v1/labor/contract/${laborContractId}`)
    expect(detail.body?.data?.status, '直批 EFFECTIVE（无 BPMN 实证）').toBe('EFFECTIVE')
    expect(detail.body?.data?.workflowInstanceId, '无流程实例').toBeNull()
    const sub2 = await apiJson('POST', `/api/v1/labor/contract/${laborContractId}/submit`)
    expect(sub2.body?.code, 'EFFECTIVE 重提交应拦截').not.toBe(200)
  })
})

test.describe('B-3 用工单（后端合计+直批）', () => {
  test('@matrix B-17-x/B-L-X1 创建→后端合计=预览公式→提交直批 APPROVED', async () => {
    const cr = await apiJson('POST', '/api/v1/labor/work-order', {
      projectId, teamId, workerName: `${PREFIX}_工人甲`, orderType: 'FIXED',
      workDate: TODAY, hours: 8, hourlyRate: 30, overtime: 2, overtimeRate: 45,
    })
    expect(cr.body?.code, '创建用工单').toBe(200)
    const pg = await apiJson('GET', `/api/v1/labor/work-order/page?page=1&size=10&projectId=${projectId}`)
    const wo = (pg.body?.data?.records || [])[0]
    expect(wo, '用工单应可定位').toBeTruthy()
    workOrderId = String(wo.id)
    expect(wo.status).toBe('DRAFT')
    // B-17-3 后端合计与前端预览公式一致：8×30 + 2×45 = 330
    expect(Number(wo.totalAmount), '后端合计=hours×hourlyRate+overtime×overtimeRate').toBe(330)
    const sub = await apiJson('POST', `/api/v1/labor/work-order/${workOrderId}/submit`)
    expect(sub.body?.code, '提交直批').toBe(200)
    const after = await apiJson('GET', `/api/v1/labor/work-order/page?page=1&size=10&projectId=${projectId}`)
    expect((after.body?.data?.records || [])[0]?.status, '直批 APPROVED').toBe('APPROVED')
    const sub2 = await apiJson('POST', `/api/v1/labor/work-order/${workOrderId}/submit`)
    expect(sub2.body?.code, 'APPROVED 重提交应拦截').not.toBe(200)
  })
})

test.describe('B-3 工资单（自动汇总+直批）与薪资统计', () => {
  test('@matrix B-19-x 工资单审批前当月统计无数据拦截（code=500 业务语义）', async () => {
    const stats = await apiJson('GET', `/api/v1/labor/salary/stats?projectId=${projectId}&month=${MONTH}`)
    expect(stats.status).toBe(200)
    expect(stats.body?.code, '无已审批工资单应返回业务错误').toBe(500)
    expect(stats.body?.message).toContain('该月份暂无已审批的薪资数据')
  })

  test('@matrix B-L-X2/B-18-x 生成工资单自动汇总周期内已审批用工单→提交直批 APPROVED', async () => {
    const cr = await apiJson('POST', '/api/v1/labor/payroll', {
      projectId, teamId, orderType: 'FIXED', periodStart: `${MONTH}-01`, periodEnd: TODAY,
    })
    expect(cr.body?.code, '生成工资单').toBe(200)
    const pg = await apiJson('GET', `/api/v1/labor/payroll/page?pageNum=1&pageSize=10&teamName=${encodeURIComponent(TEAM_NAME)}`)
    const payroll = (pg.body?.data?.records || [])[0]
    expect(payroll, '工资单应可定位').toBeTruthy()
    payrollId = String(payroll.id)
    expect(payroll.status).toBe('DRAFT')
    // B-18-3 汇总语义：totalSettlement=周期内已审批用工单合计（330，唯一一张 APPROVED 单）
    expect(Number(payroll.totalSettlement), '自动汇总已审批用工单').toBe(330)
    expect(Number(payroll.totalPaid), '无付款已付为 0').toBe(0)
    expect(Number(payroll.unpaid), '未付=结算-已付').toBe(330)
    const sub = await apiJson('POST', `/api/v1/labor/payroll/${payrollId}/submit`)
    expect(sub.body?.code, '工资单提交直批').toBe(200)
    const detail = await apiJson('GET', `/api/v1/labor/payroll/${payrollId}`)
    expect(detail.body?.data?.status, '直批 APPROVED').toBe('APPROVED')
  })

  test('@matrix B-L-X3/B-19-x 工资单审批后薪资统计可见+恒等式+compare null 分支', async () => {
    expect(payrollId).toBeTruthy()
    const stats = await apiJson('GET', `/api/v1/labor/salary/stats?projectId=${projectId}&month=${MONTH}`)
    expect(stats.body?.code, '审批后统计应可见').toBe(200)
    const data = stats.body?.data
    expect(Number(data.totalPayable), '应发合计=工资单结算额').toBe(330)
    expect(Number(data.totalDeduction)).toBe(0)
    // B-19-3 恒等式：实发=应发-扣款
    expect(Number(data.totalActual), 'totalActual=totalPayable-totalDeduction')
      .toBe(Number(data.totalPayable) - Number(data.totalDeduction))
    const teamRow = (data.teamList || []).find((t: any) => String(t.teamId) === teamId)
    expect(teamRow, '班组维度应可见').toBeTruthy()
    expect(teamRow.orderType).toBe('FIXED')
    // compare：无上月数据 → momRate/yoyRate null（前端「暂无数据」分支数据源）
    const cmp = await apiJson('GET', `/api/v1/labor/salary/compare?projectId=${projectId}&month=${MONTH}`)
    expect(cmp.body?.code).toBe(200)
    expect(cmp.body?.data?.momRate, '无上月数据 momRate 应为 null').toBeNull()
    expect(cmp.body?.data?.yoyRate, '无去年同月数据 yoyRate 应为 null').toBeNull()
  })
})

test.describe('B-3 班组引用守卫', () => {
  test('@matrix B-15-7 被工资单引用的班组删除拦截', async () => {
    const del = await apiJson('DELETE', `/api/v1/labor/team/${teamId}`)
    expect(del.body?.code, '被引用班组删除应拦截').not.toBe(200)
    // 实证：引用守卫错误走 msg 字段（业务异常处理器），兼容 message
    expect(del.body?.msg || del.body?.message, '引用守卫文案').toContain('无法删除')
    const pg = await apiJson('GET', `/api/v1/labor/team/page?pageNum=1&pageSize=10&teamName=${encodeURIComponent(TEAM_NAME)}`)
    expect((pg.body?.data?.records || []).length, '拦截后班组应完好').toBe(1)
  })
})
