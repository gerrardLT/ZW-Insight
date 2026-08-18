/**
 * M2 账本补齐：A7 施工合同列表 views/contract/index.vue 矩阵用例
 * （2026-08 账本全量补齐计划 M2；P3 批 contract-pages 已有 handler 调度级覆盖，
 * 本文件补 formatMoney/statusMap/按钮守卫/查看只读跳转等字段级断言）
 *
 * @matrix A7-02/A7-03/A7-04/A7-08/A7-09/A7-10/A7-11/A7-12
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockContractPage, mockContractDelete, mockContractSubmit,
  mockProjectList, mockPush,
} = vi.hoisted(() => ({
  mockContractPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockContractDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockContractSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockPush: vi.fn(),
}))

vi.mock('@/api/contract', () => ({
  getContractPage: mockContractPage,
  deleteContract: mockContractDelete,
  submitContract: mockContractSubmit,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: mockPush }),
    useRoute: () => ({ query: {}, params: {} }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import ContractIndex from '@/views/contract/index.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
// 模板静态属性断言（page-sizes 等渲染后不在 DOM 属性上，直接钉源码）
const indexVueSrc = readFileSync(resolve(__testDir, '../views/contract/index.vue'), 'utf-8')

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(records: any[] = []) {
  mockContractPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(ContractIndex, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('contract/index.vue A7 矩阵', () => {
  it('A7-03 formatMoney 边界：null/undefined→「-」，0→「0.00」，大额千分位 2 位小数', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    expect(st.formatMoney(null)).toBe('-')
    expect(st.formatMoney(undefined)).toBe('-')
    expect(st.formatMoney(0)).toBe('0.00')
    expect(st.formatMoney(1234567.8)).toBe('1,234,567.80')
  })

  it('A7-04 操作列守卫：仅 DRAFT 行渲染编辑/提交/删除，EFFECTIVE 行仅查看+打印', async () => {
    const w = await mountPage([
      { id: 1, status: 'DRAFT', contractAmount: 100 },
      { id: 2, status: 'EFFECTIVE', contractAmount: 200 },
    ])
    const rows = w.findAll('.el-table__row')
    const draftBtns = rows[0].findAll('button').map((b: any) => b.text()).join(' ')
    const effBtns = rows[1].findAll('button').map((b: any) => b.text()).join(' ')
    expect(draftBtns).toContain('编辑')
    expect(draftBtns).toContain('提交')
    expect(draftBtns).toContain('删除')
    expect(effBtns).not.toContain('编辑')
    expect(effBtns).not.toContain('提交')
    expect(effBtns).not.toContain('删除')
    expect(effBtns).toContain('查看')
  })

  it('A7-08 查看入口携 ?mode=view 进只读页（2026-08 实证修正：账本原预期「同跳编辑页」已过时，P0 盲点 3 修复后携 mode=view）', async () => {
    await mountPage()
    wrapper.vm.$.setupState.handleView({ id: '2089728215595675650' })
    expect(mockPush).toHaveBeenCalledWith('/contract/edit/2089728215595675650?mode=view')
    mockPush.mockClear()
    wrapper.vm.$.setupState.handleEdit({ id: 8 })
    expect(mockPush).toHaveBeenCalledWith('/contract/edit/8')
  })

  it('A7-10 statusMap 五态标签与 tag 类型；未知状态回落原文与 info', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    expect(st.getStatusLabel('DRAFT')).toBe('草稿')
    expect(st.getStatusLabel('SUBMITTED')).toBe('审批中')
    expect(st.getStatusLabel('EFFECTIVE')).toBe('已生效')
    expect(st.getStatusLabel('SETTLED')).toBe('已结算')
    expect(st.getStatusLabel('CLOSED')).toBe('已关闭')
    expect(st.getStatusType('EFFECTIVE')).toBe('success')
    expect(st.getStatusType('CLOSED')).toBe('danger')
    expect(st.getStatusLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(st.getStatusType('UNKNOWN')).toBe('info')
  })

  it('A7-11 分页 page-sizes [10,20,50,100] 源码钉住', async () => {
    await mountPage()
    expect(indexVueSrc).toContain(':page-sizes="[10, 20, 50, 100]"')
  })

  it('A7-12 重置清空 projectId/status 并重载', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.queryParams.projectId = 5
    st.queryParams.status = 'EFFECTIVE'
    st.queryParams.pageNum = 3
    mockContractPage.mockClear()
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.projectId).toBeUndefined()
    expect(st.queryParams.status).toBe('')
    expect(st.queryParams.pageNum).toBe(1)
    expect(mockContractPage).toHaveBeenCalled()
  })

  it('A7-02 项目远程搜索以 projectName 透传 getProjectList', async () => {
    await mountPage()
    mockProjectList.mockClear()
    await wrapper.vm.$.setupState.searchProject('滨江')
    expect(mockProjectList).toHaveBeenCalledWith({ projectName: '滨江' })
  })

  it('A7-09 每行渲染 PrintButton（business-type=CONTRACT 源码钉住）', async () => {
    const w = await mountPage([{ id: 1, status: 'DRAFT' }])
    expect(indexVueSrc).toContain('business-type="CONTRACT"')
    expect(w.findAll('.el-table__row')).toHaveLength(1)
  })
})
