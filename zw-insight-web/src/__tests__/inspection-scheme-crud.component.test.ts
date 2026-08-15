/**
 * basedata/inspection-scheme.vue 检查方案管理组件测试（2026-08-15 P3 方向1 续）
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
  getInspectionSchemePage: mockPage,
  createInspectionScheme: mockCreate,
  updateInspectionScheme: mockUpdate,
  deleteInspectionScheme: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import InspectionScheme from '@/views/basedata/inspection-scheme.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'inspection-scheme.vue 检查方案管理',
  component: InspectionScheme,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增方案',
  requiredError: '请输入方案名称',
  records: [
    { id: 1, schemeName: '安全检查方案', schemeType: 'SAFETY', itemCount: 12 },
    { id: 2, schemeName: '质量检查方案', schemeType: 'QUALITY', itemCount: 8 },
  ],
})
