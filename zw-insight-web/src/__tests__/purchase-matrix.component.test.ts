/**
 * 采购管理三页矩阵组件测试（账本全量补齐 M5 B-5，2026-08）
 *
 * 覆盖账本 B22 采购合同 / B23 采购结算 / B24 询价比价 的纯前端守卫增量。
 * 与既有测试边界（不重复）：
 *   - purchase-contract-crud.component.test.ts：DRAFT 行提交调用（B-22-7 提交调用）
 *   - settlement-docs.component.test.ts：结算页加载/合同切换联动/行提交删除调用
 *   - E2E expense-write.spec.ts：结算 UI 写路径 + B-P-X1 询价定标链路
 *
 * @matrix B-22-1/B-22-2/B-22-3/B-22-7/B-22-8
 * @matrix B-23-1/B-23-2/B-23-3/B-23-5/B-23-6/B-23-8/B-23-9/B-23-11/B-23-12
 * @matrix B-24-1/B-24-2/B-24-3/B-24-4/B-24-6/B-24-8
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const contractSrc = readFileSync(resolve(__dirname, '../views/purchase/contract.vue'), 'utf-8')
const settlementSrc = readFileSync(resolve(__dirname, '../views/purchase/settlement.vue'), 'utf-8')
const inquirySrc = readFileSync(resolve(__dirname, '../views/purchase/inquiry.vue'), 'utf-8')

const mocks = vi.hoisted(() => ({
  mockContractPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockContractCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockContractUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockContractDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockContractSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSettlePage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockSettleCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSettleUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSettleDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSettleSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockAvailInbounds: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockInquiryPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockInquiryCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockInquiryUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockInquiryDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockInquiryPublish: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockQuotationList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockCalcRanking: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockConfirmWinner: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockBidResult: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockSuccess: vi.fn(),
  mockWarning: vi.fn(),
  mockError: vi.fn(),
}))

vi.mock('@/api/purchase', () => ({
  getPurchaseContractPage: mocks.mockContractPage,
  createPurchaseContract: mocks.mockContractCreate,
  updatePurchaseContract: mocks.mockContractUpdate,
  deletePurchaseContract: mocks.mockContractDelete,
  submitPurchaseContract: mocks.mockContractSubmit,
  getPurchaseSettlementPage: mocks.mockSettlePage,
  createPurchaseSettlement: mocks.mockSettleCreate,
  updatePurchaseSettlement: mocks.mockSettleUpdate,
  deletePurchaseSettlement: mocks.mockSettleDelete,
  submitPurchaseSettlement: mocks.mockSettleSubmit,
  getAvailableInbounds: mocks.mockAvailInbounds,
  getInquiryPage: mocks.mockInquiryPage,
  createInquiry: mocks.mockInquiryCreate,
  updateInquiry: mocks.mockInquiryUpdate,
  deleteInquiry: mocks.mockInquiryDelete,
  publishInquiry: mocks.mockInquiryPublish,
  getQuotationList: mocks.mockQuotationList,
  calculateBidRanking: mocks.mockCalcRanking,
  confirmBidWinner: mocks.mockConfirmWinner,
  getBidResultByInquiry: mocks.mockBidResult,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mocks.mockSuccess, warning: mocks.mockWarning, error: mocks.mockError, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Contract from '@/views/purchase/contract.vue'
import Settlement from '@/views/purchase/settlement.vue'
import Inquiry from '@/views/purchase/inquiry.vue'
import ElementPlus from 'element-plus'

const globalCfg = {
  plugins: [ElementPlus],
  stubs: { ProjectSelector: true },
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.mockContractPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  mocks.mockSettlePage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  mocks.mockInquiryPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  mocks.mockAvailInbounds.mockResolvedValue({ code: 200, data: [] })
})

describe('purchase/contract.vue 采购合同（B22）', () => {
  async function mountContract() {
    const w = mount(Contract, { global: globalCfg })
    await flushPromises()
    return w
  }

  it('@matrix B-22-1 必填四条（projectId/contractName/supplierName/contractAmount，实证较账本多 projectId——缺陷#7 修复）', async () => {
    const w = await mountContract()
    const st: any = w.vm.$.setupState
    const keys = Object.keys(st.formRules)
    expect(keys).toEqual(expect.arrayContaining(['projectId', 'contractName', 'supplierName', 'contractAmount']))
    expect(keys).toHaveLength(4)
    expect(st.formRules.projectId[0].message).toBe('请选择项目')
    expect(st.formRules.contractName[0].message).toBe('请输入合同名称')
    expect(st.formRules.supplierName[0].message).toBe('请输入供应商名称')
    expect(st.formRules.contractAmount[0].message).toBe('请输入合同金额')
  })

  it('@matrix B-22-2 金额 input-number min=0 precision=2 源码钉住', () => {
    expect(contractSrc).toContain(':min="0" :precision="2"')
  })

  it('@matrix B-22-3 搜索重置 pageNum 归 1；重置清空名称/供应商/状态', async () => {
    const w = await mountContract()
    const st: any = w.vm.$.setupState
    st.queryParams.pageNum = 3
    st.queryParams.contractName = 'X'
    st.queryParams.supplierName = 'Y'
    st.queryParams.status = 'EFFECTIVE'
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    st.queryParams.pageNum = 5
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ pageNum: 1, pageSize: 10, contractName: '', supplierName: '', status: '' })
  })

  it('@matrix B-22-7 提交按钮仅 DRAFT 渲染（原「页面无提交按钮」已过时，盲点13 已补齐）', () => {
    expect(contractSrc).toContain('v-if="row.status === \'DRAFT\'" link type="success" @click="handleSubmit(row)"')
  })

  it('@matrix B-22-8 现状钉住：编辑/删除按钮无状态条件（EFFECTIVE 亦可点，依赖后端「仅草稿状态可编辑」守卫）', () => {
    const editCount = contractSrc.match(/@click="handleEdit\(row\)"/g)?.length || 0
    const delCount = contractSrc.match(/@click="handleDelete\(row\)"/g)?.length || 0
    expect(editCount).toBe(1)
    expect(delCount).toBe(1)
    // 编辑/删除行外无 v-if 状态条件（与结算页 DRAFT 分支不一致，现状钉住）
    const editLine = contractSrc.split('\n').find(l => l.includes('@click="handleEdit(row)"'))!
    expect(editLine).not.toContain('v-if')
  })

  it('@matrix B-22-1 新增保存 payload 含 projectId（缺陷#7 修复钉住：projectId=null 曾致 INSERT 500）', async () => {
    const w = await mountContract()
    const st: any = w.vm.$.setupState
    st.formRef = { validate: async () => true }
    st.handleAdd()
    st.formData.projectId = 77
    st.formData.contractName = 'C1'
    st.formData.supplierName = 'S1'
    st.formData.contractAmount = 50000
    await st.handleFormSubmit()
    expect(mocks.mockContractCreate).toHaveBeenCalledWith(expect.objectContaining({ projectId: 77, contractName: 'C1', contractAmount: 50000 }))
  })
})

describe('purchase/settlement.vue 采购结算（B23）', () => {
  async function mountSettlement() {
    mocks.mockContractPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, contractName: 'C1' }], total: 1 } })
    const w = mount(Settlement, { global: globalCfg })
    await flushPromises()
    return w
  }

  it('@matrix B-23-1 必填三条 contractId/inboundId/settlementAmount', async () => {
    const w = await mountSettlement()
    const st: any = w.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['contractId', 'inboundId', 'settlementAmount'])
    expect(st.formRules.contractId[0].message).toBe('请选择关联合同')
    expect(st.formRules.inboundId[0].message).toBe('请选择关联入库单')
    expect(st.formRules.settlementAmount[0].message).toBe('请输入结算金额')
  })

  it('@matrix B-23-2 入库单下拉未选合同前禁用 + B-23-8 编辑态双锁定（源码钉住）', () => {
    expect(settlementSrc).toContain(':disabled="isEdit || !formData.contractId"')
    expect(settlementSrc).toContain(':disabled="isEdit"')
  })

  it('@matrix B-23-3 空候选提示文案源码钉住', () => {
    expect(settlementSrc).toContain('该合同暂无可结算的已审批入库单')
  })

  it('@matrix B-23-5 选入库单回填入库金额（handleInboundChange）', async () => {
    const w = await mountSettlement()
    const st: any = w.vm.$.setupState
    st.handleAdd()
    st.inboundOptions = [{ id: 9, inboundCode: 'IN1', totalAmount: '1234.5' }]
    st.formData.inboundId = 9
    st.handleInboundChange()
    expect(st.formData.inboundAmount).toBe(1234.5)
    st.formData.inboundId = 404
    st.handleInboundChange()
    expect(st.formData.inboundAmount).toBe(0)
  })

  it('@matrix B-23-6 双保险第一重：input-number :max=inboundAmount 钳制超额值（v-show 渲染实证）', async () => {
    const w = await mountSettlement()
    const st: any = w.vm.$.setupState
    st.handleAdd()
    st.formData.contractId = 1
    st.formData.inboundId = 9
    st.formData.inboundAmount = 100000
    st.formData.settlementAmount = 120000
    await flushPromises() // el-input-number 挂载后把超 max 值钳回 inboundAmount
    expect(st.formData.settlementAmount, '超额值被 :max 钳回入库金额').toBe(100000)
  })

  it('@matrix B-23-6 双保险第二重：逻辑层 warning 拦截（关闭弹窗隔离 UI 钳制，formRef 注入绕过 validate）', async () => {
    const w = await mountSettlement()
    const st: any = w.vm.$.setupState
    st.handleAdd()
    st.dialogVisible = false
    await flushPromises()
    st.formRef = { validate: async () => true }
    st.formData.contractId = 1
    st.formData.inboundId = 9
    st.formData.inboundAmount = 100000
    st.formData.settlementAmount = 120000
    await st.handleFormSubmit()
    expect(mocks.mockWarning).toHaveBeenCalledWith('结算金额不能大于入库金额')
    expect(mocks.mockSettleCreate).not.toHaveBeenCalled()
  })

  it('@matrix B-23-6 金额不超入库金额正常创建', async () => {
    const w = await mountSettlement()
    const st: any = w.vm.$.setupState
    st.handleAdd()
    st.formRef = { validate: async () => true }
    st.formData.contractId = 1
    st.formData.inboundId = 9
    st.formData.inboundAmount = 100000
    st.formData.settlementAmount = 80000
    await st.handleFormSubmit()
    expect(mocks.mockSettleCreate).toHaveBeenCalledTimes(1)
    expect(mocks.mockSuccess).toHaveBeenCalledWith('新增成功')
  })

  it('@matrix B-23-9 草稿行三按钮 vs 已审批行文本（源码钉住）', () => {
    expect(settlementSrc).toContain('<template v-if="row.status === \'DRAFT\'">')
    expect(settlementSrc).toContain('<span v-else style="color: #909399">已审批</span>')
  })

  it('@matrix B-23-11 合同筛选 change 即搜索（源码钉住 @change=handleSearch）', () => {
    const line = settlementSrc.split('\n').find(l => l.includes('v-model="queryParams.contractId"'))!
    expect(line).toContain('@change="handleSearch"')
  })

  it('@matrix B-23-12 formatAmount 两位小数千分位', async () => {
    const w = await mountSettlement()
    const st: any = w.vm.$.setupState
    expect(st.formatAmount(1234567.5)).toBe('1,234,567.50')
    expect(st.formatAmount(null)).toBe('0.00')
    expect(st.formatAmount('99')).toBe('99.00')
  })
})

describe('purchase/inquiry.vue 询价比价（B24）', () => {
  async function mountInquiry() {
    const w = mount(Inquiry, { global: globalCfg })
    await flushPromises()
    return w
  }

  it('@matrix B-24-1 必填三条 title/materialName/quantity', async () => {
    const w = await mountInquiry()
    const st: any = w.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['title', 'materialName', 'quantity'])
    expect(st.formRules.title[0].message).toBe('请输入询价标题')
    expect(st.formRules.materialName[0].message).toBe('请输入材料名称')
    expect(st.formRules.quantity[0].message).toBe('请输入数量')
  })

  it('@matrix B-24-2 数量 min=1 源码钉住', () => {
    expect(inquirySrc).toContain(':min="1"')
  })

  it('@matrix B-24-3 buildInquiryPayload 组装 items 数组 + deadline 归一化 datetime（Jackson 500 缺陷修复钉住）', async () => {
    const w = await mountInquiry()
    const st: any = w.vm.$.setupState
    st.handleAdd()
    st.formData.title = 'T1'
    st.formData.materialName = '钢筋'
    st.formData.specification = 'Φ20'
    st.formData.quantity = 10
    st.formData.deadline = '2026-09-01'
    st.formData.requirement = '加急'
    const payload = st.buildInquiryPayload()
    expect(payload.deadline).toBe('2026-09-01T00:00:00')
    expect(payload.materialSummary).toBe('钢筋')
    expect(payload.description).toBe('加急')
    expect(payload.items).toEqual([{ materialName: '钢筋', specification: 'Φ20', quantity: 10 }])
    // 已含 T 的 datetime 不重复拼接
    st.formData.deadline = '2026-09-01T18:00:00'
    expect(st.buildInquiryPayload().deadline).toBe('2026-09-01T18:00:00')
    // 未填 deadline 为 undefined
    st.formData.deadline = ''
    expect(st.buildInquiryPayload().deadline).toBeUndefined()
  })

  it('@matrix B-24-4 发布仅 DRAFT 可见 + 比价定标 PUBLISHED/QUOTED/AWARDED 可见（源码钉住）', () => {
    expect(inquirySrc).toContain('v-if="row.status === \'DRAFT\'" link type="success" @click="handlePublish(row)"')
    expect(inquirySrc).toContain('v-if="row.status === \'PUBLISHED\' || row.status === \'QUOTED\' || row.status === \'AWARDED\'"')
  })

  it('@matrix B-24-6 状态展示含 ANNOUNCED 但筛选下拉无此选项（现状钉住）', () => {
    expect(inquirySrc).toContain("row.status === 'ANNOUNCED' ? '已公示'")
    const filterBlock = inquirySrc.split('v-model="queryParams.status"')[1].split('</el-select>')[0]
    expect(filterBlock).not.toContain('ANNOUNCED')
  })

  it('@matrix B-24-8 现状钉住：编辑按钮无状态条件（已发布单可点编辑，依赖后端守卫）', () => {
    const editLine = inquirySrc.split('\n').find(l => l.includes('@click="handleEdit(row)"'))!
    expect(editLine).not.toContain('v-if')
  })

  it('@matrix B-P-X1 比价定标：空排名 warning「暂无已提交的报价」', async () => {
    const w = await mountInquiry()
    const st: any = w.vm.$.setupState
    st.bidRow = { id: 5, status: 'PUBLISHED', title: 'T' }
    mocks.mockCalcRanking.mockResolvedValueOnce({ code: 200, data: [] })
    await st.handleCalculateRanking()
    expect(mocks.mockCalcRanking).toHaveBeenCalledWith(5)
    expect(mocks.mockWarning).toHaveBeenCalledWith('暂无已提交的报价，无法计算排名')
  })

  it('@matrix B-P-X1 确认定标 payload { inquiryId, supplierId } + 状态翻转 AWARDED', async () => {
    const w = await mountInquiry()
    const st: any = w.vm.$.setupState
    st.bidRow = { id: 5, status: 'QUOTED', title: 'T' }
    mocks.mockBidResult.mockResolvedValueOnce({ code: 200, data: [{ ranking: 1, supplierName: 'S', isWinner: 1 }] })
    await st.handleConfirmWinner({ supplierId: 88, supplierName: 'S' })
    expect(mocks.mockConfirmWinner).toHaveBeenCalledWith({ inquiryId: 5, supplierId: 88 })
    expect(st.bidRow.status).toBe('AWARDED')
    expect(mocks.mockSuccess).toHaveBeenCalledWith('定标成功')
  })
})
