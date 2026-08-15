/**
 * basedata/company.vue 公司管理组件测试（2026-08-15 P3 方向1 续）
 * @matrix P3 长尾：基础数据模块页面级覆盖（CRUD 标准 6 用例）
 */
import { vi } from 'vitest'

const { mockPage, mockCreate, mockUpdate, mockDelete } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/basedata', () => ({
  getCompanyPage: mockPage,
  createCompany: mockCreate,
  updateCompany: mockUpdate,
  deleteCompany: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Company from '@/views/basedata/company.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'company.vue 公司管理',
  component: Company,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增公司',
  requiredError: '请输入公司名称',
  records: [
    { id: 1, companyName: '甲公司', creditCode: '91110000A1', contactPerson: '张三', contactPhone: '13800000001' },
    { id: 2, companyName: '乙公司', creditCode: '91110000B2', contactPerson: '李四', contactPhone: '13800000002' },
  ],
})
