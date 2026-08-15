/**
 * machine 域四个同构 CRUD 页组件测试（2026-08-15 P3 收尾批）
 *
 * entry（设备进退场登记）/ ledger（设备台账）/ repair（维修记录）/ work-log（工作日志）
 * 均为标准 CRUD 结构，复用 crudPageSuite 工厂（每页 6 标准用例）。
 * 四页共用 @/api/machine 模块 mock（各自 import 的函数互不冲突）。
 */
import { vi } from 'vitest'

const {
  mockEntryPage, mockEntryCreate, mockEntryUpdate, mockEntryDelete,
  mockLedgerPage, mockLedgerCreate, mockLedgerUpdate, mockLedgerDelete,
  mockRepairPage, mockRepairCreate, mockRepairUpdate, mockRepairDelete,
  mockWorkLogPage, mockWorkLogCreate, mockWorkLogUpdate, mockWorkLogDelete,
} = vi.hoisted(() => {
  const page = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockEntryPage: page(), mockEntryCreate: ok(), mockEntryUpdate: ok(), mockEntryDelete: ok(),
    mockLedgerPage: page(), mockLedgerCreate: ok(), mockLedgerUpdate: ok(), mockLedgerDelete: ok(),
    mockRepairPage: page(), mockRepairCreate: ok(), mockRepairUpdate: ok(), mockRepairDelete: ok(),
    mockWorkLogPage: page(), mockWorkLogCreate: ok(), mockWorkLogUpdate: ok(), mockWorkLogDelete: ok(),
  }
})

vi.mock('@/api/machine', () => ({
  getMachineEntryPage: mockEntryPage, createMachineEntry: mockEntryCreate, updateMachineEntry: mockEntryUpdate, deleteMachineEntry: mockEntryDelete,
  getMachineLedgerPage: mockLedgerPage, createMachineLedger: mockLedgerCreate, updateMachineLedger: mockLedgerUpdate, deleteMachineLedger: mockLedgerDelete,
  getMachineRepairPage: mockRepairPage, createMachineRepair: mockRepairCreate, updateMachineRepair: mockRepairUpdate, deleteMachineRepair: mockRepairDelete,
  getMachineWorkLogPage: mockWorkLogPage, createMachineWorkLog: mockWorkLogCreate, updateMachineWorkLog: mockWorkLogUpdate, deleteMachineWorkLog: mockWorkLogDelete,
}))
// entry/repair/work-log 内嵌 ProjectSelector 子组件使用（MachineSelector 用的
// getMachineLedgerPage 已在上方模块 mock 中），mock 防真实请求
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

import MachineEntry from '@/views/machine/entry.vue'
import MachineLedger from '@/views/machine/ledger.vue'
import MachineRepair from '@/views/machine/repair.vue'
import MachineWorkLog from '@/views/machine/work-log.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'machine/entry.vue 设备进退场登记',
  component: MachineEntry,
  pageMock: mockEntryPage, createMock: mockEntryCreate, updateMock: mockEntryUpdate, deleteMock: mockEntryDelete,
  addButtonText: '新增登记',
  requiredError: '请选择设备',
  records: [
    { id: 1, machineName: '塔吊A', entryType: 'ENTRY', entryDate: '2026-07-01', projectName: '滨江花园一期', status: 'ACTIVE' },
    { id: 2, machineName: '施工电梯B', entryType: 'EXIT', entryDate: '2026-07-15', projectName: '城南市政', status: 'EXITED' },
  ],
})

crudPageSuite({
  title: 'machine/ledger.vue 设备台账',
  component: MachineLedger,
  pageMock: mockLedgerPage, createMock: mockLedgerCreate, updateMock: mockLedgerUpdate, deleteMock: mockLedgerDelete,
  addButtonText: '新增设备',
  requiredError: '请输入设备名称',
  records: [
    { id: 1, machineName: '塔吊A', machineCode: 'MC-001', machineType: '起重机械', specification: 'QTZ63', status: 'NORMAL' },
    { id: 2, machineName: '挖掘机B', machineCode: 'MC-002', machineType: '土方机械', specification: 'PC200', status: 'REPAIRING' },
  ],
})

crudPageSuite({
  title: 'machine/repair.vue 维修记录',
  component: MachineRepair,
  pageMock: mockRepairPage, createMock: mockRepairCreate, updateMock: mockRepairUpdate, deleteMock: mockRepairDelete,
  addButtonText: '新增维修记录',
  requiredError: '请选择设备',
  records: [
    { id: 1, machineName: '塔吊A', repairDate: '2026-07-10', repairCost: 5000, repairDesc: '更换钢丝绳', status: 'COMPLETED' },
    { id: 2, machineName: '挖掘机B', repairDate: '2026-07-20', repairCost: 12000, repairDesc: '发动机检修', status: 'REPAIRING' },
  ],
})

crudPageSuite({
  title: 'machine/work-log.vue 工作日志',
  component: MachineWorkLog,
  pageMock: mockWorkLogPage, createMock: mockWorkLogCreate, updateMock: mockWorkLogUpdate, deleteMock: mockWorkLogDelete,
  addButtonText: '新增工作日志',
  requiredError: '请选择设备',
  records: [
    { id: 1, machineName: '塔吊A', workDate: '2026-08-01', workHours: 8, operator: '张师傅', remark: '正常作业' },
    { id: 2, machineName: '挖掘机B', workDate: '2026-08-02', workHours: 6, operator: '李师傅', remark: '土方开挖' },
  ],
})
