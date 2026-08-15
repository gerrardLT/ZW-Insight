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
  requiredError: '请输入证件名称',
  // certificate.vue 删除为双参 deleteCertificate(row.type || 'person', row.id)
  deleteExpectedArgs: (row) => [row.type || 'person', row.id],
  records: [
    { id: 1, type: 'person', certName: '一级建造师', certNo: 'JZ20200001', holderName: '张三', issueDate: '2020-01-01', expiryDate: '2026-01-01', issueOrgan: '住建部' },
    { id: 2, type: 'person', certName: '安全生产考核合格证', certNo: 'AQ20210002', holderName: '李四', issueDate: '2021-05-01', expiryDate: '2027-05-01', issueOrgan: '住建厅' },
  ],
})
