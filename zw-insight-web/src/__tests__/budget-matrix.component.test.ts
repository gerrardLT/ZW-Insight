/**
 * M2 账本补齐：A11 预算编制列表 + A12 目标成本变更列表 + A14 预算控制配置
 * 矩阵用例（P3 批 budget-pages 已有调度级覆盖；本文件补状态标签/按钮守卫/
 * 筛选重置/payload 口径/源码边界钉住等字段级断言）
 *
 * @matrix A11-01/A11-02/A11-10/A11-11/A11-12/A12-01/A12-02/A12-03/A12-04/A12-09/A12-10/A12-11/A14-02/A14-03/A14-05/A14-06/A14-08/A14-10
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockBudgetPage, mockCreateBudget, mockUpdateBudget, mockDeleteBudget, mockSubmitBudget, mockBudgetDetails,
  mockProjectList,
  mockChangeList, mockChangeDelete, mockChangeSubmit, mockChangeWithdraw,
  mockConfigList, mockConfigCreate, mockConfigUpdate, mockConfigDelete,
  mockPush, mockWarning,
} = vi.hoisted(() => ({
  mockBudgetPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreateBudget: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdateBudget: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDeleteBudget: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmitBudget: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockBudgetDetails: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockChangeList: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockChangeDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockChangeSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockChangeWithdraw: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockConfigList: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockConfigCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockConfigUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockConfigDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockPush: vi.fn(),
  mockWarning: vi.fn(),
}))

vi.mock('@/api/budget', () => ({
  getBudgetPage: mockBudgetPage,
  createBudget: mockCreateBudget,
  updateBudget: mockUpdateBudget,
  deleteBudget: mockDeleteBudget,
  submitBudget: mockSubmitBudget,
  getBudgetDetailsByBudgetId: mockBudgetDetails,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('@/api/budget-change', () => ({
  listBudgetChanges: mockChangeList,
  deleteBudgetChange: mockChangeDelete,
  submitBudgetChange: mockChangeSubmit,
  withdrawBudgetChange: mockChangeWithdraw,
}))
vi.mock('@/api/budget-control-config', () => ({
  listBudgetControlConfigs: mockConfigList,
  createBudgetControlConfig: mockConfigCreate,
  updateBudgetControlConfig: mockConfigUpdate,
  deleteBudgetControlConfig: mockConfigDelete,
}))
vi.mock('@/components/ProjectSelector.vue', () => ({
  default: { name: 'ProjectSelector', props: ['modelValue', 'width'], render: () => null },
}))
// T7：budget/index.vue 预算执行面板引用 dashboard 端点；batch 为批量导入组件依赖。
// 两者均 mock，防真实请求并避免 request→@/router→createRouter 链穿透本文件的 factory mock
vi.mock('@/api/dashboard', () => ({
  getBudgetExecution: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
}))
vi.mock('@/api/batch', () => ({
  importData: vi.fn(), startExport: vi.fn(), getExportStatus: vi.fn(),
  downloadExportFile: vi.fn(), downloadTemplate: vi.fn(async (): Promise<any> => new Blob(['x'])),
  getFilePreviewUrl: vi.fn(), getTemplateList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {}, params: {} }),
  useRouter: () => ({ push: mockPush }),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

// 暗色联动引入 useAppStore 后：无 pinia 环境的组件测试统一 mock，防 getActivePinia
vi.mock('@/stores/app', () => ({
  useAppStore: () => ({ isDark: false }),
}))
import BudgetIndex from '@/views/budget/index.vue'
import ChangeIndex from '@/views/budget/change/index.vue'
import ControlConfig from '@/views/budget/control-config/index.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const budgetIndexSrc = readFileSync(resolve(__testDir, '../views/budget/index.vue'), 'utf-8')
const changeIndexSrc = readFileSync(resolve(__testDir, '../views/budget/change/index.vue'), 'utf-8')
const configSrc = readFileSync(resolve(__testDir, '../views/budget/control-config/index.vue'), 'utf-8')

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(component: any) {
  wrapper = mount(component, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('budget/index.vue A11 矩阵', () => {
  it('A11-01 列表三态标签：APPROVED 已批准/APPROVING 审批中/其余草稿', async () => {
    mockBudgetPage.mockResolvedValue({ code: 200, data: { records: [
      { id: 1, status: 'APPROVED', totalAmount: 100 },
      { id: 2, status: 'APPROVING', totalAmount: 100 },
      { id: 3, status: 'DRAFT', totalAmount: 100 },
    ], total: 3 } })
    await mountPage(BudgetIndex)
    const rows = wrapper.findAll('.el-table__row')
    expect(rows[0].text()).toContain('已批准')
    expect(rows[1].text()).toContain('审批中')
    expect(rows[2].text()).toContain('草稿')
  })

  it('A11-02 项目筛选带 projectId 查询且 page 复位；重置清空并重载', async () => {
    await mountPage(BudgetIndex)
    const st = wrapper.vm.$.setupState
    st.queryParams.projectId = 5
    st.queryParams.page = 3
    mockBudgetPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.page).toBe(1)
    expect(mockBudgetPage).toHaveBeenCalledWith(expect.objectContaining({ projectId: 5, page: 1 }))
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.projectId).toBeUndefined()
    expect(st.queryParams.size).toBe(10)
    expect(mockBudgetPage).toHaveBeenCalledTimes(2)
  })

  it('A11-10 预算总额输入边界：min=0 precision=2 源码钉住', async () => {
    await mountPage(BudgetIndex)
    expect(budgetIndexSrc).toContain('v-model="formData.totalAmount" :min="0" :precision="2"')
    expect(budgetIndexSrc).toContain('v-model="row.budgetQuantity" :min="0" :precision="2"')
    expect(budgetIndexSrc).toContain('v-model="row.budgetUnitPrice" :min="0" :precision="2"')
  })

  it('A11-11 编辑按钮不受状态限制（任意行可见），提交/删除仅 DRAFT 行', async () => {
    mockBudgetPage.mockResolvedValue({ code: 200, data: { records: [
      { id: 1, status: 'DRAFT', totalAmount: 1 },
      { id: 2, status: 'APPROVED', totalAmount: 1 },
      { id: 3, status: 'APPROVING', totalAmount: 1 },
    ], total: 3 } })
    await mountPage(BudgetIndex)
    const rows = wrapper.findAll('.el-table__row')
    const btnText = (row: any) => row.findAll('button').map((b: any) => b.text()).join(' ')
    expect(btnText(rows[0])).toContain('编辑')
    expect(btnText(rows[0])).toContain('提交')
    expect(btnText(rows[0])).toContain('删除')
    expect(btnText(rows[1])).toContain('编辑')
    expect(btnText(rows[1])).not.toContain('提交')
    expect(btnText(rows[1])).not.toContain('删除')
    expect(btnText(rows[2])).toContain('编辑')
    expect(btnText(rows[2])).not.toContain('提交')
  })

  it('A11-12 分页 page-sizes [10,20,50] 源码钉住', async () => {
    await mountPage(BudgetIndex)
    expect(budgetIndexSrc).toContain(':page-sizes="[10, 20, 50]"')
  })
})

describe('budget/change/index.vue A12 矩阵', () => {
  it('A12-01 五态标签映射：DRAFT/SUBMITTED/APPROVED/REJECTED/WITHDRAWN', async () => {
    mockChangeList.mockResolvedValue({ code: 200, data: { records: [
      { id: 1, status: 'DRAFT', totalAdjustAmount: 1 },
      { id: 2, status: 'SUBMITTED', totalAdjustAmount: 1 },
      { id: 3, status: 'APPROVED', totalAdjustAmount: 1 },
      { id: 4, status: 'REJECTED', totalAdjustAmount: 1 },
      { id: 5, status: 'WITHDRAWN', totalAdjustAmount: 1 },
    ], total: 5 } })
    await mountPage(ChangeIndex)
    const st = wrapper.vm.$.setupState
    expect(st.statusLabel('DRAFT')).toBe('草稿')
    expect(st.statusLabel('SUBMITTED')).toBe('审批中')
    expect(st.statusLabel('APPROVED')).toBe('已通过')
    expect(st.statusLabel('REJECTED')).toBe('已驳回')
    expect(st.statusLabel('WITHDRAWN')).toBe('已撤回')
    expect(st.statusTagType('SUBMITTED')).toBe('warning')
    expect(st.statusTagType('REJECTED')).toBe('danger')
    const rows = wrapper.findAll('.el-table__row')
    expect(rows).toHaveLength(5)
  })

  it('A12-02 ProjectSelector @change 直接触发搜索；A12-03 调整总额红绿着色钉住', async () => {
    await mountPage(ChangeIndex)
    expect(changeIndexSrc).toContain('@change="handleSearch"')
    expect(changeIndexSrc).toContain("'text-danger': row.totalAdjustAmount < 0, 'text-success': row.totalAdjustAmount > 0")
  })

  it('A12-04 状态驱动操作集：DRAFT 编辑/提交/删除；SUBMITTED 撤回；APPROVED 仅查看', async () => {
    mockChangeList.mockResolvedValue({ code: 200, data: { records: [
      { id: 1, status: 'DRAFT', totalAdjustAmount: 1 },
      { id: 2, status: 'SUBMITTED', totalAdjustAmount: -1 },
      { id: 3, status: 'APPROVED', totalAdjustAmount: 0 },
    ], total: 3 } })
    await mountPage(ChangeIndex)
    const rows = wrapper.findAll('.el-table__row')
    const btnText = (row: any) => row.findAll('button').map((b: any) => b.text()).join(' ')
    expect(btnText(rows[0])).toContain('编辑')
    expect(btnText(rows[0])).toContain('提交')
    expect(btnText(rows[0])).toContain('删除')
    expect(btnText(rows[1])).toContain('撤回')
    expect(btnText(rows[1])).not.toContain('编辑')
    expect(btnText(rows[2])).not.toContain('编辑')
    expect(btnText(rows[2])).not.toContain('撤回')
    expect(btnText(rows[2])).toContain('查看')
  })

  it('A12-09 查看跳 form?id=&mode=view，编辑不带 mode', async () => {
    await mountPage(ChangeIndex)
    const st = wrapper.vm.$.setupState
    st.handleView({ id: 9 })
    expect(mockPush).toHaveBeenCalledWith('/budget/change/form?id=9&mode=view')
    st.handleEdit({ id: 9 })
    expect(mockPush).toHaveBeenCalledWith('/budget/change/form?id=9')
  })

  it('A12-10 状态筛选随查询下发且搜索复位 pageNum；A12-11 分页 [10,20,50] 钉住', async () => {
    await mountPage(ChangeIndex)
    const st = wrapper.vm.$.setupState
    st.queryParams.status = 'DRAFT'
    st.queryParams.pageNum = 2
    mockChangeList.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(mockChangeList).toHaveBeenCalledWith(expect.objectContaining({ status: 'DRAFT', pageNum: 1 }))
    expect(changeIndexSrc).toContain(':page-sizes="[10, 20, 50]"')
  })

  it('A12-08 删除确认文案「删除后不可恢复」源码钉住', async () => {
    await mountPage(ChangeIndex)
    expect(changeIndexSrc).toContain('确定要删除该变更单吗？删除后不可恢复。')
  })
})

describe('budget/control-config/index.vue A14 矩阵', () => {
  it('A14-01 补充 三态标签映射与「全局默认」回落显示', async () => {
    mockConfigList.mockResolvedValue({ code: 200, data: { records: [
      { id: 1, projectName: '', controlMode: 'WARN_ONLY', warningThreshold: 80 },
      { id: 2, projectName: '滨江项目', controlMode: 'BLOCK', warningThreshold: 90 },
      { id: 3, projectName: '其他项目', controlMode: 'EXEMPT', warningThreshold: 60 },
    ], total: 3 } })
    await mountPage(ControlConfig)
    const st = wrapper.vm.$.setupState
    expect(st.controlModeLabel('WARN_ONLY')).toBe('仅提醒')
    expect(st.controlModeLabel('BLOCK')).toBe('禁止提交')
    expect(st.controlModeLabel('EXEMPT')).toBe('免控')
    expect(st.controlModeTagType('WARN_ONLY')).toBe('warning')
    expect(st.controlModeTagType('BLOCK')).toBe('danger')
    expect(st.controlModeTagType('EXEMPT')).toBe('success')
    const rows = wrapper.findAll('.el-table__row')
    expect(rows[0].text()).toContain('全局默认')
    expect(rows[1].text()).toContain('滨江项目')
  })

  it('A14-02 名称/模式筛选生效且搜索复位 pageNum；重置清空条件', async () => {
    await mountPage(ControlConfig)
    const st = wrapper.vm.$.setupState
    st.queryParams.projectName = '滨江'
    st.queryParams.controlMode = 'BLOCK'
    st.queryParams.pageNum = 2
    mockConfigList.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(mockConfigList).toHaveBeenCalledWith(expect.objectContaining({ projectName: '滨江', controlMode: 'BLOCK', pageNum: 1 }))
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.projectName).toBe('')
    expect(st.queryParams.controlMode).toBe('')
  })

  it('A14-03 控制模式/预警阈值必填文案钉住', async () => {
    await mountPage(ControlConfig)
    const rules = wrapper.vm.$.setupState.formRules
    expect(rules.controlMode[0].message).toBe('请选择控制模式')
    expect(rules.warningThreshold[0].message).toBe('请设置预警阈值')
  })

  it('A14-05 项目留空=全局规则：payload.projectId 为 null', async () => {
    await mountPage(ControlConfig)
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    expect(st.formData.controlMode).toBe('BLOCK')
    expect(st.formData.warningThreshold).toBe(80)
    mockConfigCreate.mockClear()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockConfigCreate).toHaveBeenCalledWith({ projectId: null, controlMode: 'BLOCK', warningThreshold: 80 })
  })

  it('A14-06 阈值滑杆 min=50 max=99 step=1 源码钉住', async () => {
    await mountPage(ControlConfig)
    expect(configSrc).toContain(':min="50"')
    expect(configSrc).toContain(':max="99"')
    expect(configSrc).toContain(':step="1"')
  })

  it('A14-08 删除回落提示文案「删除后将回落为全局默认规则」钉住', async () => {
    await mountPage(ControlConfig)
    expect(configSrc).toContain('删除后将回落为全局默认规则')
  })

  it('A14-10 后端返回数组或分页双兼容：records 兜底裸数组', async () => {
    mockConfigList.mockResolvedValue({ code: 200, data: [{ id: 7, projectName: '', controlMode: 'BLOCK', warningThreshold: 80 }] })
    await mountPage(ControlConfig)
    const st = wrapper.vm.$.setupState
    expect(st.tableData).toHaveLength(1)
    expect(st.tableData[0].id).toBe(7)
    expect(st.total).toBe(1)
  })
})
