/**
 * basedata/material.vue 材料字典管理组件测试（2026-08-15 P3 长尾补测）
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
  getMaterialDictPage: mockPage,
  createMaterialDict: mockCreate,
  updateMaterialDict: mockUpdate,
  deleteMaterialDict: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Material from '@/views/basedata/material.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'material.vue 材料字典管理',
  component: Material,
  pageKey: 'page', // 材料字典页已统一为 page/size 口径（T1 接线）
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增材料',
  requiredError: '请输入材料名称',
  records: [
    { id: 1, materialName: '螺纹钢', categoryName: '钢材', specification: 'HRB400', unit: '吨', referencePrice: 4000 },
    { id: 2, materialName: '水泥', categoryName: '建材', specification: 'P.O 42.5', unit: '吨', referencePrice: 500 },
  ],
})
