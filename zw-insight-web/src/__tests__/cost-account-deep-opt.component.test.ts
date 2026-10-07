/**
 * P2-M4 预算与 CBS 成本账户深挖测试
 * 覆盖 CBS 树加载、账户关闭、台账流水映射
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockGetTree,
  mockGetPage,
  mockLock,
  mockClose,
  mockGetLedger
} = vi.hoisted(() => ({
  mockGetTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockGetPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockLock: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockClose: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockGetLedger: vi.fn(async (): Promise<any> => ({ code: 200, data: [] }))
}))

vi.mock('@/api/cost-account', () => ({
  getCostAccountTree: mockGetTree,
  getCostAccountPage: mockGetPage,
  lockCostAccount: mockLock,
  closeCostAccount: mockClose,
  getCostAccountLedger: mockGetLedger,
  createCostAccount: vi.fn(async () => ({ code: 200 })),
  updateCostAccount: vi.fn(async () => ({ code: 200 })),
  deleteCostAccount: vi.fn(async () => ({ code: 200 })),
  costRollUp: vi.fn(async () => ({ code: 200, data: { postedCount: 0, duplicateCount: 0, failed: [], unmapped: [] } })),
  bindCostAccountLink: vi.fn(async () => ({ code: 200 }))
}))

vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async () => ({ code: 200, data: [{ id: 90001, projectName: '测试项目' }] }))
}))

vi.mock('@/api/wbs', () => ({
  getWbsSelectList: vi.fn(async () => ({ code: 200, data: [] }))
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') }
  }
})

import CostAccountIndex from '@/views/budget/cost-account/index.vue'

describe('CostAccountIndex 成本账户管理 (P2-M4 B1/A1)', () => {
  let wrapper: any = null

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.unmount()
      wrapper = null
    }
  })

  it('选择项目后无过滤条件优先加载服务端全量树并拉平（B1，防 children 被 assembleTree 重置）', async () => {
    // 服务端 /tree 返回「根节点嵌套 children」结构
    mockGetTree.mockResolvedValue({
      code: 200,
      data: [
        {
          id: 1, accountCode: '01', accountName: '材料费', costCategory: 'MATERIAL', status: 'ACTIVE',
          children: [
            { id: 2, parentId: 1, accountCode: '0101', accountName: '主材', costCategory: 'MATERIAL', status: 'ACTIVE', children: [] }
          ]
        }
      ]
    })

    wrapper = mount(CostAccountIndex, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.queryParams.projectId = 90001
    await vm.loadPage()
    await flushPromises()

    expect(mockGetTree).toHaveBeenCalledWith(90001)
    // 拉平后根+子均入 rows（否则 totals 只汇总根、树只剩根）
    expect(vm.rows).toHaveLength(2)
    // 重装树后子节点挂回根下（回归钉住：旧实现会把服务端 children 重置为空）
    const tree = vm.pagedRows
    expect(tree).toHaveLength(1)
    expect(tree[0].children).toHaveLength(1)
    expect(tree[0].children[0].id).toBe(2)
  })

  it('支持关闭账户 (B1 / closeCostAccount)', async () => {
    wrapper = mount(CostAccountIndex, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.queryParams.projectId = 90001
    await vm.handleClose({ id: 88, accountName: '试验费' })
    await flushPromises()

    expect(mockClose).toHaveBeenCalledWith(88)
  })
})
