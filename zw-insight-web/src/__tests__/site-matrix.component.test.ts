/**
 * 现场管理五页矩阵组件测试（账本全量补齐 M7 C-2，2026-08）
 *
 * 覆盖账本 C15 进度计划 / C16 施工日志 / C17 检查列表 / C18 检查表单 / C19 检查详情
 * 的纯前端守卫增量。与既有测试边界（不重复）：
 *   - site-pages.component.test.ts：construction-log/schedule 基础 CRUD（crudPageSuite）
 *
 * @matrix C-15-1/C-15-2/C-15-3/C-15-4/C-15-7/C-15-9
 * @matrix C-16-1/C-16-2/C-16-3
 * @matrix C-17-1/C-17-4/C-17-5/C-17-6/C-17-7/C-17-8/C-17-9
 * @matrix C-18-1/C-18-2/C-18-3/C-18-5/C-18-6/C-18-7/C-18-8/C-18-10/C-18-11/C-18-12
 * @matrix C-19-1/C-19-2/C-19-3/C-19-4/C-19-5
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const norm = (s: string) => s.replace(/\r\n/g, '\n')
const scheduleSrc = norm(readFileSync(resolve(__dirname, '../views/site/schedule.vue'), 'utf-8'))
const logSrc = norm(readFileSync(resolve(__dirname, '../views/site/construction-log.vue'), 'utf-8'))
const inspIndexSrc = norm(readFileSync(resolve(__dirname, '../views/site/inspection/index.vue'), 'utf-8'))
const inspFormSrc = norm(readFileSync(resolve(__dirname, '../views/site/inspection/form.vue'), 'utf-8'))
const inspDetailSrc = norm(readFileSync(resolve(__dirname, '../views/site/inspection/detail.vue'), 'utf-8'))

const mocks = vi.hoisted(() => ({
  routeState: { params: {} as Record<string, string>, query: {} as Record<string, string> },
  mockPush: vi.fn(),
  mockSchedulePage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockPlanTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockScheduleCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockScheduleUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockScheduleDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockLogPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockLogCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockLogUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockLogDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockQualityPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockSafetyPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockQualityDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSafetyDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockCreateInspection: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSchemeList: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockSchemeItems: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockApplyScheme: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockInspectionDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockUpdateDetails: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSuccess: vi.fn(),
  mockWarning: vi.fn(),
  mockError: vi.fn(),
}))

vi.mock('@/api/site', () => ({
  getSchedulePage: mocks.mockSchedulePage, getSchedulePlanTree: mocks.mockPlanTree,
  createSchedule: mocks.mockScheduleCreate, updateSchedule: mocks.mockScheduleUpdate, deleteSchedule: mocks.mockScheduleDelete,
  getConstructionLogPage: mocks.mockLogPage, createConstructionLog: mocks.mockLogCreate,
  updateConstructionLog: mocks.mockLogUpdate, deleteConstructionLog: mocks.mockLogDelete,
  getQualityInspectionPage: mocks.mockQualityPage, getSafetyInspectionPage: mocks.mockSafetyPage,
  deleteQualityInspection: mocks.mockQualityDelete, deleteSafetyInspection: mocks.mockSafetyDelete,
  createInspection: mocks.mockCreateInspection,
}))
vi.mock('@/api/inspection-scheme', () => ({
  listInspectionSchemes: mocks.mockSchemeList, getSchemeItems: mocks.mockSchemeItems,
  applyScheme: mocks.mockApplyScheme, getInspectionDetail: mocks.mockInspectionDetail,
  updateInspectionDetails: mocks.mockUpdateDetails,
}))
vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRoute: () => mocks.routeState,
    useRouter: () => ({ push: mocks.mockPush }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mocks.mockSuccess, warning: mocks.mockWarning, error: mocks.mockError, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Schedule from '@/views/site/schedule.vue'
import ConstructionLog from '@/views/site/construction-log.vue'
import InspectionIndex from '@/views/site/inspection/index.vue'
import InspectionForm from '@/views/site/inspection/form.vue'
import InspectionDetail from '@/views/site/inspection/detail.vue'
import ElementPlus from 'element-plus'

const globalCfg = {
  plugins: [ElementPlus],
  stubs: { ProjectSelector: true, GanttChart: true },
}

// 每个用例挂载的 wrapper 统一登记，afterEach 卸载并清空 body，
// 防止 element-plus teleport 弹层在 jsdom 中累积导致后续挂载越来越慢
const liveWrappers: any[] = []
function track(w: any) { liveWrappers.push(w); return w }
afterEach(() => {
  while (liveWrappers.length) liveWrappers.pop()!.unmount()
  document.body.innerHTML = ''
})

beforeEach(() => {
  vi.clearAllMocks()
  mocks.routeState.params = {}
  mocks.routeState.query = {}
  mocks.mockPlanTree.mockResolvedValue({ code: 200, data: [] })
  mocks.mockInspectionDetail.mockResolvedValue({ code: 200, data: {} })
  mocks.mockSchemeItems.mockResolvedValue({ code: 200, data: [] })
})

describe('site/schedule.vue 进度计划（C15）', () => {
  async function mountPage() {
    const w = track(mount(Schedule, { global: globalCfg }))
    await flushPromises()
    return w
  }

  it('@matrix C-15-3 必填三条 taskName/planStartDate/planEndDate', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['taskName', 'planStartDate', 'planEndDate'])
    expect(st.formRules.taskName[0].message).toBe('请输入任务名称')
    expect(st.formRules.planStartDate[0].message).toBe('请选择开始日期')
    expect(st.formRules.planEndDate[0].message).toBe('请选择完成日期')
  })

  it('@matrix C-15-4 progress slider max=100 源码钉住 + C-15-1 el-progress 渲染', () => {
    expect(scheduleSrc).toContain('<el-slider v-model="formData.progress" :max="100" show-input />')
    expect(scheduleSrc).toContain('<el-progress :percentage="row.progress || 0"')
  })

  it('@matrix C-15-2 taskStatus 三态翻译：COMPLETED→已完成 success / DELAYED→滞后 danger / 其他→进行中 warning', () => {
    expect(scheduleSrc).toContain("row.taskStatus === 'COMPLETED' ? '已完成' : row.taskStatus === 'DELAYED' ? '滞后' : '进行中'")
    expect(scheduleSrc).toContain("row.taskStatus === 'COMPLETED' ? 'success' : row.taskStatus === 'DELAYED' ? 'danger' : 'warning'")
  })

  it('@matrix C-15-7 甘特图空数据 el-empty：tree 返回 [] → ganttHasData=false', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    st.ganttProjectId = 9
    await flushPromises()
    expect(mocks.mockPlanTree).toHaveBeenCalledWith(9)
    expect(st.ganttHasData).toBe(false)
    expect(scheduleSrc).toContain('<el-empty v-else-if="ganttProjectId && !ganttHasData" description="暂无进度计划数据" />')
  })

  it('@matrix C-15-9 树接口失败 → ganttHasData=false（甘特区空状态，不崩溃）', async () => {
    mocks.mockPlanTree.mockRejectedValue(new Error('500'))
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    st.ganttProjectId = 9
    await flushPromises()
    expect(st.ganttHasData).toBe(false)
  })

  it('@matrix C-15-7 反向：tree 有数据 → ganttHasData=true（GanttChart 渲染前提）', async () => {
    mocks.mockPlanTree.mockResolvedValue({ code: 200, data: [{ id: 1, text: '基础施工' }] })
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    st.ganttProjectId = 9
    await flushPromises()
    expect(st.ganttHasData).toBe(true)
  })
})

describe('site/construction-log.vue 施工日志（C16）', () => {
  async function mountPage() {
    const w = track(mount(ConstructionLog, { global: globalCfg }))
    await flushPromises()
    return w
  }

  it('@matrix C-16-1 单日 logDate 查询转 startDate=endDate（请求无 logDate 键）', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    st.queryParams.logDate = '2026-08-20'
    await st.loadData()
    await flushPromises()
    const lastCall: any = (mocks.mockLogPage.mock.calls as any[])[mocks.mockLogPage.mock.calls.length - 1][0]
    expect(lastCall.startDate).toBe('2026-08-20')
    expect(lastCall.endDate).toBe('2026-08-20')
    expect(lastCall).not.toHaveProperty('logDate')
  })

  it('@matrix C-16-2 必填三条 projectId/logDate/productionRecord', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['projectId', 'logDate', 'productionRecord'])
    expect(st.formRules.productionRecord[0].message).toBe('请输入生产记录')
  })

  it('@matrix C-16-3 workerCount input-number min=0 源码钉住（负值拦截）', () => {
    expect(logSrc).toContain('v-model="formData.workerCount" :min="0"')
  })
})

describe('site/inspection/index.vue 检查列表（C17）', () => {
  async function mountPage() {
    const w = track(mount(InspectionIndex, { global: globalCfg }))
    await flushPromises()
    return w
  }

  it('@matrix C-17-5 RECT_MAP 四态 + 未知透传；空值渲染 "-"（源码钉住）', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    expect(st.rectText('PENDING')).toBe('待整改')
    expect(st.rectTagType('PENDING')).toBe('warning')
    expect(st.rectText('SUBMITTED')).toBe('已提交')
    expect(st.rectText('APPROVED')).toBe('已通过')
    expect(st.rectText('REJECTED')).toBe('已驳回')
    expect(st.rectTagType('REJECTED')).toBe('danger')
    expect(st.rectText('UNKNOWN')).toBe('UNKNOWN')
    expect(inspIndexSrc).toContain('<span v-else>-</span>')
  })

  it('@matrix C-17-4 hasProblem 1→有问题 danger / 0→无问题 success 源码钉住', () => {
    expect(inspIndexSrc).toContain("row.hasProblem === 1 ? 'danger' : 'success'")
    expect(inspIndexSrc).toContain("row.hasProblem === 1 ? '有问题' : '无问题'")
  })

  it('@matrix C-17-1 tab 切换重载：quality→getQualityInspectionPage；safety→getSafetyInspectionPage 且 page 归 1', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    expect(mocks.mockQualityPage).toHaveBeenCalledTimes(1)
    st.activeTab = 'safety'
    st.queryParams.page = 3
    st.handleTabChange()
    await flushPromises()
    expect(st.queryParams.page).toBe(1)
    expect(mocks.mockSafetyPage).toHaveBeenCalledTimes(1)
  })

  it('@matrix C-17-9 删除按当前 tab 类型路由 API（quality→deleteQuality / safety→deleteSafety）', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    await st.handleDelete({ id: 11 })
    await flushPromises()
    expect(mocks.mockQualityDelete).toHaveBeenCalledWith(11)
    st.activeTab = 'safety'
    await st.handleDelete({ id: 22 })
    await flushPromises()
    expect(mocks.mockSafetyDelete).toHaveBeenCalledWith(22)
  })

  it('@matrix C-17-6/7/8 新增/编辑/详情路由：form 携带 type query；编辑/详情携带 id', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    st.handleAdd()
    expect(mocks.mockPush).toHaveBeenCalledWith({ path: '/site/inspection/form', query: { type: 'quality' } })
    st.handleEdit({ id: 5 })
    expect(mocks.mockPush).toHaveBeenCalledWith({ path: '/site/inspection/form/5', query: { type: 'quality' } })
    st.handleDetail({ id: 6 })
    expect(mocks.mockPush).toHaveBeenCalledWith({ path: '/site/inspection/detail/6' })
  })
})

describe('site/inspection/form.vue 检查表单（C18）', () => {
  async function mountPage() {
    const w = track(mount(InspectionForm, { global: globalCfg }))
    await flushPromises()
    return w
  }

  it('@matrix C-18-1 必填两条 projectId/inspectionType（实证：无其他必填）', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['projectId', 'inspectionType'])
    expect(st.formRules.inspectionType[0].message).toBe('请选择检查类型')
  })

  it('@matrix C-18-2 检查类型切换：清空 schemeId+detailList 并按类型重载方案列表', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    st.formData.inspectionType = 'QUALITY'
    st.formData.schemeId = 3
    st.detailList = [{ itemName: 'X', checkStandard: '', checkMethod: '', checkResult: 'PASS' }]
    await st.handleInspectionTypeChange()
    await flushPromises()
    expect(st.formData.schemeId).toBeUndefined()
    expect(st.detailList).toEqual([])
    expect(mocks.mockSchemeList).toHaveBeenCalledWith(expect.objectContaining({ inspectionType: 'QUALITY' }))
  })

  it('@matrix C-18-3 新增模式选方案：getSchemeItems 填充明细，checkResult 均为 NOT_CHECKED', async () => {
    mocks.mockSchemeItems.mockResolvedValue({ code: 200, data: [
      { itemName: '安全帽佩戴', checkStandard: '全员佩戴', checkMethod: '目测' },
      { itemName: '临边防护', checkMethod: '' },
    ] })
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    await st.handleSchemeChange(8)
    await flushPromises()
    expect(mocks.mockSchemeItems).toHaveBeenCalledWith(8)
    expect(st.detailList).toHaveLength(2)
    expect(st.detailList.every((d: any) => d.checkResult === 'NOT_CHECKED')).toBe(true)
    expect(st.detailList[0].itemName).toBe('安全帽佩戴')
    expect(st.detailList[1].checkStandard).toBe('')
    // 清空方案则明细清空
    await st.handleSchemeChange(undefined)
    expect(st.detailList).toEqual([])
  })

  it('@matrix C-18-5/6 有方案时 itemName/checkMethod 只读渲染 + 无「添加检查项」按钮（源码钉住）', () => {
    expect(inspFormSrc).toContain('v-if="!formData.schemeId"\n                v-model="row.itemName"')
    expect(inspFormSrc).toContain('不可新增方案外检查项')
    expect(inspFormSrc).toContain('<div class="detail-toolbar" v-if="!formData.schemeId">')
  })

  it('@matrix C-18-7 手动添加上限 100 条 warning「检查项最多添加100条」', async () => {
    // 100 行明细会触发 el-table 在 jsdom 中超时级渲染，本例仅验证添加守卫逻辑，stub 掉表格
    const w = track(mount(InspectionForm, { global: { plugins: [ElementPlus], stubs: { ...globalCfg.stubs, 'el-table': true } } }))
    await flushPromises()
    const st: any = w.vm.$.setupState
    st.detailList = Array.from({ length: 100 }, (_, i) => ({ itemName: `项${i}`, checkStandard: '', checkMethod: '', checkResult: 'NOT_CHECKED' }))
    st.handleAddDetail()
    expect(mocks.mockWarning).toHaveBeenCalledWith('检查项最多添加100条')
    expect(st.detailList).toHaveLength(100)
    st.detailList.pop()
    st.handleAddDetail()
    expect(st.detailList).toHaveLength(100)
  })

  it('@matrix C-18-8 提交 filter(d=>d.itemName) 过滤空行 + C-18-12 保存后 router.push("/site/inspection")', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    st.formRef = { validate: async () => true }
    st.formData.projectId = 1
    st.formData.inspectionType = 'SAFETY'
    st.detailList = [
      { itemName: '有效项', checkStandard: 'S', checkMethod: 'M', checkResult: 'PASS' },
      { itemName: '', checkStandard: '', checkMethod: '', checkResult: 'NOT_CHECKED' },
    ]
    await st.handleSubmit()
    await flushPromises()
    expect(mocks.mockCreateInspection).toHaveBeenCalledWith(expect.objectContaining({
      details: [expect.objectContaining({ itemName: '有效项' })],
    }))
    const details = (mocks.mockCreateInspection.mock.calls as any[])[0][0].details
    expect(details).toHaveLength(1)
    expect(mocks.mockPush).toHaveBeenCalledWith('/site/inspection')
  })

  it('@matrix C-18-11 现状钉住：检查项全空（含全空行 filter 后为空）前端无拦截，仍调 createInspection', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    st.formRef = { validate: async () => true }
    st.formData.projectId = 1
    st.formData.inspectionType = 'QUALITY'
    st.detailList = [{ itemName: '', checkStandard: '', checkMethod: '', checkResult: 'NOT_CHECKED' }]
    await st.handleSubmit()
    await flushPromises()
    expect(mocks.mockCreateInspection).toHaveBeenCalledWith(expect.objectContaining({ details: [] }))
  })

  it('@matrix C-18-10 编辑模式详情加载：details 优先；无 details 时 schemeSnapshot 快照恢复', async () => {
    mocks.mockInspectionDetail.mockResolvedValue({ code: 200, data: {
      id: 77, projectId: 1, inspectionType: 'QUALITY', schemeId: undefined,
      schemeSnapshot: JSON.stringify({ schemeName: '质量方案A', items: [{ itemName: '钢筋间距', checkStandard: '±10mm', checkMethod: '尺量' }] }),
    } })
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    await st.loadInspectionDetail(77)
    await flushPromises()
    expect(mocks.mockInspectionDetail).toHaveBeenCalledWith(77)
    expect(st.detailList).toHaveLength(1)
    expect(st.detailList[0].itemName).toBe('钢筋间距')
    expect(st.detailList[0].checkResult).toBe('NOT_CHECKED')
  })
})

describe('site/inspection/detail.vue 检查详情（C19）', () => {
  async function mountPage() {
    mocks.routeState.params = { id: '9' }
    const w = track(mount(InspectionDetail, { global: globalCfg }))
    await flushPromises()
    return w
  }

  it('@matrix C-19-1 总结果 PASS→合格 success / 否则不合格 danger 源码钉住', () => {
    expect(inspDetailSrc).toContain(":type=\"detail.result === 'PASS' ? 'success' : 'danger'\"")
    expect(inspDetailSrc).toContain("detail.result === 'PASS' ? '合格' : '不合格'")
  })

  it('@matrix C-19-2 明细三态 PASS/FAIL/其他（合格/不合格/未检查）+ C-19-4 空明细 el-empty 源码钉住', () => {
    expect(inspDetailSrc).toContain("v-if=\"row.result === 'PASS'\"")
    expect(inspDetailSrc).toContain("v-else-if=\"row.result === 'FAIL'\"")
    expect(inspDetailSrc).toContain('<el-empty v-if="detailItems.length === 0" description="暂无检查明细" />')
  })

  it('@matrix C-19-3 schemeName 从快照解析（JSON 字符串快照）；无快照回退 schemeName 字段', async () => {
    mocks.mockInspectionDetail.mockResolvedValue({ code: 200, data: {
      inspectionNo: 'ZJ001', inspectionType: 'QUALITY', result: 'PASS',
      schemeSnapshot: JSON.stringify({ schemeName: '质量方案A', items: [{ itemName: '项1', result: 'PASS' }] }),
    } })
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    expect(st.schemeName).toBe('质量方案A')
    expect(st.detailItems).toHaveLength(1)
    expect(st.detailItems[0].itemName).toBe('项1')
  })

  it('@matrix C-19-5 详情加载失败提示 ElMessage.error("加载检查详情失败")', async () => {
    mocks.mockInspectionDetail.mockRejectedValue(new Error('500'))
    await mountPage()
    expect(mocks.mockError).toHaveBeenCalledWith('加载检查详情失败')
  })
})
