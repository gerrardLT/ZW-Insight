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
  title: 'office-supply.vue 办公用品领用',
  component: OfficeSupply,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增领用申请',
  requiredError: '请输入物品名称',
  records: [
    { id: 1, applyNo: 'OS-001', itemName: '打印纸', specification: 'A4', quantity: 5, applicant: '张三', applyDate: '2026-08-01', status: 'PENDING' },
    { id: 2, applyNo: 'OS-002', itemName: '硒鼓', specification: 'HP-88A', quantity: 2, applicant: '李四', applyDate: '2026-08-02', status: 'APPROVED' },
  ],
})
