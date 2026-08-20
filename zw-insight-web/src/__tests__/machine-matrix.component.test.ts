/**
 * M3 账本补齐：B-2 机械域合同/台账/进出场/台班/维修页矩阵用例
 * views/machine/contract.vue（B6）+ ledger.vue（B7）+ entry.vue（B8）
 * + work-log.vue（B9）+ repair.vue（B10）
 *
 * @matrix B-6-1/B-6-2/B-6-3/B-6-6/B-6-8
 * @matrix B-7-1/B-7-2/B-7-3/B-7-5/B-7-7
 * @matrix B-8-1/B-8-2/B-8-5/B-8-8
 * @matrix B-9-1/B-9-2/B-9-3/B-9-7/B-9-8
 * @matrix B-10-1/B-10-3/B-10-4/B-10-5
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockContractPage, mockSubmitContract, mockDeleteContract,
  mockLedgerPage, mockDeleteLedger,
  mockEntryPage, mockWorkLogPage, mockRepairPage,
  mockWarning, mockSuccess, mockConfirm,
} = vi.hoisted(() => ({
  mockContractPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockSubmitContract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDeleteContract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockLedgerPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockDeleteLedger: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockEntryPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockWorkLogPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockRepairPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockWarning: vi.fn(),
  mockSuccess: vi.fn(),
  mockConfirm: vi.fn(async () => 'confirm'),
}))

vi.mock('@/api/machine', () => ({
  getMachineContractPage: mockContractPage,
  createMachineContract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  updateMachineContract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteMachineContract: mockDeleteContract,
  submitMachineContract: mockSubmitContract,
  getMachineLedgerPage: mockLedgerPage,
  createMachineLedger: vi.fn(async (): Promise<any> => ({ code: 200 })),
  updateMachineLedger: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteMachineLedger: mockDeleteLedger,
  getMachineEntryPage: mockEntryPage,
  createMachineEntry: vi.fn(async (): Promise<any> => ({ code: 200 })),
  updateMachineEntry: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteMachineEntry: vi.fn(async (): Promise<any> => ({ code: 200 })),
  getMachineWorkLogPage: mockWorkLogPage,
  createMachineWorkLog: vi.fn(async (): Promise<any> => ({ code: 200 })),
  updateMachineWorkLog: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteMachineWorkLog: vi.fn(async (): Promise<any> => ({ code: 200 })),
  getMachineRepairPage: mockRepairPage,
  createMachineRepair: vi.fn(async (): Promise<any> => ({ code: 200 })),
  updateMachineRepair: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteMachineRepair: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mockSuccess, error: vi.fn(), warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

import ContractView from '@/views/machine/contract.vue'
import LedgerView from '@/views/machine/ledger.vue'
import EntryView from '@/views/machine/entry.vue'
import WorkLogView from '@/views/machine/work-log.vue'
import RepairView from '@/views/machine/repair.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const contractSrc = readFileSync(resolve(__testDir, '../views/machine/contract.vue'), 'utf-8')
const ledgerSrc = readFileSync(resolve(__testDir, '../views/machine/ledger.vue'), 'utf-8')
const entrySrc = readFileSync(resolve(__testDir, '../views/machine/entry.vue'), 'utf-8')
const workLogSrc = readFileSync(resolve(__testDir, '../views/machine/work-log.vue'), 'utf-8')
const repairSrc = readFileSync(resolve(__testDir, '../views/machine/repair.vue'), 'utf-8')

const stubs = {
  ProjectSelector: { template: '<div class="stub-project-selector" />', props: ['modelValue'] },
  MachineSelector: { template: '<div class="stub-machine-selector" />', props: ['modelValue'] },
  PrintButton: { template: '<div class="stub-print" />', props: ['businessType', 'businessDataId', 'variables', 'text', 'link', 'showIcon'] },
}

let wrapper: any = null
beforeEach(() => { vi.clearAllMocks() })
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

async function mountView(view: any, pageMock: any, records: any[] = []) {
  pageMock.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(view, { global: { plugins: [ElementPlus], stubs } })
  await flushPromises()
  return wrapper
}

describe('machine/contract.vue B6 矩阵', () => {
  it('B-6-1 必填规则实证修正：实为 4 条 projectId/contractName/supplierName/machineName（2026-08-17 缺陷#9 补项目字段；账本「3 条」已过时）', async () => {
    const w = await mountView(ContractView, mockContractPage)
    const rules = w.vm.$.setupState.formRules
    expect(rules.projectId[0].message).toBe('请选择项目')
    expect(rules.contractName[0].message).toBe('请输入合同名称')
    expect(rules.supplierName[0].message).toBe('请输入供应商')
    expect(rules.machineName[0].message).toBe('请输入设备名称')
    expect(Object.keys(rules)).toHaveLength(4)
  })

  it('B-6-2 合同金额 min=0 precision=2 边界（源码钉住）', async () => {
    await mountView(ContractView, mockContractPage)
    expect(contractSrc).toContain('v-model="formData.contractAmount" :min="0" :precision="2"')
  })

  it('B-6-3 租赁方式默认「月租」：初始 formData 与 handleAdd 重置后均为月租', async () => {
    const w = await mountView(ContractView, mockContractPage)
    const st = w.vm.$.setupState
    expect(st.formData.rentalType).toBe('月租')
    st.formData.rentalType = '台班'
    st.handleAdd()
    expect(st.formData.rentalType).toBe('月租')
  })

  it('B-6-6 打印变量构造：buildPrintVariables 8 字段齐全，空值回落 ""/0', async () => {
    const w = await mountView(ContractView, mockContractPage)
    const vars = w.vm.$.setupState.buildPrintVariables({ id: 1 })
    expect(vars).toEqual({
      contractCode: '', contractName: '', supplierName: '', machineName: '',
      contractAmount: 0, rentalType: '', startDate: '', endDate: '',
    })
    const full = w.vm.$.setupState.buildPrintVariables({
      contractCode: 'C1', contractName: 'N', supplierName: 'S', machineName: 'M',
      contractAmount: 100, rentalType: '月租', startDate: '2026-01-01', endDate: '2026-12-31',
    })
    expect(full.contractAmount).toBe(100)
    expect(full.endDate).toBe('2026-12-31')
  })

  it('B-6-8 实证修正：提交审批按钮已存在（盲点 13 修复 2026-08-15 决策 A），仅 DRAFT 行渲染，提交走 submitMachineContract', async () => {
    const w = await mountView(ContractView, mockContractPage, [
      { id: 1, status: 'DRAFT', contractName: 'A' },
      { id: 2, status: 'EFFECTIVE', contractName: 'B' },
    ])
    const rows = w.findAll('.el-table__row')
    expect(rows[0].text()).toContain('提交审批')
    expect(rows[1].text()).not.toContain('提交审批')
    await w.vm.$.setupState.handleSubmit({ id: 1 })
    await flushPromises()
    expect(mockSubmitContract).toHaveBeenCalledWith(1)
    expect(mockSuccess).toHaveBeenCalledWith('已提交审批')
  })
})

describe('machine/ledger.vue B7 矩阵', () => {
  it('B-7-1 必填 machineName 提示「请输入设备名称」', async () => {
    const w = await mountView(LedgerView, mockLedgerPage)
    const rules = w.vm.$.setupState.formRules
    expect(rules.machineName[0].message).toBe('请输入设备名称')
    expect(Object.keys(rules)).toHaveLength(1)
  })

  it('B-7-2 权属列 OWN→自有 / 其他→租赁', async () => {
    const w = await mountView(LedgerView, mockLedgerPage, [
      { id: 1, machineName: '挖机', ownerType: 'OWN', status: 'IN_FIELD' },
      { id: 2, machineName: '吊车', ownerType: 'RENT', status: 'REGISTERED' },
    ])
    const rows = w.findAll('.el-table__row')
    expect(rows[0].text()).toContain('自有')
    expect(rows[1].text()).toContain('租赁')
  })

  it('B-7-3 状态三态：IN_FIELD 在场 / OUT_FIELD 已退场 / 其他 已登记', async () => {
    const w = await mountView(LedgerView, mockLedgerPage, [
      { id: 1, machineName: 'A', ownerType: 'OWN', status: 'IN_FIELD' },
      { id: 2, machineName: 'B', ownerType: 'OWN', status: 'OUT_FIELD' },
      { id: 3, machineName: 'C', ownerType: 'OWN', status: 'REGISTERED' },
    ])
    const rows = w.findAll('.el-table__row')
    expect(rows[0].text()).toContain('在场')
    expect(rows[1].text()).toContain('已退场')
    expect(rows[2].text()).toContain('已登记')
  })

  it('B-7-5 新增默认 ownerType=OWN', async () => {
    const w = await mountView(LedgerView, mockLedgerPage)
    const st = w.vm.$.setupState
    expect(st.formData.ownerType).toBe('OWN')
    st.formData.ownerType = 'RENT'
    st.handleAdd()
    expect(st.formData.ownerType).toBe('OWN')
  })

  it('B-7-7 删除无前端状态守卫：handleDelete 确认后直接调 deleteMachineLedger（引用校验依赖后端 ReferenceCheck）', async () => {
    const w = await mountView(LedgerView, mockLedgerPage)
    await w.vm.$.setupState.handleDelete({ id: 42, status: 'IN_FIELD' })
    await flushPromises()
    expect(mockConfirm).toHaveBeenCalled()
    expect(mockDeleteLedger).toHaveBeenCalledWith(42)
    expect(ledgerSrc).not.toContain('ReferenceCheck')
  })
})

describe('machine/entry.vue B8 矩阵', () => {
  it('B-8-1 必填 machineId/projectId/entryType 三条提示', async () => {
    const w = await mountView(EntryView, mockEntryPage)
    const rules = w.vm.$.setupState.formRules
    expect(rules.machineId[0].message).toBe('请选择设备')
    expect(rules.projectId[0].message).toBe('请选择项目')
    expect(rules.entryType[0].message).toBe('请选择类型')
  })

  it('B-8-2 entryDate 无必填规则（前端放行，后端兜底）', async () => {
    const w = await mountView(EntryView, mockEntryPage)
    const rules = w.vm.$.setupState.formRules
    expect(rules.entryDate).toBeUndefined()
    expect(entrySrc).toContain('prop="entryDate"')
    expect(entrySrc).not.toContain("entryDate: [{ required")
  })

  it('B-8-5 类型筛选 IN/OUT + tag 颜色：进场 success/出场 danger', async () => {
    const w = await mountView(EntryView, mockEntryPage, [
      { id: 1, machineName: '挖机', entryType: 'IN', entryDate: '2026-08-01' },
      { id: 2, machineName: '挖机', entryType: 'OUT', entryDate: '2026-08-10' },
    ])
    const rows = w.findAll('.el-table__row')
    expect(rows[0].find('.el-tag--success').exists()).toBe(true)
    expect(rows[0].text()).toContain('进场')
    expect(rows[1].find('.el-tag--danger').exists()).toBe(true)
    expect(rows[1].text()).toContain('出场')
    expect(entrySrc).toContain('<el-option label="进场" value="IN" />')
    expect(entrySrc).toContain('<el-option label="出场" value="OUT" />')
  })

  it('B-8-8 设备选择器数据源：MachineSelector 组件挂载（下拉数据走真实 API，E2E 覆盖）', async () => {
    const w = await mountView(EntryView, mockEntryPage)
    w.vm.$.setupState.handleAdd()
    await flushPromises()
    expect(entrySrc).toContain('<MachineSelector v-model="formData.machineId" />')
  })
})

describe('machine/work-log.vue B9 矩阵', () => {
  it('B-9-1 必填 machineId/workDate 提示', async () => {
    const w = await mountView(WorkLogView, mockWorkLogPage)
    const rules = w.vm.$.setupState.formRules
    expect(rules.machineId[0].message).toBe('请选择设备')
    expect(rules.workDate[0].message).toBe('请选择日期')
  })

  it('B-9-2 精度钉住：shiftCount precision=1 / workQuantity precision=2 / oilConsumption precision=1，均 min=0', async () => {
    await mountView(WorkLogView, mockWorkLogPage)
    expect(workLogSrc).toContain('v-model="formData.shiftCount" :min="0" :precision="1"')
    expect(workLogSrc).toContain('v-model="formData.workQuantity" :min="0" :precision="2"')
    expect(workLogSrc).toContain('v-model="formData.oilConsumption" :min="0" :precision="1"')
  })

  it('B-9-3 结算状态展示：SETTLED success 已结算 / 其他 info 未结算', async () => {
    const w = await mountView(WorkLogView, mockWorkLogPage, [
      { id: 1, machineName: '挖机', settlementStatus: 'SETTLED' },
      { id: 2, machineName: '挖机', settlementStatus: null },
    ])
    const rows = w.findAll('.el-table__row')
    expect(rows[0].find('.el-tag--success').exists()).toBe(true)
    expect(rows[0].text()).toContain('已结算')
    expect(rows[1].find('.el-tag--info').exists()).toBe(true)
    expect(rows[1].text()).toContain('未结算')
  })

  it('B-9-7 已结算日志前端无编辑/删除禁用（盲点钉住）：SETTLED 行按钮仍渲染，守卫依赖后端', async () => {
    const w = await mountView(WorkLogView, mockWorkLogPage, [
      { id: 1, machineName: '挖机', settlementStatus: 'SETTLED' },
    ])
    const btns = w.findAll('.el-table__row button').map((b: any) => b.text()).join(' ')
    expect(btns).toContain('编辑')
    expect(btns).toContain('删除')
    expect(workLogSrc).not.toContain("settlementStatus === 'SETTLED' &&")
  })

  it('B-9-8 台班数 0 放行：min=0 无 >0 校验规则（前端不拦，语义现状钉住）', async () => {
    const w = await mountView(WorkLogView, mockWorkLogPage)
    const st = w.vm.$.setupState
    st.handleAdd()
    st.formData.shiftCount = 0
    expect(st.formData.shiftCount).toBe(0)
    expect(workLogSrc).not.toContain('shiftCount > 0')
  })
})

describe('machine/repair.vue B10 矩阵', () => {
  it('B-10-1 必填 machineId/faultDescription 提示', async () => {
    const w = await mountView(RepairView, mockRepairPage)
    const rules = w.vm.$.setupState.formRules
    expect(rules.machineId[0].message).toBe('请选择设备')
    expect(rules.faultDescription[0].message).toBe('请输入故障描述')
  })

  it('B-10-3 状态选择器仅编辑态显示：v-if="isEdit"（源码钉住）', async () => {
    await mountView(RepairView, mockRepairPage)
    expect(repairSrc).toContain('v-if="isEdit" label="状态"')
  })

  it('B-10-4 四态流转展示：已报修/已派工/维修中/已完成 tag 文案与类型', async () => {
    const w = await mountView(RepairView, mockRepairPage)
    const st = w.vm.$.setupState
    expect(st.statusText('REPORTED')).toBe('已报修')
    expect(st.statusText('DISPATCHED')).toBe('已派工')
    expect(st.statusText('REPAIRING')).toBe('维修中')
    expect(st.statusText('COMPLETED')).toBe('已完成')
    expect(st.statusTagType('DISPATCHED')).toBe('warning')
    expect(st.statusTagType('COMPLETED')).toBe('success')
  })

  it('B-10-5 维修费用 min=0 precision=2（源码钉住，负数拒绝）', async () => {
    await mountView(RepairView, mockRepairPage)
    expect(repairSrc).toContain('v-model="formData.repairCost" :min="0" :precision="2"')
  })
})
