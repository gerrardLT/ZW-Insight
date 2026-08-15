/**
 * basedata/supplier.vue 供应商管理组件测试（2026-08-15 P3 长尾补测）
 * @matrix P3 长尾：基础数据模块页面级覆盖（CRUD 标准 6 用例）
 */
import { vi } from 'vitest'

const {
  mockPage, mockCreate, mockUpdate, mockDelete,
} = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/basedata', () => ({
  getSupplierPage: mockPage,
  createSupplier: mockCreate,
  updateSupplier: mockUpdate,
  deleteSupplier: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Supplier from '@/views/basedata/supplier.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'supplier.vue 供应商管理',
  component: Supplier,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增供应商',
  requiredError: '请输入供应商名称',
  records: [
    { id: 1, supplierName: 'E2E供应商A', supplierType: 'MATERIAL', contactName: '张', contactPhone: '13800000000', address: '杭州', creditCode: '91330000MA0000000X' },
    { id: 2, supplierName: 'E2E供应商B', supplierType: 'LABOR', contactName: '李', contactPhone: '13800000001', address: '上海', creditCode: '91330000MA0000001Y' },
  ],
})
