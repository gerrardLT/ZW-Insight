/**
 * site 域施工日志与进度计划页组件测试（2026-08-15 P3 收尾批）
 * 两页均为标准 CRUD + ProjectSelector 子组件（mock @/api/project 隔离），
 * 复用 crudPageSuite 工厂。schedule 额外预载计划树（getSchedulePlanTree）。
 */
import { vi } from 'vitest'

const {
  mockLogPage, mockLogCreate, mockLogUpdate, mockLogDelete,
  mockSchedulePage, mockScheduleCreate, mockScheduleUpdate, mockScheduleDelete, mockPlanTree,
} = vi.hoisted(() => {
  const page = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockLogPage: page(), mockLogCreate: ok(), mockLogUpdate: ok(), mockLogDelete: ok(),
    mockSchedulePage: page(), mockScheduleCreate: ok(), mockScheduleUpdate: ok(), mockScheduleDelete: ok(),
    mockPlanTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  }
})

vi.mock('@/api/site', () => ({
  getConstructionLogPage: mockLogPage, createConstructionLog: mockLogCreate, updateConstructionLog: mockLogUpdate, deleteConstructionLog: mockLogDelete,
  getSchedulePage: mockSchedulePage, getSchedulePlanTree: mockPlanTree, createSchedule: mockScheduleCreate, updateSchedule: mockScheduleUpdate, deleteSchedule: mockScheduleDelete,
}))
// 两页内嵌 ProjectSelector 子组件会调 getProjectList，mock 防真实请求
vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import ConstructionLog from '@/views/site/construction-log.vue'
import Schedule from '@/views/site/schedule.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'site/construction-log.vue 施工日志',
  component: ConstructionLog,
  pageMock: mockLogPage, createMock: mockLogCreate, updateMock: mockLogUpdate, deleteMock: mockLogDelete,
  addButtonText: '新增施工日志',
  requiredError: '请选择项目',
  records: [
    { id: 1, projectName: '滨江花园一期', logDate: '2026-08-01', weather: '晴', content: '主体结构施工', recorder: '张工' },
    { id: 2, projectName: '城南市政', logDate: '2026-08-02', weather: '多云', content: '路基回填', recorder: '李工' },
  ],
})

crudPageSuite({
  title: 'site/schedule.vue 进度计划',
  component: Schedule,
  pageMock: mockSchedulePage, createMock: mockScheduleCreate, updateMock: mockScheduleUpdate, deleteMock: mockScheduleDelete,
  addButtonText: '新增进度计划',
  requiredError: '请输入任务名称',
  records: [
    { id: 1, projectName: '滨江花园一期', taskName: '基础施工', planStartDate: '2026-01-01', planEndDate: '2026-03-31', status: 'IN_PROGRESS' },
    { id: 2, projectName: '城南市政', taskName: '路基工程', planStartDate: '2026-02-01', planEndDate: '2026-05-31', status: 'COMPLETED' },
  ],
})
