/**
 * contract 域合同列表/产值上报/BOQ 上传页组件测试（2026-08-15 P3 收尾批 10b）
 * form.vue 已有 contract-form-view 测试、boq-utils 已有纯函数测试，本文件补页面层。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockContractPage, mockContractDelete, mockContractSubmit,
  mockOutputPage, mockOutputCreate, mockOutputSubmit,
  mockUploadBoq, mockBoqTree, mockBoqFlat, mockBoqDelete,
  mockProjectList, mockError, mockWarning,
  mockRouteParams,
} = vi.hoisted(() => ({
  mockContractPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockContractDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockContractSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockOutputPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockOutputCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockOutputSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUploadBoq: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockBoqTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockBoqFlat: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockBoqDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockError: vi.fn(),
  mockWarning: vi.fn(),
  mockRouteParams: { contractId: '55', id: '55' } as Record<string, string>,
}))

vi.mock('@/api/contract', () => ({
  getContractPage: mockContractPage, deleteContract: mockContractDelete, submitContract: mockContractSubmit,
  getOutputReportPage: mockOutputPage, createOutputReport: mockOutputCreate, submitOutputReport: mockOutputSubmit,
}))
vi.mock('@/api/boq', () => ({
  uploadBoq: mockUploadBoq, getBoqTree: mockBoqTree, getBoqFlat: mockBoqFlat, deleteBoq: mockBoqDelete,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual, // 保留 createRouter/createWebHistory（router/index.ts 间接依赖）
    useRouter: () => ({ push: vi.fn() }),
    useRoute: () => ({ query: {}, params: mockRouteParams }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: mockError, warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import ContractIndex from '@/views/contract/index.vue'
import OutputReport from '@/views/contract/output-report.vue'
import BoqUpload from '@/views/contract/boq-upload.vue'
import { ElMessageBox } from 'element-plus'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const boqUploadSrc = readFileSync(resolve(__testDir, '../views/contract/boq-upload.vue'), 'utf-8')
const boqApiSrc = readFileSync(resolve(__testDir, '../api/boq.ts'), 'utf-8')

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('contract/index.vue 施工合同列表', () => {
  async function mountPage(records: any[] = []) {
    mockContractPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(ContractIndex, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载合同分页与项目下拉', async () => {
    const w = await mountPage([{ id: 1, contractName: 'C1', status: 'DRAFT' }])
    expect(mockContractPage).toHaveBeenCalled()
    expect(mockProjectList).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(1)
  })

  it('行提交调 submitContract、删除调 deleteContract', async () => {
    await mountPage([{ id: 2, status: 'DRAFT' }])
    const st = wrapper.vm.$.setupState
    await st.handleSubmitContract({ id: 2 })
    await flushPromises()
    expect(mockContractSubmit).toHaveBeenCalledWith(2)
    await st.handleDelete({ id: 2 })
    await flushPromises()
    expect(mockContractDelete).toHaveBeenCalledWith(2)
  })

  it('搜索重置页码、重置清空条件', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.queryParams.pageNum = 3
    mockContractPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
  })
})

describe('contract/output-report.vue 产值上报', () => {
  async function mountPage(records: any[] = []) {
    mockOutputPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(OutputReport, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载产值分页与合同下拉', async () => {
    await mountPage([{ id: 1, reportPeriod: '2026-07', status: 'DRAFT' }])
    expect(mockOutputPage).toHaveBeenCalled()
    expect(wrapper.vm.$.setupState.tableData).toHaveLength(1)
  })

  it('行提交审批调 submitOutputReport', async () => {
    await mountPage([{ id: 3, status: 'DRAFT' }])
    await wrapper.vm.$.setupState.handleSubmit({ id: 3 })
    await flushPromises()
    expect(mockOutputSubmit).toHaveBeenCalledWith(3)
  })

  it('新增弹窗必填规则：项目/合同/期间钉住', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    const msgs = Object.values(st.createRules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请选择项目')
    expect(msgs.some((m: string) => m.includes('合同'))).toBe(true)
  })

  it('填充模式默认 amount、可切换 boq', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    expect(st.fillMode).toBe('amount')
    st.fillMode = 'boq'
    expect(st.fillMode).toBe('boq')
  })
})

describe('contract/boq-upload.vue BOQ 上传', () => {
  async function mountPage() {
    wrapper = mount(BoqUpload, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  function fakeFile(name: string, size: number) {
    return { raw: { name, size } } as any
  }

  it('非 xlsx 文件被拒（大小写不敏感边界：.XLSX 合法，.xls/.csv 拒绝）', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    mockError.mockClear()
    st.handleFileChange(fakeFile('清单.xls', 100))
    expect(mockError).toHaveBeenCalledWith('仅支持 .xlsx 格式文件')
    expect(st.selectedFile).toBeNull()
    st.handleFileChange(fakeFile('清单.CSV', 100))
    expect(mockError).toHaveBeenCalledTimes(2)
    // .XLSX 大写合法（盲点 4 修复行为）
    st.handleFileChange(fakeFile('清单.XLSX', 100))
    expect(st.selectedFile).toBeTruthy()
  })

  it('超过 20MB 被拒', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    mockError.mockClear()
    st.handleFileChange(fakeFile('big.xlsx', 21 * 1024 * 1024))
    expect(mockError).toHaveBeenCalledWith('文件大小不能超过 20MB')
    expect(st.selectedFile).toBeNull()
  })

  it('多文件超限提示仅允许一个', async () => {
    await mountPage()
    mockWarning.mockClear()
    wrapper.vm.$.setupState.handleExceed()
    expect(mockWarning).toHaveBeenCalledWith('仅允许上传一个文件，请先移除已选文件')
  })

  it('未选文件上传被拦', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    mockWarning.mockClear()
    await st.handleUpload()
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('请先选择文件')
    expect(mockUploadBoq).not.toHaveBeenCalled()
  })

  it('选中文件后上传调 uploadBoq（contractId 取自路由参数）', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleFileChange(fakeFile('boq.xlsx', 100))
    await st.handleUpload()
    await flushPromises()
    expect(mockUploadBoq).toHaveBeenCalledWith('55', expect.anything())
  })

  it('@matrix A9-06 未选文件时「开始上传解析」disabled（源码钉住）', async () => {
    await mountPage()
    expect(boqUploadSrc).toContain(':disabled="!selectedFile"')
    expect(wrapper.vm.$.setupState.selectedFile).toBeNull()
  })

  it('@matrix A9-09 buildTree 按 parentId 建树，孤儿/无父回落根节点', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    const tree = st.buildTree([
      { id: 1, parentId: null, itemName: '根' },
      { id: 2, parentId: 1, itemName: '子' },
      { id: 3, parentId: 999, itemName: '孤儿' },
    ])
    expect(tree).toHaveLength(2) // 根 + 孤儿回落
    expect(tree[0].children).toHaveLength(1)
    expect(tree[0].children[0].itemName).toBe('子')
    expect(tree[1].itemName).toBe('孤儿')
    // 后端已返回树形（带 children）时直用不重建
    const asIs = st.buildTree([{ id: 1, children: [{ id: 2 }] }])
    expect(asIs).toHaveLength(1)
  })

  it('@matrix A9-10 金额 2 位/数量 ≤4 位格式化，空值显示「-」', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    expect(st.formatMoney(null)).toBe('-')
    expect(st.formatMoney(1234.5)).toBe('1,234.50')
    expect(st.formatNumber(null)).toBe('-')
    expect(st.formatNumber(1.23456789)).toBe('1.2346')
    expect(st.formatNumber(3)).toBe('3')
  })

  it('@matrix A9-11 清除确认取消不发 DELETE', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    ;(ElMessageBox.confirm as any).mockRejectedValueOnce('cancel')
    mockBoqDelete.mockClear()
    await st.handleDeleteBoq().catch(() => { /* 取消即 reject */ })
    await flushPromises()
    expect(mockBoqDelete).not.toHaveBeenCalled()
  })

  it('@matrix A9-12 确认清除后树/统计清空、boqLoaded 复位', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.boqTreeData = [{ id: 1 }]
    st.uploadResult = { totalItems: 1, levelCount: 1, totalAmount: 1 }
    expect(st.boqLoaded).toBe(true)
    await st.handleDeleteBoq()
    await flushPromises()
    expect(mockBoqDelete).toHaveBeenCalledWith('55')
    expect(st.boqTreeData).toEqual([])
    expect(st.uploadResult).toBeNull()
    expect(st.boqLoaded).toBe(false)
  })

  it('@matrix A9-13 上传超时 120s 配置钉住（api/boq.ts）', async () => {
    expect(boqApiSrc).toContain('timeout: 120000')
    expect(boqUploadSrc).toContain('default-expand-all')
  })
})
