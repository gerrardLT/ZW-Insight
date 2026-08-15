/**
 * hr/vehicle.vue 车辆管理组件测试（2026-08-15 P3 方向1 续）
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
  getVehiclePage: mockPage,
  createVehicle: mockCreate,
  updateVehicle: mockUpdate,
  deleteVehicle: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Vehicle from '@/views/hr/vehicle.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'vehicle.vue 车辆管理',
  component: Vehicle,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增车辆',
  requiredError: '请输入车牌号',
  records: [
    { id: 1, plateNumber: '浙A12345', vehicleType: '轿车', brand: '大众帕萨特', driver: '王五', department: '工程部', insuranceExpiry: '2026-12-31', inspectionExpiry: '2027-06-30' },
    { id: 2, plateNumber: '浙B67890', vehicleType: '货车', brand: '东风天龙', driver: '赵六', department: '物资部', insuranceExpiry: '2026-10-31', inspectionExpiry: '2027-03-31' },
  ],
})
