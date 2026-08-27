/**
 * M4 账本补齐：B-3 劳务域页面矩阵用例（合同/班组/花名册/派工单/工资单）
 * views/labor/{contract,team,roster,work-order,payroll}.vue
 *
 * 既有 P3 覆盖（不重复）：CRUD 标准 6 例见 labor-pages/team-crud/roster-crud/settlement-docs，
 * 行提交审批见 labor-pages.component.test.ts。本文件钉住矩阵级行为差异与盲点现状。
 *
 * @matrix B-14-1/B-14-2/B-14-3/B-14-5/B-14-6/B-14-7/B-14-8
 * @matrix B-15-2/B-15-3/B-15-4/B-15-5
 * @matrix B-16-1/B-16-2/B-16-5/B-16-6
 * @matrix B-17-1/B-17-2/B-17-3/B-17-4/B-17-6/B-17-8
 * @matrix B-18-1/B-18-2/B-18-3/B-18-5/B-18-6/B-18-8
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockContractPage, mockContractCreate, mockTeamPage, mockTeamCreate,
  mockRosterPage, mockRosterCreate,
  mockWorkOrderPage, mockWorkOrderCreate,
  mockPayrollPage, mockPayrollCreate,
  mockWarning, mockSuccess, mockConfirm,
} = vi.hoisted(() => ({
  mockContractPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockContractCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockTeamPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockTeamCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockRosterPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockRosterCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockWorkOrderPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockWorkOrderCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockPayrollPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockPayrollCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockWarning: vi.fn(),
  mockSuccess: vi.fn(),
  mockConfirm: vi.fn(async () => 'confirm'),
}))

vi.mock('@/api/labor', () => ({
  getLaborContractPage: mockContractPage, createLaborContract: mockContractCreate,
  updateLaborContract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteLaborContract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  submitLaborContract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  getLaborTeamPage: mockTeamPage, createLaborTeam: mockTeamCreate,
  updateLaborTeam: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteLaborTeam: vi.fn(async (): Promise<any> => ({ code: 200 })),
  getLaborRosterPage: mockRosterPage, createLaborRoster: mockRosterCreate,
  updateLaborRoster: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteLaborRoster: vi.fn(async (): Promise<any> => ({ code: 200 })),
  entryLaborRoster: vi.fn(async (): Promise<any> => ({ code: 200 })),
  exitLaborRoster: vi.fn(async (): Promise<any> => ({ code: 200 })),
  getWorkOrderPage: mockWorkOrderPage, createWorkOrder: mockWorkOrderCreate,
  updateWorkOrder: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteWorkOrder: vi.fn(async (): Promise<any> => ({ code: 200 })),
  submitWorkOrder: vi.fn(async (): Promise<any> => ({ code: 200 })),
  getPayrollPage: mockPayrollPage, createPayroll: mockPayrollCreate,
  deletePayroll: vi.fn(async (): Promise<any> => ({ code: 200 })),
  submitPayroll: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))
vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mockSuccess, error: vi.fn(), warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

// 暗色联动引入 useAppStore 后：无 pinia 环境的组件测试统一 mock，防 getActivePinia
vi.mock('@/stores/app', () => ({
  useAppStore: () => ({ isDark: false }),
}))
import LaborContract from '@/views/labor/contract.vue'
import Team from '@/views/labor/team.vue'
import Roster from '@/views/labor/roster.vue'
import WorkOrder from '@/views/labor/work-order.vue'
import Payroll from '@/views/labor/payroll.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const contractSrc = readFileSync(resolve(__testDir, '../views/labor/contract.vue'), 'utf-8')
const teamSrc = readFileSync(resolve(__testDir, '../views/labor/team.vue'), 'utf-8')
const rosterSrc = readFileSync(resolve(__testDir, '../views/labor/roster.vue'), 'utf-8')
const workOrderSrc = readFileSync(resolve(__testDir, '../views/labor/work-order.vue'), 'utf-8')
const payrollSrc = readFileSync(resolve(__testDir, '../views/labor/payroll.vue'), 'utf-8')

const stubs = {
  ProjectSelector: { template: '<div class="stub-project-selector" />', props: ['modelValue'] },
  TeamSelector: { template: '<div class="stub-team-selector" />', props: ['modelValue'] },
}

let wrapper: any = null
beforeEach(() => { vi.clearAllMocks() })
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

// ─── B14 劳务合同 ───
describe('labor/contract.vue B14 矩阵', () => {
  async function mountContract(records: any[] = []) {
    mockContractPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(LaborContract, { global: { plugins: [ElementPlus], stubs } })
    await flushPromises()
    return wrapper
  }

  it('B-14-1 formRules 必填 3 条：projectId/contractName/teamName（缺陷#9 后含项目字段）', async () => {
    const w = await mountContract()
    const rules = w.vm.$.setupState.formRules
    expect((rules.projectId as any[])[0]).toMatchObject({ required: true, message: '请选择项目' })
    expect((rules.contractName as any[])[0]).toMatchObject({ required: true, message: '请输入合同名称' })
    expect((rules.teamName as any[])[0]).toMatchObject({ required: true, message: '请输入施工队伍' })
    expect(Object.keys(rules)).toHaveLength(3)
  })

  it('B-14-2 合同金额 min=0 precision=2（模板表达式钉住）', async () => {
    await mountContract()
    expect(contractSrc).toContain('v-model="formData.contractAmount" :min="0" :precision="2"')
  })

  it('B-14-3 状态 tag 映射：EFFECTIVE→生效/success，其他→草稿/info（源码钉住）', async () => {
    await mountContract()
    expect(contractSrc).toContain("row.status === 'EFFECTIVE' ? 'success' : 'info'")
    expect(contractSrc).toContain("row.status === 'EFFECTIVE' ? '生效' : '草稿'")
  })

  it('B-14-5 handleReset 恢复默认查询参数并重新加载', async () => {
    const w = await mountContract()
    const st = w.vm.$.setupState
    st.queryParams.contractName = '某合同'
    st.queryParams.status = 'EFFECTIVE'
    st.queryParams.page = 3
    mockContractPage.mockClear()
    st.handleReset()
    expect(st.queryParams).toEqual({ page: 1, size: 10, projectId: undefined, contractName: '', teamName: '', status: '' })
    expect(mockContractPage).toHaveBeenCalled()
  })

  it('B-14-6 提交审批按钮仅 DRAFT 行渲染（盲点13 修复后实证，账本「无提交按钮」已过时）', async () => {
    const w = await mountContract([
      { id: 1, contractName: '草稿合同', status: 'DRAFT' },
      { id: 2, contractName: '生效合同', status: 'EFFECTIVE' },
    ])
    const texts: string[] = w.findAll('button').map((b: any) => b.text())
    expect(texts.filter(t => t.includes('提交审批'))).toHaveLength(1)
  })

  it('B-14-8 钉住现状：编辑/删除按钮无状态条件（EFFECTIVE 行仍渲染，与分包合同 D6 不一致）', async () => {
    const w = await mountContract([
      { id: 1, contractName: '草稿合同', status: 'DRAFT' },
      { id: 2, contractName: '生效合同', status: 'EFFECTIVE' },
    ])
    const texts: string[] = w.findAll('button').map((b: any) => b.text())
    expect(texts.filter(t => t === '编辑')).toHaveLength(2)
    expect(texts.filter(t => t === '删除')).toHaveLength(2)
    // 源码实证：编辑/删除无 v-if 状态条件
    expect(contractSrc).not.toContain('v-if="row.status === \'DRAFT\'" link type="primary" @click="handleEdit')
    expect(contractSrc).toContain('<el-button link type="primary" @click="handleEdit(row)">编辑</el-button>')
  })
})

// ─── B15 班组 ───
describe('labor/team.vue B15 矩阵', () => {
  async function mountTeam(records: any[] = []) {
    mockTeamPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Team, { global: { plugins: [ElementPlus], stubs } })
    await flushPromises()
    return wrapper
  }

  it('B-15-2 formRules 必填 3 条：teamName/leaderName/workType', async () => {
    const w = await mountTeam()
    const rules = w.vm.$.setupState.formRules
    expect((rules.teamName as any[])[0]).toMatchObject({ required: true, message: '请输入班组名称' })
    expect((rules.leaderName as any[])[0]).toMatchObject({ required: true, message: '请输入班组长' })
    expect((rules.workType as any[])[0]).toMatchObject({ required: true, message: '请输入工种' })
    expect(Object.keys(rules)).toHaveLength(3)
  })

  it('B-15-3 人数 memberCount 默认 1、min=1（handleAdd 重置 + 模板钉住）', async () => {
    const w = await mountTeam()
    const st = w.vm.$.setupState
    st.formData.memberCount = 99
    st.handleAdd()
    expect(st.formData.memberCount).toBe(1)
    expect(teamSrc).toContain('v-model="formData.memberCount" :min="1"')
  })

  it('B-15-5 钉住现状：leaderPhone 无任何校验规则（formRules 无该键）', async () => {
    const w = await mountTeam()
    const rules = w.vm.$.setupState.formRules
    expect('leaderPhone' in rules).toBe(false)
  })
})

// ─── B16 花名册 ───
describe('labor/roster.vue B16 矩阵', () => {
  async function mountRoster(records: any[] = []) {
    mockRosterPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Roster, { global: { plugins: [ElementPlus], stubs } })
    await flushPromises()
    return wrapper
  }

  it('B-16-1 formRules 必填 2 条：workerName/idCard', async () => {
    const w = await mountRoster()
    const rules = w.vm.$.setupState.formRules
    expect((rules.workerName as any[])[0]).toMatchObject({ required: true, message: '请输入姓名' })
    expect((rules.idCard as any[])[0]).toMatchObject({ required: true, message: '请输入身份证号' })
    expect(Object.keys(rules)).toHaveLength(2)
  })

  it('B-16-2 钉住现状：idCard 仅必填无格式校验（无 pattern/validator）', async () => {
    const w = await mountRoster()
    const idCardRules = (w.vm.$.setupState.formRules.idCard || []) as any[]
    expect(idCardRules.every((r: any) => !r.pattern && typeof r.validator !== 'function')).toBe(true)
  })

  it('B-16-5 状态 tag 映射：status===1→在场/success，其他→退场/info（源码钉住）', async () => {
    await mountRoster()
    expect(rosterSrc).toContain("row.status === 1 ? 'success' : 'info'")
    expect(rosterSrc).toContain("row.status === 1 ? '在场' : '退场'")
  })

  it('B-16-6 钉住现状：表单无退场日期字段（表格列有 exitDate，编辑态无法维护）', async () => {
    const w = await mountRoster()
    expect('exitDate' in w.vm.$.setupState.formData).toBe(false)
    expect(rosterSrc).toContain('prop="exitDate" label="退场日期"')
  })

  it('B-16-7 P0 Req5 进退场按钮门禁：在岗显退场登记、离岗显进场登记', async () => {
    const w = await mountRoster([
      { id: 1, workerName: '在场工人', status: 1 },
      { id: 2, workerName: '离场工人', status: 0 },
    ])
    const text = w.text()
    expect(text).toContain('退场登记')
    expect(text).toContain('进场登记')
    // 筛选参数接入 page 接口（entryStatus）
    expect('entryStatus' in w.vm.$.setupState.queryParams).toBe(true)
  })
})

// ─── B17 派工单 ───
describe('labor/work-order.vue B17 矩阵', () => {
  async function mountWorkOrder(records: any[] = []) {
    mockWorkOrderPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(WorkOrder, { global: { plugins: [ElementPlus], stubs } })
    await flushPromises()
    return wrapper
  }

  it('B-17-1 formRules 必填 5 条：projectId/teamId/workerName/orderType/workDate', async () => {
    const w = await mountWorkOrder()
    const rules = w.vm.$.setupState.formRules
    expect((rules.projectId as any[])[0]).toMatchObject({ required: true, message: '请选择项目' })
    expect((rules.teamId as any[])[0]).toMatchObject({ required: true, message: '请选择班组' })
    expect((rules.workerName as any[])[0]).toMatchObject({ required: true, message: '请输入工人姓名' })
    expect((rules.orderType as any[])[0]).toMatchObject({ required: true, message: '请选择用工类型' })
    expect((rules.workDate as any[])[0]).toMatchObject({ required: true, message: '请选择工作日期' })
    expect(Object.keys(rules)).toHaveLength(5)
  })

  it('B-17-2 totalPreview = hours×hourlyRate + overtime×overtimeRate（computed 行为 + 文案钉住）', async () => {
    const w = await mountWorkOrder()
    const st = w.vm.$.setupState
    st.formData.hours = 8
    st.formData.hourlyRate = 30
    st.formData.overtime = 2
    st.formData.overtimeRate = 45
    expect(st.totalPreview).toBe(330)
    expect(workOrderSrc).toContain('（最终以后端计算为准）')
  })

  it('B-17-3 工时 precision=1、时薪 precision=2、加班费率 precision=2（模板钉住）', async () => {
    await mountWorkOrder()
    expect(workOrderSrc).toContain('v-model="formData.hours" :min="0" :precision="1"')
    expect(workOrderSrc).toContain('v-model="formData.hourlyRate" :min="0" :precision="2"')
    expect(workOrderSrc).toContain('v-model="formData.overtimeRate" :min="0" :precision="2"')
  })

  it('B-17-4 用工类型映射：FIXED→固定，其他→临时（源码钉住）', async () => {
    await mountWorkOrder()
    expect(workOrderSrc).toContain("row.orderType === 'FIXED' ? '固定' : '临时'")
  })

  it('B-17-6 钉住现状：分页为 page/size 口径（与劳务域其余页 pageNum/pageSize 不一致）', async () => {
    const w = await mountWorkOrder()
    expect(w.vm.$.setupState.queryParams).toMatchObject({ page: 1, size: 10 })
    expect(workOrderSrc).toContain('v-model:current-page="queryParams.page"')
  })

  it('B-17-8 提交/删除按钮仅 DRAFT 行渲染', async () => {
    const w = await mountWorkOrder([
      { id: 1, workerName: '张三', status: 'DRAFT' },
      { id: 2, workerName: '李四', status: 'APPROVED' },
    ])
    const texts: string[] = w.findAll('button').map((b: any) => b.text())
    expect(texts.filter(t => t === '提交')).toHaveLength(1)
    expect(texts.filter(t => t === '删除')).toHaveLength(1)
  })
})

// ─── B18 工资单 ───
describe('labor/payroll.vue B18 矩阵', () => {
  async function mountPayroll(records: any[] = [], teamRecords: any[] = []) {
    mockPayrollPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    mockTeamPage.mockResolvedValue({ code: 200, data: { records: teamRecords, total: teamRecords.length } })
    wrapper = mount(Payroll, { global: { plugins: [ElementPlus], stubs } })
    await flushPromises()
    return wrapper
  }

  it('B-18-1 生成表单必填 3 条：teamId/orderType/period', async () => {
    const w = await mountPayroll()
    const rules = w.vm.$.setupState.formRules
    expect((rules.teamId as any[])[0]).toMatchObject({ required: true, message: '请选择班组' })
    expect((rules.orderType as any[])[0]).toMatchObject({ required: true, message: '请选择用工类型' })
    expect((rules.period as any[])[0]).toMatchObject({ required: true, message: '请选择结算周期' })
    expect(Object.keys(rules)).toHaveLength(3)
  })

  it('B-18-2 handleTeamChange 按选中班组自动带出 projectId', async () => {
    const w = await mountPayroll([], [{ id: 5, teamName: 'M4班组', projectId: 77 }])
    const st = w.vm.$.setupState
    st.handleTeamChange(5)
    expect(st.formData.projectId).toBe(77)
    st.handleTeamChange(999) // 未命中班组 → projectId 为 undefined
    expect(st.formData.projectId).toBeUndefined()
  })

  it('B-18-3 三态状态映射：SETTLED→已结算/success、APPROVED→已审批/warning、默认草稿/info', async () => {
    const w = await mountPayroll()
    const st = w.vm.$.setupState
    expect(st.statusLabel('SETTLED')).toBe('已结算')
    expect(st.statusLabel('APPROVED')).toBe('已审批')
    expect(st.statusLabel('DRAFT')).toBe('草稿')
    expect(st.statusTagType('SETTLED')).toBe('success')
    expect(st.statusTagType('APPROVED')).toBe('warning')
    expect(st.statusTagType('DRAFT')).toBe('info')
  })

  it('B-18-5 alert 文案与 orderType 默认 FIXED 钉住（handleAdd 重置）', async () => {
    const w = await mountPayroll()
    const st = w.vm.$.setupState
    expect(payrollSrc).toContain('结算金额由周期内已审批的用工单自动汇总，无需手工录入。')
    st.formData.orderType = 'TEMPORARY'
    st.handleAdd()
    expect(st.formData.orderType).toBe('FIXED')
    expect(st.formData.period).toEqual([])
  })

  it('B-18-6 提交/删除按钮仅 DRAFT 行渲染', async () => {
    const w = await mountPayroll([
      { id: 1, teamName: 'A', status: 'DRAFT' },
      { id: 2, teamName: 'B', status: 'SETTLED' },
    ])
    const texts: string[] = w.findAll('button').map((b: any) => b.text())
    expect(texts.filter(t => t === '提交')).toHaveLength(1)
    expect(texts.filter(t => t === '删除')).toHaveLength(1)
  })

  it('B-18-8 生成提交 payload：period 拆分为 periodStart/periodEnd，附 teamId/projectId/orderType', async () => {
    const w = await mountPayroll([], [{ id: 5, teamName: 'M4班组', projectId: 77 }])
    const st = w.vm.$.setupState
    st.formRef = { validate: async () => true }
    st.formData.teamId = 5
    st.formData.projectId = 77
    st.formData.orderType = 'FIXED'
    st.formData.period = ['2026-08-01', '2026-08-31']
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockPayrollCreate).toHaveBeenCalledWith({
      teamId: 5, projectId: 77, orderType: 'FIXED',
      periodStart: '2026-08-01', periodEnd: '2026-08-31',
    })
    expect(mockSuccess).toHaveBeenCalledWith('生成成功')
  })
})
