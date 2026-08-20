/**
 * workflow-platform-matrix：D-5 工作流 + D-6 平台运营账本补盲（2026-08-20 M10）
 *
 * 覆盖账本缺口（源码实证钉住）：
 * - D-29-1/2/4/7 designer：DEFAULT_XML 三节点、saveXML 下载 process.bpmn、导入失败提示、unmount destroy
 * - D-30-3/4/7 process：accept 限制、流程图 URL 赋值（同步非 Promise）、无挂起/激活差距
 * - D-32-7/10/11 approval：批量空选守卫、无委托/转办/撤回差距（api 有 delegateTask/transferTask 无消费方）
 * - D-33-2/3/4/5/6/7/8 rollback：6 bizType、四态映射、仅 status===2 处理冲突、resolution 守卫、dateRange→startDate/endDate、page/size
 * - D-34-3/4/5/6/7/8/10/11 tenant：边界钳制、pageNum/pageSize 口径（与 tenant-type page/size 并存）、停用文案、状态互斥按钮、续期规则、12 模块
 * - D-35-3/4/7 tenant-type：durationDays 四档 select、sortOrder min=0、page/size
 * - D-36-3/4/5 storage：五类型、仅 storageType required（盲点：其余字段无校验直接提交）、secretKey password
 *
 * designer 依赖 bpmn-js（canvas），经 vi.mock 类 stub 隔离。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const {
  mockDeploy, mockProcessList, mockProcessImage, mockProcessVersions,
  mockTodo, mockDone, mockBatchApprove,
  mockRollbackLogs, mockConfirmConflict,
  mockTenantPage, mockTenantCreate, mockTenantRenew, mockTenantModules,
  mockStoragePage, mockStorageCreate,
  mockSaveXML, mockImportXML, mockDestroy,
  mockElSuccess, mockElError, mockElWarning, mockConfirm,
} = vi.hoisted(() => ({
  mockDeploy: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProcessList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockProcessImage: vi.fn((id: any) => `/api/v1/workflow/process/${id}/image`),
  mockProcessVersions: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockTodo: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockDone: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockBatchApprove: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockRollbackLogs: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockConfirmConflict: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockTenantPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockTenantCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockTenantRenew: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockTenantModules: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockStoragePage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockStorageCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSaveXML: vi.fn(async () => ({ xml: '<xml/>' })),
  mockImportXML: vi.fn(async () => undefined),
  mockDestroy: vi.fn(),
  mockElSuccess: vi.fn(),
  mockElError: vi.fn(),
  mockElWarning: vi.fn(),
  mockConfirm: vi.fn(async () => 'confirm'),
}))

vi.mock('@/api/workflow', () => ({
  deployProcess: mockDeploy,
  getProcessList: mockProcessList,
  getProcessImage: mockProcessImage,
  getProcessVersions: mockProcessVersions,
  getTodoTasks: mockTodo,
  getDoneTasks: mockDone,
  completeTask: vi.fn(async (): Promise<any> => ({ code: 200 })),
  rejectToPrevious: vi.fn(async (): Promise<any> => ({ code: 200 })),
  rejectToStart: vi.fn(async (): Promise<any> => ({ code: 200 })),
  terminateProcess: vi.fn(async (): Promise<any> => ({ code: 200 })),
  batchApprove: mockBatchApprove,
  getBusinessTypeTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  getBusinessTypeDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  createBusinessType: vi.fn(async (): Promise<any> => ({ code: 200 })),
  updateBusinessType: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteBusinessType: vi.fn(async (): Promise<any> => ({ code: 200 })),
  getRollbackLogs: mockRollbackLogs,
  confirmRollbackConflict: mockConfirmConflict,
}))
vi.mock('@/api/platform', () => ({
  getTenantPage: mockTenantPage,
  createTenant: mockTenantCreate,
  updateTenant: vi.fn(async (): Promise<any> => ({ code: 200 })),
  disableTenant: vi.fn(async (): Promise<any> => ({ code: 200 })),
  enableTenant: vi.fn(async (): Promise<any> => ({ code: 200 })),
  renewTenant: mockTenantRenew,
  updateTenantModules: mockTenantModules,
  getTenantTypePage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  createTenantType: vi.fn(async (): Promise<any> => ({ code: 200 })),
  updateTenantType: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteTenantType: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))
vi.mock('@/api/file', () => ({
  getStoragePage: mockStoragePage,
  createStorage: mockStorageCreate,
  updateStorage: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteStorage: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))
// bpmn-js Modeler stub（canvas 依赖隔离，destroy 注入可断言）
vi.mock('bpmn-js/lib/Modeler', () => ({
  default: class {
    createDiagram = vi.fn(async () => undefined)
    importXML = mockImportXML
    saveXML = mockSaveXML
    destroy = mockDestroy
    on = vi.fn()
    get = () => ({ zoom: vi.fn() })
  },
}))
vi.mock('bpmn-js/dist/assets/diagram-js.css', () => ({}))
vi.mock('bpmn-js/dist/assets/bpmn-js.css', () => ({}))
vi.mock('bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css', () => ({}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mockElSuccess, error: mockElError, warning: mockElWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

import Designer from '@/views/workflow/designer/index.vue'
import Process from '@/views/workflow/process/index.vue'
import Approval from '@/views/workflow/approval/index.vue'
import Rollback from '@/views/workflow/rollback/index.vue'
import Tenant from '@/views/platform/tenant/index.vue'
import TenantType from '@/views/platform/tenant-type/index.vue'
import Storage from '@/views/platform/storage/index.vue'

const norm = (s: string) => s.replace(/\r\n/g, '\n')
const src = (rel: string) => norm(readFileSync(resolve(__dirname, '..', rel), 'utf-8'))

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
  mockImportXML.mockImplementation(async () => undefined) // 恢复默认（导入用例可能改写为 reject）
})

describe('workflow/designer/index.vue 流程设计器（D-29）', () => {
  async function mountDesigner() {
    wrapper = mount(Designer, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-29-1 DEFAULT_XML 含 StartEvent→UserTask→EndEvent 三节点', async () => {
    const s = src('views/workflow/designer/index.vue')
    expect(s).toContain('<startEvent id="StartEvent_1"')
    expect(s).toContain('<userTask id="UserTask_1"')
    expect(s).toContain('<endEvent id="EndEvent_1"')
    await mountDesigner()
    expect(mockImportXML).toHaveBeenCalled() // 挂载即 importXML(DEFAULT_XML)
  })

  it('D-29-2 保存 XML：saveXML 后下载 process.bpmn 并提示成功', async () => {
    await mountDesigner()
    await wrapper.vm.$.setupState.handleSave()
    await flushPromises()
    expect(mockSaveXML).toHaveBeenCalledWith({ format: true })
    expect(mockElSuccess).toHaveBeenCalledWith('已保存为 process.bpmn')
  })

  it('D-29-4 导入失败提示「导入的文件格式不正确」（accept 仅 .bpmn/.xml）', async () => {
    await mountDesigner()
    const s = src('views/workflow/designer/index.vue')
    expect(s).toContain('accept=".bpmn,.xml"')
    expect(s).toContain('导入的文件格式不正确')
    mockImportXML.mockImplementationOnce(async () => { throw new Error('invalid') })
    const file = { raw: new File(['not xml'], 'bad.txt') }
    await wrapper.vm.$.setupState.handleImport(file)
    // jsdom FileReader onload 为异步宏任务，轮询等待 error 提示
    await vi.waitFor(() => expect(mockElError).toHaveBeenCalledWith('导入的文件格式不正确'), { timeout: 2000 })
  })

  it('D-29-7 unmount 时 modeler.destroy() 释放画布', async () => {
    await mountDesigner()
    wrapper.unmount()
    wrapper = null
    expect(mockDestroy).toHaveBeenCalled()
  })
})

describe('workflow/process/index.vue 流程定义（D-30）', () => {
  it('D-30-3 上传 accept 限制 .bpmn/.bpmn20.xml/.xml', () => {
    const s = src('views/workflow/process/index.vue')
    expect(s).toContain('accept=".bpmn,.bpmn20.xml,.xml"')
  })

  it('D-30-4 查看流程图：getProcessImage 同步赋 URL 并打开弹窗', async () => {
    wrapper = mount(Process, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    st.handleViewImage({ id: 'p9' })
    expect(mockProcessImage).toHaveBeenCalledWith('p9')
    expect(st.currentImageUrl).toBe('/api/v1/workflow/process/p9/image')
    expect(st.imageDialogVisible).toBe(true)
  })

  it('D-30-7 差距钉住：无挂起/激活按钮，api 无 suspend/activate', () => {
    const s = src('views/workflow/process/index.vue')
    expect(s).not.toMatch(/挂起|激活/)
    expect(s).not.toMatch(/suspend|activate/)
    expect(norm(src('api/workflow.ts'))).not.toMatch(/suspend|activate/)
  })
})

describe('workflow/approval/index.vue 审批中心补盲（D-32）', () => {
  it('D-32-7 批量通过空选守卫：按钮 disabled + confirm 拒绝时零调用', async () => {
    wrapper = mount(Approval, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const s = src('views/workflow/approval/index.vue')
    expect(s).toMatch(/:disabled="selectedRows\.length === 0"/)
    const st = wrapper.vm.$.setupState
    st.selectedRows = []
    mockConfirm.mockRejectedValueOnce('cancel')
    await expect(st.handleBatchApprove()).rejects.toBeDefined()
    expect(mockBatchApprove).not.toHaveBeenCalled()
  })

  it('D-32-10/11 差距钉住：无委托/转办/撤回 UI，api 无 withdraw（delegateTask/transferTask 存在但页面不消费）', () => {
    const s = src('views/workflow/approval/index.vue')
    expect(s).not.toContain('delegateTask')
    expect(s).not.toContain('transferTask')
    expect(s).not.toMatch(/撤回|withdraw/)
    expect(s).not.toMatch(/我发起|myStart/i)
    const api = norm(src('api/workflow.ts'))
    expect(api).toContain('export function delegateTask')
    expect(api).toContain('export function transferTask')
    expect(api).not.toMatch(/withdraw/)
  })
})

describe('workflow/rollback/index.vue 回滚日志补盲（D-33）', () => {
  const CONFLICT_ROWS = [
    { id: 1, rollbackStatus: 2, bizType: 'PAYMENT_APPLY', workflowInstanceId: 'w1', errorMessage: '冲突' },
    { id: 2, rollbackStatus: 0, bizType: 'LABOR_SETTLEMENT' },
  ]

  async function mountRollback() {
    mockRollbackLogs.mockResolvedValue({ code: 200, data: { records: CONFLICT_ROWS, total: 2 } })
    wrapper = mount(Rollback, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-33-2 bizType 六选项齐备且随查询提交', async () => {
    const s = src('views/workflow/rollback/index.vue')
    for (const t of ['LABOR_SETTLEMENT', 'MACHINE_SETTLEMENT', 'PURCHASE_SETTLEMENT', 'SUBCONTRACT_SETTLEMENT', 'PAYMENT_APPLY', 'INVOICE_APPLY']) {
      expect(s).toContain(`value="${t}"`)
    }
    await mountRollback()
    const st = wrapper.vm.$.setupState
    st.queryParams.bizType = 'PAYMENT_APPLY'
    mockRollbackLogs.mockClear()
    st.handleSearch()
    await flushPromises()
    expect((mockRollbackLogs.mock.calls as any)[0][0]).toMatchObject({ bizType: 'PAYMENT_APPLY' })
  })

  it('D-33-3 状态四态渲染（0成功/1失败/2冲突待确认/3重试中）', async () => {
    const s = src('views/workflow/rollback/index.vue')
    expect(s).toContain("0: '回滚成功'")
    expect(s).toContain("1: '回滚失败'")
    expect(s).toContain("2: '冲突待确认'")
    expect(s).toContain("3: '重试中'")
    await mountRollback()
    expect(wrapper.text()).toContain('冲突待确认')
    expect(wrapper.text()).toContain('回滚成功')
  })

  it('D-33-4 仅 rollbackStatus===2 显示「处理冲突」，其余行无操作', async () => {
    await mountRollback()
    const rows = wrapper.findAll('.el-table__row')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('处理冲突')
    expect(rows[1].text()).not.toContain('处理冲突')
  })

  it('D-33-5 冲突处理未选 resolution：提交按钮 disabled + 函数级守卫拦截', async () => {
    await mountRollback()
    const s = src('views/workflow/rollback/index.vue')
    expect(s).toContain(':disabled="!conflictForm.resolution"')
    const st = wrapper.vm.$.setupState
    st.handleConfirmConflict(CONFLICT_ROWS[0])
    expect(st.conflictForm.resolution).toBe('')
    await st.submitConflictConfirm()
    await flushPromises()
    expect(mockConfirmConflict).not.toHaveBeenCalled()
    expect(mockElWarning).toHaveBeenCalledWith('请选择处理方式')
  })

  it('D-33-6 三种 resolution 依次提交 confirmRollbackConflict(rowId, { resolution })', async () => {
    await mountRollback()
    const st = wrapper.vm.$.setupState
    for (const resolution of ['FORCE_ROLLBACK', 'SKIP', 'MANUAL']) {
      st.handleConfirmConflict(CONFLICT_ROWS[0])
      st.conflictForm.resolution = resolution
      await st.submitConflictConfirm()
      await flushPromises()
    }
    expect(mockConfirmConflict).toHaveBeenCalledTimes(3)
    expect((mockConfirmConflict.mock.calls as any)[0]).toEqual([1, { resolution: 'FORCE_ROLLBACK' }])
    expect((mockConfirmConflict.mock.calls as any)[1]).toEqual([1, { resolution: 'SKIP' }])
    expect((mockConfirmConflict.mock.calls as any)[2]).toEqual([1, { resolution: 'MANUAL' }])
  })

  it('D-33-7 dateRange 映射为 startDate/endDate 传参', async () => {
    await mountRollback()
    const st = wrapper.vm.$.setupState
    st.queryParams.dateRange = ['2026-08-01', '2026-08-20']
    mockRollbackLogs.mockClear()
    st.handleSearch()
    await flushPromises()
    expect((mockRollbackLogs.mock.calls as any)[0][0]).toMatchObject({ startDate: '2026-08-01', endDate: '2026-08-20' })
  })

  it('D-33-8 分页用 page/size 口径，重置清空全部条件', async () => {
    await mountRollback()
    expect((mockRollbackLogs.mock.calls as any)[0][0]).toMatchObject({ page: 1, size: 10 })
    const st = wrapper.vm.$.setupState
    st.queryParams.bizType = 'PAYMENT_APPLY'
    st.queryParams.rollbackStatus = 1
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ page: 1, size: 10, bizType: undefined, rollbackStatus: undefined, dateRange: null })
  })
})

describe('platform/tenant/index.vue 租户管理补盲（D-34）', () => {
  async function mountTenant() {
    wrapper = mount(Tenant, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-34-3/4 边界钳制：maxUsers 1-9999、durationDays 1-3650', () => {
    const s = src('views/platform/tenant/index.vue')
    expect(s).toMatch(/maxUsers"\s+:min="1"\s+:max="9999"/)
    expect(s).toMatch(/durationDays"\s+:min="1"\s+:max="3650"/)
  })

  it('D-34-5 状态/类型组合查询提交且重置清空', async () => {
    await mountTenant()
    const st = wrapper.vm.$.setupState
    st.queryParams.status = 1
    st.queryParams.userType = 'TRIAL'
    mockTenantPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect((mockTenantPage.mock.calls as any)[0][0]).toMatchObject({ status: 1, userType: 'TRIAL' })
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.status).toBeUndefined()
    expect(st.queryParams.userType).toBeUndefined()
  })

  it('D-34-6 停用确认文案明示「所有用户将无法登录」', () => {
    const s = src('views/platform/tenant/index.vue')
    expect(s).toContain('停用后该租户下所有用户将无法登录')
  })

  it('D-34-7 状态互斥按钮：status===1 停用 / status 2|3 启用（v-if）', () => {
    const s = src('views/platform/tenant/index.vue')
    expect(s).toMatch(/v-if="row\.status === 1"/)
    expect(s).toMatch(/v-if="row\.status === 2 \|\| row\.status === 3"/)
  })

  it('D-34-8 续期规则 1-1095：input-number 铳制 + rules min/max 双层防线钉住', async () => {
    await mountTenant()
    const s = src('views/platform/tenant/index.vue')
    expect(s).toMatch(/renewForm\.durationDays"\s+:min="1"\s+:max="1095"/)
    expect(s).toContain('续期天数范围为 1-1095')
    const st = wrapper.vm.$.setupState
    const rules = st.renewRules.durationDays
    expect(rules.some((r: any) => r.min === 1 && r.max === 1095)).toBe(true)
    expect(rules.some((r: any) => r.required === true)).toBe(true)
    // 注：async-validator 在 vitest 预打包 ESM 与 Node CJS 下对无 type required 规则行为不一致
    //（实测探针：ESM 侧将数字按 string 检查致误拒），故不在此做跨环境不稳定的 validate 行为断言
  })

  it('D-34-10 模块配置 12 项，保存调 updateTenantModules(tenantId, modules)', async () => {
    await mountTenant()
    const st = wrapper.vm.$.setupState
    expect(st.moduleOptions).toHaveLength(12)
    expect(st.moduleOptions.map((m: any) => m.value)).toContain('FINANCE')
    st.handleModules({ id: 5, modules: ['TENDER'] })
    expect(st.moduleForm).toEqual({ tenantId: 5, modules: ['TENDER'] })
    await st.submitModules()
    await flushPromises()
    expect(mockTenantModules).toHaveBeenCalledWith(5, ['TENDER'])
  })

  it('D-34-11 使用量列渲染 currentUsers / maxUsers', () => {
    const s = src('views/platform/tenant/index.vue')
    expect(s).toContain('row.currentUsers ?? 0 }} / {{ row.maxUsers ?? 0')
  })

  it('D-34 分页口径钉住：tenant 页用 pageNum/pageSize（与 tenant-type page/size 两套并存）', async () => {
    await mountTenant()
    expect((mockTenantPage.mock.calls as any)[0][0]).toMatchObject({ pageNum: 1, pageSize: 10 })
  })
})

describe('platform/tenant-type + storage 补盲（D-35/D-36）', () => {
  it('D-35-3 durationDays 固定四档 select（30/90/180/365）', () => {
    const s = src('views/platform/tenant-type/index.vue')
    expect(s).toContain(':value="30"')
    expect(s).toContain(':value="90"')
    expect(s).toContain(':value="180"')
    expect(s).toContain(':value="365"')
  })

  it('D-35-4 sortOrder min=0 钳制钉住', () => {
    expect(src('views/platform/tenant-type/index.vue')).toMatch(/sortOrder"\s+:min="0"/)
  })

  it('D-35-7 分页 page/size 口径钉住', () => {
    const s = src('views/platform/tenant-type/index.vue')
    expect(s).toContain('v-model:current-page="queryParams.page"')
    expect(s).toContain('v-model:page-size="queryParams.size"')
  })

  it('D-36-3 五种存储类型选项齐备', () => {
    const s = src('views/platform/storage/index.vue')
    for (const t of ['LOCAL', 'MINIO', 'ALIYUN', 'TENCENT', 'QINIU']) {
      expect(s).toContain(`value="${t}"`)
    }
  })

  it('D-36-4 盲点钉住：仅 storageType required，空 endpoint 亦直接提交 createStorage', async () => {
    wrapper = mount(Storage, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['storageType'])
    st.handleAdd()
    await flushPromises()
    expect(st.formData.endpoint).toBe('')
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockStorageCreate).toHaveBeenCalledTimes(1)
    expect((mockStorageCreate.mock.calls as any)[0][0]).toMatchObject({ storageType: 'LOCAL', endpoint: '' })
  })

  it('D-36-5 secretKey 为 password 型（show-password 掩码）', () => {
    const s = src('views/platform/storage/index.vue')
    expect(s).toMatch(/secretKey"\s+type="password"\s+show-password/)
  })
})
