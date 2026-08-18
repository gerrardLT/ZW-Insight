/**
 * M2 账本补齐：A10 产值上报 views/contract/output-report.vue 矩阵用例
 * （P3 批 contract-pages 已有调度级覆盖；本文件补校验规则/BOQ 联动/金额计算/
 * details 过滤/按钮状态守卫等字段级断言）
 *
 * @matrix A10-02/A10-03/A10-04/A10-05/A10-06/A10-07/A10-08/A10-09/A10-10/A10-11/A10-13/A10-14/A10-15
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockOutputPage, mockOutputCreate, mockOutputSubmit,
  mockContractPage, mockBoqFlat, mockProjectList, mockWarning,
} = vi.hoisted(() => ({
  mockOutputPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockOutputCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockOutputSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockContractPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockBoqFlat: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockWarning: vi.fn(),
}))

vi.mock('@/api/contract', () => ({
  getOutputReportPage: mockOutputPage,
  createOutputReport: mockOutputCreate,
  submitOutputReport: mockOutputSubmit,
  getContractPage: mockContractPage,
}))
vi.mock('@/api/boq', () => ({
  getBoqFlat: mockBoqFlat,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn() }),
    useRoute: () => ({ query: {}, params: {} }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import OutputReport from '@/views/contract/output-report.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const outputVueSrc = readFileSync(resolve(__testDir, '../views/contract/output-report.vue'), 'utf-8')

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(records: any[] = []) {
  mockOutputPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(OutputReport, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('contract/output-report.vue A10 矩阵', () => {
  it('A10-03/A10-04 新增必填规则与「本期产值须大于0」min:0.01 钉住', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    const rules = st.createRules
    expect(rules.projectId[0].message).toBe('请选择项目')
    expect(rules.contractId[0].message).toBe('请选择施工合同')
    expect(rules.reportPeriod[0].message).toBe('请选择报告期间')
    expect(rules.currentOutput[0]).toMatchObject({ type: 'number', required: true, min: 0.01 })
    expect(rules.currentOutput[0].message).toBe('本期产值须大于0')
  })

  it('A10-02 合同级联仅拉取 EFFECTIVE 合同（pageSize=100），查询区与弹窗各自独立', async () => {
    await mountPage()
    mockContractPage.mockClear()
    await wrapper.vm.$.setupState.loadContractOptions(9, 'query')
    expect(mockContractPage).toHaveBeenCalledWith({ pageNum: 1, pageSize: 100, projectId: 9, status: 'EFFECTIVE' })
    // 未选项目时清空选项不发请求
    mockContractPage.mockClear()
    await wrapper.vm.$.setupState.loadContractOptions(undefined, 'query')
    expect(mockContractPage).not.toHaveBeenCalled()
    expect(wrapper.vm.$.setupState.contractOptions).toEqual([])
  })

  it('A10-05 无 BOQ 合同保持纯金额模式；A10-06 有 BOQ 自动切清单模式', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    // 无清单
    mockBoqFlat.mockResolvedValue({ code: 200, data: [] })
    st.createForm.contractId = 1
    await st.handleFormContractChange()
    await flushPromises()
    expect(st.fillMode).toBe('amount')
    expect(st.boqItems).toEqual([])
    // 有清单
    mockBoqFlat.mockResolvedValue({ code: 200, data: [{ id: 11, itemCode: 'A1', itemName: '挖方', unit: 'm³', unitPrice: 10, quantity: 100 }] })
    await st.handleFormContractChange()
    await flushPromises()
    expect(st.fillMode).toBe('boq')
    expect(st.boqItems).toHaveLength(1)
    expect(st.boqItems[0].reportQuantity).toBe(0)
  })

  it('A10-07/A10-08 行金额=q×p 舍入 2 位（0.33×3=0.99 无浮点误差），合计随完成量重算', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    expect(st.lineAmount({ reportQuantity: 3, unitPrice: 0.33 })).toBe(0.99)
    expect(st.lineAmount({ reportQuantity: 0, unitPrice: 100 })).toBe(0)
    st.boqItems = [
      { id: 1, reportQuantity: 3, unitPrice: 0.33 },
      { id: 2, reportQuantity: 2, unitPrice: 5 },
    ]
    st.createForm.currentOutput = 0
    st.recalcFromBoq()
    expect(st.createForm.currentOutput).toBe(10.99)
  })

  it('A10-09 boq 模式全部完成量为 0 → warning 拦截且不发创建请求', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    // 弹窗打开后 createFormRef 才绑定（未开启时 handleCreateSubmit 提前返回）
    st.createDialogVisible = true
    await flushPromises()
    st.createForm.projectId = 1
    st.createForm.contractId = 2
    st.createForm.reportPeriod = '2026-08'
    st.createForm.currentOutput = 10 // 绕过数值必填规则，触达明细守卫
    st.fillMode = 'boq'
    st.boqItems = [{ id: 1, reportQuantity: 0, unitPrice: 5 }]
    mockOutputCreate.mockClear()
    mockWarning.mockClear()
    await st.handleCreateSubmit()
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('请至少填写一条清单行的本期完成量')
    expect(mockOutputCreate).not.toHaveBeenCalled()
  })

  it('A10-10 草稿保存 details 仅含 reportQuantity>0 行且带 amount；纯金额模式无 details', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.createDialogVisible = true
    await flushPromises()
    st.createForm.projectId = 1
    st.createForm.contractId = 2
    st.createForm.reportPeriod = '2026-08'
    st.createForm.currentOutput = 10
    // boq 模式：部分行有量
    st.fillMode = 'boq'
    st.boqItems = [
      { id: 11, reportQuantity: 2, unitPrice: 5 },
      { id: 12, reportQuantity: 0, unitPrice: 99 },
    ]
    mockOutputCreate.mockClear()
    await st.handleCreateSubmit()
    await flushPromises()
    expect(mockOutputCreate).toHaveBeenCalledTimes(1)
    const payload = (mockOutputCreate.mock.calls as any)[0][0]
    expect(payload.details).toEqual([{ boqItemId: 11, quantity: 2, amount: 10 }])
    // 纯金额模式：details 为 undefined
    st.fillMode = 'amount'
    mockOutputCreate.mockClear()
    await st.handleCreateSubmit()
    await flushPromises()
    expect((mockOutputCreate.mock.calls as any)[0][0].details).toBeUndefined()
  })

  it('A10-11 提交按钮仅 DRAFT/REJECTED 行渲染', async () => {
    const w = await mountPage([
      { id: 1, status: 'DRAFT', currentOutput: 1 },
      { id: 2, status: 'SUBMITTED', currentOutput: 1 },
      { id: 3, status: 'APPROVED', currentOutput: 1 },
      { id: 4, status: 'REJECTED', currentOutput: 1 },
    ])
    const rows = w.findAll('.el-table__row')
    const btnText = (row: any) => row.findAll('button').map((b: any) => b.text()).join(' ')
    expect(btnText(rows[0])).toContain('提交')
    expect(btnText(rows[1])).not.toContain('提交')
    expect(btnText(rows[2])).not.toContain('提交')
    expect(btnText(rows[3])).toContain('提交')
    // 四态标签映射
    const st = wrapper.vm.$.setupState
    expect(st.getStatusLabel('DRAFT')).toBe('草稿')
    expect(st.getStatusLabel('SUBMITTED')).toBe('审批中')
    expect(st.getStatusLabel('APPROVED')).toBe('已通过')
    expect(st.getStatusLabel('REJECTED')).toBe('已驳回')
  })

  it('A10-13 切换项目复位合同/BOQ/填报模式并重新拉合同选项（**2026-08 实证**：currentOutput 不在项目切换时复位，仅开启弹窗时重置）', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.createForm.projectId = 3 // loadContractOptions 在 projectId 为空时提前 return
    st.createForm.contractId = 5
    st.boqItems = [{ id: 1 }]
    st.fillMode = 'boq'
    st.createForm.currentOutput = 88
    mockContractPage.mockClear()
    st.handleFormProjectChange()
    await flushPromises()
    expect(st.createForm.contractId).toBeUndefined()
    expect(st.boqItems).toEqual([])
    expect(st.fillMode).toBe('amount')
    expect(st.createForm.currentOutput).toBe(88) // 实证：不随项目切换复位
    expect(mockContractPage).toHaveBeenCalled()
    // openCreateDialog 才全量复位（含金额）
    st.openCreateDialog()
    await flushPromises()
    expect(st.createForm.currentOutput).toBe(0)
  })

  it('A10-14 重置清空查询条件与合同选项并重载', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.queryParams.projectId = 3
    st.queryParams.contractId = 4
    st.queryParams.pageNum = 2
    st.contractOptions = [{ id: 4 }]
    mockOutputPage.mockClear()
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.projectId).toBeUndefined()
    expect(st.queryParams.contractId).toBeUndefined()
    expect(st.queryParams.pageNum).toBe(1)
    expect(st.contractOptions).toEqual([])
    expect(mockOutputPage).toHaveBeenCalled()
  })

  it('A10-15 分页 page-sizes [10,20,50] 源码钉住', async () => {
    await mountPage()
    expect(outputVueSrc).toContain(':page-sizes="[10, 20, 50]"')
  })
})
