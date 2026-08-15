/**
 * basedata/owner.vue 甲方单位管理组件测试（2026-08-15 P3 方向1 续）
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
  getOwnerPage: mockPage,
  createOwner: mockCreate,
  updateOwner: mockUpdate,
  deleteOwner: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Owner from '@/views/basedata/owner.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'owner.vue 甲方单位管理',
  component: Owner,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增甲方单位',
  requiredError: '请输入甲方名称',
  records: [
    { id: 1, ownerName: '城投集团', contactPerson: '王五', contactPhone: '13900000001' },
    { id: 2, ownerName: '交投集团', contactPerson: '赵六', contactPhone: '13900000002' },
  ],
})
