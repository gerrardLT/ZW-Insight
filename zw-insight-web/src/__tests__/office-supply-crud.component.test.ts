/**
 * hr/office-supply.vue 办公用品领用组件测试（2026-08-15 P3 方向1 续）
 * @matrix P3 长尾：人事模块页面级覆盖（CRUD 标准 6 用例）
 */
import { vi } from 'vitest'

const { mockPage, mockCreate, mockUpdate, mockDelete } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/hr', () => ({
  getOfficeSupplyPage: mockPage,
  createOfficeSupply: mockCreate,
  updateOfficeSupply: mockUpdate,
  deleteOfficeSupply: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import OfficeSupply from '@/views/hr/office-supply.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'office-supply.vue 办公用品',
  component: OfficeSupply,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增办公用品',
  requiredError: '请输入物品名称',
  records: [
    { id: 1, supplyName: '打印纸', categoryName: '办公耗材', specification: 'A4', unit: '包', stockQuantity: 50, status: 1 },
    { id: 2, supplyName: '硒鼓', categoryName: '办公耗材', specification: 'HP-88A', unit: '个', stockQuantity: 3, status: 1 },
  ],
})
