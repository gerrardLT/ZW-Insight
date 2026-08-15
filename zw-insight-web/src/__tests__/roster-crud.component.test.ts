/**
 * labor/roster.vue 劳务花名册组件测试（2026-08-15 P3 方向1 第三批）
 * @matrix P3 长尾：劳务模块页面级覆盖（CRUD 标准 6 用例）
 */
import { vi } from 'vitest'

const { mockPage, mockCreate, mockUpdate, mockDelete } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/labor', () => ({
  getLaborRosterPage: mockPage,
  createLaborRoster: mockCreate,
  updateLaborRoster: mockUpdate,
  deleteLaborRoster: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Roster from '@/views/labor/roster.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'roster.vue 劳务花名册',
  component: Roster,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增人员',
  requiredError: '请输入姓名',
  records: [
    { id: 1, name: '张工', idCard: '330101199001011234', phone: '13800000001', teamName: '木工一班', status: 'ACTIVE' },
    { id: 2, name: '李工', idCard: '330101199202022345', phone: '13800000002', teamName: '钢筋一班', status: 'ACTIVE' },
  ],
})
