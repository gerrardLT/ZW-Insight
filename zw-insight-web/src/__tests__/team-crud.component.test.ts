/**
 * labor/team.vue 班组管理组件测试（2026-08-15 P3 长尾补测）
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
  getLaborTeamPage: mockPage,
  createLaborTeam: mockCreate,
  updateLaborTeam: mockUpdate,
  deleteLaborTeam: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Team from '@/views/labor/team.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'team.vue 班组管理',
  component: Team,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增班组',
  requiredError: '请输入班组名称',
  records: [
    { id: 1, teamName: 'E2E班组A', leaderName: '张', leaderPhone: '13800000000', workType: '木工', memberCount: 10 },
    { id: 2, teamName: 'E2E班组B', leaderName: '李', leaderPhone: '13800000001', workType: '钢筋工', memberCount: 8 },
  ],
})
