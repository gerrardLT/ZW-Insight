/**
 * tender/certificate.vue 证件管理组件测试（2026-08-15 P3 方向1 续）
 * @matrix P3 长尾：投标模块页面级覆盖（CRUD 标准 6 用例）
 */
import { vi } from 'vitest'

const { mockPage, mockCreate, mockUpdate, mockDelete } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/tender', () => ({
  getCertificatePage: mockPage,
  createCertificate: mockCreate,
  updateCertificate: mockUpdate,
  deleteCertificate: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Certificate from '@/views/tender/certificate.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'certificate.vue 证件管理',
  component: Certificate,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增证件',
  requiredError: '请输入持证人',
  // 2026-08-21 契约对齐：本地状态 pageNum/pageSize，请求实参映射为后端 page/size
  pageArgKey: 'page',
  // certificate.vue 删除恒为双参 deleteCertificate('person', row.id)
  deleteExpectedArgs: (row) => ['person', row.id],
  // 字段对齐后端实体 BizPersonCertificate：personName/certificateType/certificateNo/issueDate/expireDate
  records: [
    { id: 1, personName: '张三', certificateType: '一级建造师', certificateNo: 'JZ20200001', issueDate: '2020-01-01', expireDate: '2026-01-01', status: 1 },
    { id: 2, personName: '李四', certificateType: '安全生产考核合格证', certificateNo: 'AQ20210002', issueDate: '2021-05-01', expireDate: '2027-05-01', status: 1 },
  ],
})
