/**
 * workflow 域与系统模板页组件测试（2026-08-15 P3 收尾批 14，web 页面级收官）
 * designer 依赖 bpmn-js（canvas），经 vi.mock 类 stub 隔离。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockTodo, mockDone, mockComplete, mockRejectPrev, mockRejectStart, mockTerminate, mockBatchApprove,
  mockBtTree, mockBtDetail, mockBtCreate, mockBtUpdate, mockBtDelete,
  mockDeploy, mockProcessList, mockProcessImage, mockProcessVersions,
  mockRollbackLogs, mockConfirmConflict,
  mockPrintPage, mockPrintCreate, mockPrintUpdate, mockPrintDelete, mockPrintRender,
  mockBatchTemplateList, mockBatchCreate, mockBatchUpdate, mockBatchDelete,
  mockSaveXML, mockImportXML,
  mockUpdateProperties, modelerConstructorOptions, mockModelerGet,
} = vi.hoisted(() => {
  const page = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  const mockCanvasZoom = vi.fn()
  const mockUpdateProperties = vi.fn()
  const mockSelectionGet = vi.fn((): any[] => [])
  // Modeler.get 服务路由：canvas/selection/modeling（属性面板与 zoom 依赖）
  const mockModelerGet = vi.fn((service: string) => {
    if (service === 'canvas') return { zoom: mockCanvasZoom }
    if (service === 'selection') return { get: mockSelectionGet }
    if (service === 'modeling') return { updateProperties: mockUpdateProperties }
    return undefined
  })
  const modelerConstructorOptions: any[] = []
  return {
    mockTodo: page(), mockDone: page(), mockComplete: ok(), mockRejectPrev: ok(), mockRejectStart: ok(),
    mockTerminate: ok(), mockBatchApprove: ok(),
    mockBtTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockBtDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockBtCreate: ok(), mockBtUpdate: ok(), mockBtDelete: ok(),
    mockDeploy: ok(),
    mockProcessList: page(),
    mockProcessImage: vi.fn(async (): Promise<any> => ({ code: 200, data: '' })),
    mockProcessVersions: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockRollbackLogs: page(), mockConfirmConflict: ok(),
    mockPrintPage: page(), mockPrintCreate: ok(), mockPrintUpdate: ok(), mockPrintDelete: ok(),
    mockPrintRender: vi.fn(async (): Promise<any> => ({ code: 200, data: '<html/>' })),
    mockBatchTemplateList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockBatchCreate: ok(), mockBatchUpdate: ok(), mockBatchDelete: ok(),
    mockSaveXML: vi.fn(async () => ({ xml: '<xml/>' })),
    mockImportXML: vi.fn(async () => undefined),
    mockUpdateProperties, modelerConstructorOptions, mockModelerGet,
  }
})

vi.mock('@/api/workflow', () => ({
  getTodoTasks: mockTodo, getDoneTasks: mockDone, completeTask: mockComplete,
  rejectToPrevious: mockRejectPrev, rejectToStart: mockRejectStart, terminateProcess: mockTerminate,
  batchApprove: mockBatchApprove,
  getBusinessTypeTree: mockBtTree, getBusinessTypeDetail: mockBtDetail,
  createBusinessType: mockBtCreate, updateBusinessType: mockBtUpdate, deleteBusinessType: mockBtDelete,
  deployProcess: mockDeploy, getProcessList: mockProcessList,
  getProcessImage: mockProcessImage, getProcessVersions: mockProcessVersions,
  getRollbackLogs: mockRollbackLogs, confirmRollbackConflict: mockConfirmConflict,
}))
vi.mock('@/api/print-template', () => ({
  getPrintTemplatePage: mockPrintPage, createPrintTemplate: mockPrintCreate,
  updatePrintTemplate: mockPrintUpdate, deletePrintTemplate: mockPrintDelete,
  renderPrintTemplate: mockPrintRender, exportPrintTemplatePdf: vi.fn(),
  getPrintTemplateDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
}))
vi.mock('@/api/batch', () => ({
  getTemplateList: mockBatchTemplateList, createTemplate: mockBatchCreate,
  updateTemplate: mockBatchUpdate, deleteTemplate: mockBatchDelete,
  renderTemplate: vi.fn(), downloadTemplate: vi.fn(), importData: vi.fn(),
  startExport: vi.fn(), getExportStatus: vi.fn(), downloadExportFile: vi.fn(), getFilePreviewUrl: vi.fn(),
}))
// bpmn-js Modeler stub（canvas 依赖隔离；捕获构造参数钉住 moddleExtensions）
vi.mock('bpmn-js/lib/Modeler', () => ({
  default: class {
    constructor(options: any) { modelerConstructorOptions.push(options) }
    createDiagram = vi.fn(async () => undefined)
    importXML = mockImportXML
    saveXML = mockSaveXML
    destroy = vi.fn()
    on = vi.fn()
    get = mockModelerGet
  },
}))
vi.mock('bpmn-js/dist/assets/diagram-js.css', () => ({}))
vi.mock('bpmn-js/dist/assets/bpmn-js.css', () => ({}))
vi.mock('bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css', () => ({}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn() }),
    useRoute: () => ({ query: {}, params: { id: '1' } }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Approval from '@/views/workflow/approval/index.vue'
import BusinessType from '@/views/workflow/business-type/index.vue'
import Designer from '@/views/workflow/designer/index.vue'
import PropertiesPanel from '@/views/workflow/designer/PropertiesPanel.vue'
import Process from '@/views/workflow/process/index.vue'
import Rollback from '@/views/workflow/rollback/index.vue'
import PrintTemplate from '@/views/system/print-template/index.vue'
import BatchTemplate from '@/views/system/template/index.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('workflow/approval/index.vue 审批中心', () => {
  async function mountPage() {
    mockTodo.mockResolvedValue({ code: 200, data: { records: [{ id: 't1', taskName: '审批1' }], total: 1 } })
    mockDone.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    wrapper = mount(Approval, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载默认待办 tab 拉取待办任务', async () => {
    await mountPage()
    expect(mockTodo).toHaveBeenCalled()
  })

  // 分页参数口径钉住（2026-08-17 真实浏览器实测修复）：
  // 后端 /todo /done 收 page/size（ApprovalController SoT），原传 pageNum/pageSize 翻页失效
  it('分页参数以后端 page/size 口径传递，切 tab 重置页码', async () => {
    await mountPage()
    expect(mockTodo).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 10 }))
    const st = wrapper.vm.$.setupState
    st.queryParams.page = 3
    st.activeTab = 'done'
    st.handleTabChange()
    await flushPromises()
    expect(st.queryParams.page).toBe(1)
    expect(mockDone).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 10 }))
  })

  it('tab 切换到已办', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    mockDone.mockClear()
    st.activeTab = 'done'
    st.handleTabChange()
    await flushPromises()
    expect(mockDone).toHaveBeenCalled()
  })

  it('通过/退回走对话框提交流程、终止直接调 API', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    // 通过：打开对话框 → submitApprove 调 completeTask
    st.handleApprove({ taskId: 't1' })
    expect(st.approveDialogVisible).toBe(true)
    expect(st.approveForm.taskId).toBe('t1')
    await st.submitApprove()
    await flushPromises()
    expect(mockComplete).toHaveBeenCalledWith(expect.objectContaining({ taskId: 't1' }))
    // 退回：默认 previous 类型 → submitReject 调 rejectToPrevious
    st.handleReject({ taskId: 't2' })
    expect(st.rejectForm.type).toBe('previous')
    await st.submitReject()
    await flushPromises()
    expect(mockRejectPrev).toHaveBeenCalledWith(expect.objectContaining({ taskId: 't2' }))
    // 退回发起人分支
    st.rejectForm.type = 'start'
    st.rejectForm.taskId = 't2b'
    await st.submitReject()
    await flushPromises()
    expect(mockRejectStart).toHaveBeenCalledWith(expect.objectContaining({ taskId: 't2b' }))
    // 终止直接调 terminateProcess
    await st.handleTerminate({ taskId: 't3' })
    await flushPromises()
    expect(mockTerminate).toHaveBeenCalledWith({ taskId: 't3' })
  })

  it('批量通过调 batchApprove（勾选任务 taskId 列表）', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleSelectionChange([{ taskId: 'a' }, { taskId: 'b' }])
    await st.handleBatchApprove()
    await flushPromises()
    expect(mockBatchApprove).toHaveBeenCalledWith({ taskIds: ['a', 'b'] })
  })
})

describe('workflow/business-type/index.vue 业务类型配置', () => {
  async function mountPage() {
    mockBtTree.mockResolvedValue({ code: 200, data: [{ id: 1, typeName: '合同审批', typeCode: 'CONTRACT' }] })
    wrapper = mount(BusinessType, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载业务类型树', async () => {
    await mountPage()
    expect(mockBtTree).toHaveBeenCalled()
  })

  it('新增走 create；选中节点后编辑走 update；删除调 delete', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    await st.handleSubmit()
    await flushPromises()
    expect(mockBtCreate).toHaveBeenCalledTimes(1)
    // 编辑依赖 currentNode（handleNodeClick 经 detail API 回显）
    mockBtDetail.mockResolvedValue({ code: 200, data: { id: 2, typeName: 'X', typeCode: 'Y' } })
    st.handleNodeClick({ id: 2 })
    await flushPromises()
    st.handleEdit()
    await flushPromises()
    expect(st.formData.id).toBe(2)
    await st.handleSubmit()
    await flushPromises()
    expect(mockBtUpdate).toHaveBeenCalledTimes(1)
    await st.handleDelete({ id: 2 })
    await flushPromises()
    expect(mockBtDelete).toHaveBeenCalledWith(2)
  })
})

describe('workflow/designer/index.vue 流程设计器', () => {
  it('部署：saveXML 打包后调 deployProcess', async () => {
    wrapper = mount(Designer, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    mockDeploy.mockClear()
    await st.handleDeploy()
    await flushPromises()
    expect(mockSaveXML).toHaveBeenCalled()
    expect(mockDeploy).toHaveBeenCalled()
  })

  it('Modeler 创建参数含 moddleExtensions.flowable（Flowable 命名空间）', async () => {
    wrapper = mount(Designer, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const opts = modelerConstructorOptions[modelerConstructorOptions.length - 1]
    expect(opts.moddleExtensions?.flowable).toBeTruthy()
    expect(opts.moddleExtensions.flowable.uri).toBe('http://flowable.org/bpmn')
    expect(opts.moddleExtensions.flowable.prefix).toBe('flowable')
  })
})

describe('workflow/designer/PropertiesPanel.vue 属性面板', () => {
  function userTaskEl(attrs: Record<string, string> = {}) {
    return {
      type: 'bpmn:UserTask',
      businessObject: { get: (k: string) => attrs[k] },
    }
  }

  it('选中 UserTask 渲染 4 字段表单并回显属性', async () => {
    const el = userTaskEl({ name: '审批', 'flowable:assignee': '${initiator}' })
    const fakeModeler = {
      on: vi.fn(),
      get: (svc: string) =>
        svc === 'selection' ? { get: () => [el] }
          : svc === 'modeling' ? { updateProperties: mockUpdateProperties }
            : undefined,
    }
    wrapper = mount(PropertiesPanel, { props: { modeler: fakeModeler }, global: { plugins: [ElementPlus] } })
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('节点名称')
    expect(text).toContain('审批人')
    expect(text).toContain('候选组')
    expect(text).toContain('候选人')
    const inputs = wrapper.findAll('input')
    expect(inputs[0].element.value).toBe('审批')
    expect(inputs[1].element.value).toBe('${initiator}')
  })

  it('回写调 updateProperties：name 与 flowable:assignee，空串写 undefined', async () => {
    const el = userTaskEl({ name: '审批' })
    const fakeModeler = {
      on: vi.fn(),
      get: (svc: string) =>
        svc === 'selection' ? { get: () => [el] }
          : svc === 'modeling' ? { updateProperties: mockUpdateProperties }
            : undefined,
    }
    wrapper = mount(PropertiesPanel, { props: { modeler: fakeModeler }, global: { plugins: [ElementPlus] } })
    await flushPromises()
    mockUpdateProperties.mockClear()
    const inputs = wrapper.findAll('input')
    await inputs[1].setValue('zhangwei')
    await inputs[1].trigger('change')
    expect(mockUpdateProperties).toHaveBeenCalledWith(el, { 'flowable:assignee': 'zhangwei' })
    await inputs[0].setValue('合同审批')
    await inputs[0].trigger('change')
    expect(mockUpdateProperties).toHaveBeenCalledWith(el, { name: '合同审批' })
    // 空串 → undefined（移除属性）
    await inputs[1].setValue('')
    await inputs[1].trigger('change')
    expect(mockUpdateProperties).toHaveBeenCalledWith(el, { 'flowable:assignee': undefined })
  })

  it('未选中时显示说明卡：processKey 语义 + ${initiator} 示例', async () => {
    const fakeModeler = { on: vi.fn(), get: (svc: string) => (svc === 'selection' ? { get: () => [] } : undefined) }
    wrapper = mount(PropertiesPanel, { props: { modeler: fakeModeler }, global: { plugins: [ElementPlus] } })
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('processKey')
    expect(text).toContain('${initiator}')
  })
})

describe('workflow/process/index.vue 流程管理', () => {
  async function mountPage() {
    mockProcessList.mockResolvedValue({ code: 200, data: { records: [{ id: 'p1', name: '合同流程' }], total: 1 } })
    wrapper = mount(Process, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载流程列表', async () => {
    await mountPage()
    expect(mockProcessList).toHaveBeenCalled()
  })

  it('查看版本拉取版本列表', async () => {
    await mountPage()
    mockProcessVersions.mockClear()
    await wrapper.vm.$.setupState.handleViewVersions({ id: 'p1' })
    await flushPromises()
    expect(mockProcessVersions).toHaveBeenCalled()
  })
})

describe('workflow/rollback/index.vue 回滚日志', () => {
  async function mountPage() {
    mockRollbackLogs.mockResolvedValue({ code: 200, data: { records: [{ id: 1, status: 'CONFLICT' }], total: 1 } })
    wrapper = mount(Rollback, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载回滚日志、搜索重置页码（page/size 口径）', async () => {
    await mountPage()
    expect(mockRollbackLogs).toHaveBeenCalled()
    const st = wrapper.vm.$.setupState
    st.queryParams.page = 3
    mockRollbackLogs.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.page).toBe(1)
  })

  it('冲突处理：打开对话框→未选处理方式被拦→选择后调 confirmRollbackConflict', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleConfirmConflict({ id: 9 })
    expect(st.conflictDialogVisible).toBe(true)
    expect(st.conflictRow.id).toBe(9)
    // 未选处理方式被拦
    await st.submitConflictConfirm()
    await flushPromises()
    expect(mockConfirmConflict).not.toHaveBeenCalled()
    // 选择后提交
    st.conflictForm.resolution = 'OVERRIDE'
    await st.submitConflictConfirm()
    await flushPromises()
    expect(mockConfirmConflict).toHaveBeenCalledWith(9, { resolution: 'OVERRIDE' })
  })
})

describe('system/print-template/index.vue 打印模板', () => {
  async function mountPage() {
    mockPrintPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, templateName: '合同打印' }], total: 1 } })
    wrapper = mount(PrintTemplate, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载模板列表', async () => {
    const w = await mountPage()
    expect(mockPrintPage).toHaveBeenCalled()
    expect(w.text()).toContain('合同打印')
  })

  it('预览调 renderPrintTemplate', async () => {
    await mountPage()
    mockPrintRender.mockClear()
    await wrapper.vm.$.setupState.handlePreview({ id: 3 })
    await flushPromises()
    expect(mockPrintRender).toHaveBeenCalled()
  })

  it('删除调 deletePrintTemplate', async () => {
    await mountPage()
    await wrapper.vm.$.setupState.handleDelete({ id: 4 })
    await flushPromises()
    expect(mockPrintDelete).toHaveBeenCalledWith(4)
  })
})

describe('system/template/index.vue 批量导入模板', () => {
  async function mountPage() {
    mockBatchTemplateList.mockResolvedValue({ code: 200, data: [{ id: 1, templateName: '材料导入模板' }] })
    wrapper = mount(BatchTemplate, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载模板列表', async () => {
    await mountPage()
    expect(mockBatchTemplateList).toHaveBeenCalled()
  })

  it('新增走 create、编辑走 update、删除调 delete', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    await st.handleSubmit()
    await flushPromises()
    expect(mockBatchCreate).toHaveBeenCalledTimes(1)
    st.handleEdit({ id: 2, templateName: 'X' })
    await flushPromises()
    await st.handleSubmit()
    await flushPromises()
    expect(mockBatchUpdate).toHaveBeenCalledTimes(1)
    await st.handleDelete({ id: 2 })
    await flushPromises()
    expect(mockBatchDelete).toHaveBeenCalledWith(2)
  })
})
