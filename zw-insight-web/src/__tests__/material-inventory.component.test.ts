/**
 * P3-M5 材料盘点页面组件测试
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockGetPage,
  mockGetDetail,
  mockGetStockPage,
  mockCreate,
  mockUpdate,
  mockDelete,
  mockSubmit
} = vi.hoisted(() => ({
  mockGetPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockGetDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: { details: [] } })),
  mockGetStockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmit: vi.fn(async (): Promise<any> => ({ code: 200 }))
}))

vi.mock('@/api/material', () => ({
  getMaterialCheckPage: mockGetPage,
  getMaterialCheckDetail: mockGetDetail,
  getMaterialStockPage: mockGetStockPage,
  createMaterialCheck: mockCreate,
  updateMaterialCheck: mockUpdate,
  deleteMaterialCheck: mockDelete,
  submitMaterialCheck: mockSubmit
}))

vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async () => ({ code: 200, data: [{ id: 90001, projectName: '滨江一期' }] }))
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') }
  }
})

import MaterialInventory from '@/views/material/inventory.vue'

describe('views/material/inventory.vue 材料盘点', () => {
  let wrapper: any = null

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetPage.mockResolvedValue({
      code: 200,
      data: {
        records: [
          { id: 1, projectId: 90001, projectName: '滨江一期', inventoryDate: '2026-10-01', status: 'DRAFT' },
          { id: 2, projectId: 90001, projectName: '滨江一期', inventoryDate: '2026-10-02', status: 'APPROVED' }
        ],
        total: 2
      }
    })
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.unmount()
      wrapper = null
    }
  })

  it('挂载后加载盘点单列表并渲染', async () => {
    wrapper = mount(MaterialInventory, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(mockGetPage).toHaveBeenCalled()
    const rows = wrapper.findAll('.el-table__row')
    expect(rows).toHaveLength(2)
  })

  it('DRAFT 状态行支持点击确认生效调用 submitMaterialCheck', async () => {
    wrapper = mount(MaterialInventory, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleSubmit({ id: 1 })
    await flushPromises()

    expect(mockSubmit).toHaveBeenCalledWith(1)
  })

  it('新建盘点从项目库存载入并组装 adjustments 契约', async () => {
    mockGetStockPage.mockResolvedValue({
      code: 200,
      data: {
        records: [
          { id: 88, materialName: '钢筋', specification: 'HRB400', unit: '吨', stockQuantity: 12.5 }
        ],
        total: 1
      }
    })
    wrapper = mount(MaterialInventory, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.formData.projectId = 90001
    await vm.loadStockRows()
    vm.formData.details[0].actualQuantity = 11
    await vm.handleSaveForm()
    await flushPromises()

    expect(mockCreate).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 90001,
      adjustments: { '88': 11 }
    }))
  })

  it('DRAFT 状态行支持删除', async () => {
    wrapper = mount(MaterialInventory, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleDelete({ id: 1 })
    await flushPromises()

    expect(mockDelete).toHaveBeenCalledWith(1)
  })
})
