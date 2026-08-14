/**
 * inspection/form.vue 方案驱动逻辑组件测试（2026-08-14 P1 补测）
 *
 * @matrix C-18 检查表单方案驱动 6 逻辑（前端 UI 层）
 * 钉住 form.vue 的方案驱动函数（L183-335）：
 *   1. handleInspectionTypeChange 清空 schemeId/details 并按类型重载方案列表
 *   2. loadSchemeList 空类型短路
 *   3. 新增态 handleSchemeChange → getSchemeItems 填充且 checkResult 全 NOT_CHECKED
 *   4. 编辑态 handleSchemeChange → applyScheme 调用（route.params.id 分支）
 *   5. handleAddDetail 100 条上限
 *   6. handleSubmit 空行 filter（itemName 为空不入库）
 *
 * 模式与 payment-apply.component.test.ts 一致：真实 Element Plus 挂载，
 * mock API 层（协作方），通过 setupState 驱动组件内部函数。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockListInspectionSchemes,
  mockGetSchemeItems,
  mockApplyScheme,
  mockGetInspectionDetail,
  mockUpdateInspectionDetails,
  mockCreateInspection,
  mockGetInspectionPage,
  mockRoute,
  mockRouterPush,
} = vi.hoisted(() => ({
  mockListInspectionSchemes: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: { records: [] } })),
  mockGetSchemeItems: vi.fn(async (_id?: any): Promise<any> => ({ code: 200, data: [] })),
  mockApplyScheme: vi.fn(async (_id?: any, _s?: any): Promise<any> => ({ code: 200 })),
  mockGetInspectionDetail: vi.fn(async (_id?: any): Promise<any> => ({ code: 200, data: {} })),
  mockUpdateInspectionDetails: vi.fn(async (_id?: any, _d?: any): Promise<any> => ({ code: 200 })),
  mockCreateInspection: vi.fn(async (_p?: any): Promise<any> => ({ code: 200 })),
  mockGetInspectionPage: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: { records: [] } })),
  mockRoute: { params: {} as Record<string, any>, query: {} },
  mockRouterPush: vi.fn(),
}))

vi.mock('@/api/inspection-scheme', () => ({
  listInspectionSchemes: mockListInspectionSchemes,
  getSchemeItems: mockGetSchemeItems,
  applyScheme: mockApplyScheme,
  getInspectionDetail: mockGetInspectionDetail,
  updateInspectionDetails: mockUpdateInspectionDetails,
}))
vi.mock('@/api/site', () => ({
  createInspection: mockCreateInspection,
  getInspectionPage: mockGetInspectionPage,
}))
vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({ push: mockRouterPush }),
}))
// ElMessage 实例会向 happy-dom body 附加 DOM 并持定时器，多用例累积后显著拖慢，
// partial mock 掉消息 API（保留其余 ElementPlus 组件真实挂载）
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
    ElMessageBox: {
      ...actual.ElMessageBox,
      confirm: vi.fn(async () => 'confirm'),
    },
  }
})
// ProjectSelector 涉及独立请求链，本测试聚焦方案驱动逻辑，stub 之
vi.mock('@/components/ProjectSelector.vue', () => ({
  default: {
    name: 'ProjectSelector',
    render: () => null,
  },
}))

import InspectionForm from '@/views/site/inspection/form.vue'

let currentWrapper: any = null

async function mountPage() {
  const wrapper = mount(InspectionForm, {
    global: { plugins: [ElementPlus] },
  })
  await flushPromises()
  currentWrapper = wrapper
  return wrapper
}

/** script setup 组件经实例代理访问 setup 绑定（dev 模式 setupState 暴露） */
function setupState(wrapper: any): any {
  return wrapper.vm.$.setupState
}

describe('inspection/form.vue 方案驱动逻辑（@matrix C-18）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.params = {}
    mockListInspectionSchemes.mockResolvedValue({ code: 200, data: { records: [] } })
    mockGetSchemeItems.mockResolvedValue({ code: 200, data: [] })
    mockApplyScheme.mockResolvedValue({ code: 200 })
    mockGetInspectionDetail.mockResolvedValue({ code: 200, data: {} })
    mockCreateInspection.mockResolvedValue({ code: 200 })
  })

  // 每例后卸载：happy-dom 下 DOM 累积会逐例拖慢挂载（实证 16s/例退化）
  afterEach(() => {
    if (currentWrapper) {
      try { currentWrapper.unmount() } catch { /* 忽略卸载异常 */ }
      currentWrapper = null
    }
  })

  it('挂载后不自动加载方案列表（无检查类型）', async () => {
    await mountPage()
    expect(mockListInspectionSchemes).not.toHaveBeenCalled()
  })

  it('handleInspectionTypeChange 清空 schemeId/details 并按类型重载方案', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    // 先有脏状态
    st.formData.schemeId = 99
    st.detailList = [{ itemName: '脏数据', checkStandard: '', checkMethod: '', checkResult: 'NOT_CHECKED' }]
    st.formData.inspectionType = 'SAFETY'
    mockListInspectionSchemes.mockResolvedValue({ code: 200, data: { records: [{ id: 1, schemeName: '安全方案' }] } })

    await st.handleInspectionTypeChange()
    expect(st.formData.schemeId).toBeUndefined()
    expect(st.detailList.length).toBe(0)
    expect(mockListInspectionSchemes).toHaveBeenCalledTimes(1)
    expect(mockListInspectionSchemes.mock.calls[0][0].inspectionType).toBe('SAFETY')
    expect(st.schemeList.length).toBe(1)
  })

  it('loadSchemeList 空类型短路不调 API', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.inspectionType = ''
    await st.loadSchemeList()
    expect(mockListInspectionSchemes).not.toHaveBeenCalled()
    expect(st.schemeList.length).toBe(0)
  })

  it('新增态 handleSchemeChange → getSchemeItems 填充且 checkResult 全 NOT_CHECKED', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    mockGetSchemeItems.mockResolvedValue({
      code: 200,
      data: [
        { itemName: '临边防护', checkStandard: 'GB50xxx', checkMethod: '目测' },
        { itemName: '安全帽佩戴', checkStandard: 'JGJ59' },
      ],
    })

    await st.handleSchemeChange(1001)
    expect(mockGetSchemeItems).toHaveBeenCalledWith(1001)
    expect(st.detailList.length).toBe(2)
    expect(st.detailList[0].itemName).toBe('临边防护')
    expect(st.detailList[0].checkStandard).toBe('GB50xxx')
    expect(st.detailList[0].checkMethod).toBe('目测')
    expect(st.detailList[1].checkMethod).toBe('') // 缺省补空串
    // 全部未检查
    expect(st.detailList.every((d: any) => d.checkResult === 'NOT_CHECKED')).toBe(true)
    expect(mockApplyScheme).not.toHaveBeenCalled()
  })

  it('编辑态 handleSchemeChange → applyScheme 调用（非 getSchemeItems）', async () => {
    mockRoute.params = { id: '777' }
    mockGetInspectionDetail.mockResolvedValue({
      code: 200,
      data: { id: 777, projectId: 1, inspectionType: 'QUALITY', details: [] },
    })
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    expect(st.isEdit).toBe(true)

    await st.handleSchemeChange(2002)
    expect(mockApplyScheme).toHaveBeenCalledWith(777, 2002)
    expect(mockGetSchemeItems).not.toHaveBeenCalled()
  })

  it('handleSchemeChange(undefined) 清空明细', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.detailList = [{ itemName: 'x', checkStandard: '', checkMethod: '', checkResult: 'NOT_CHECKED' }]
    await st.handleSchemeChange(undefined)
    expect(st.detailList.length).toBe(0)
    expect(mockGetSchemeItems).not.toHaveBeenCalled()
  })

  it('handleAddDetail 添加行且 100 条上限拦截', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.handleAddDetail()
    expect(st.detailList.length).toBe(1)
    expect(st.detailList[0].checkResult).toBe('NOT_CHECKED')
    // 填满 100 条后再添加被拦截：同步替换+同步断言，不等渲染完成
    //（el-table 100 行在 happy-dom 下渲染极慢，上限逻辑本身为同步纯判断）
    st.detailList = Array.from({ length: 100 }, (_, i) => ({
      itemName: `项${i}`, checkStandard: '', checkMethod: '', checkResult: 'NOT_CHECKED',
    }))
    st.handleAddDetail()
    expect(st.detailList.length, '超过 100 条应被拦截').toBe(100)
    // 立即卸载避免 100 行渲染拖慢后续用例
    wrapper.unmount()
    currentWrapper = null
  }, 20_000)

  it('handleDeleteDetail 删除指定行', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.detailList = [
      { itemName: 'a', checkStandard: '', checkMethod: '', checkResult: 'NOT_CHECKED' },
      { itemName: 'b', checkStandard: '', checkMethod: '', checkResult: 'NOT_CHECKED' },
    ]
    st.handleDeleteDetail(0)
    expect(st.detailList.length).toBe(1)
    expect(st.detailList[0].itemName).toBe('b')
  })

  it('handleSubmit 空明细行被 filter（itemName 为空不入库）', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.projectId = 1
    st.formData.inspectionType = 'SAFETY'
    st.detailList = [
      { itemName: '有效项', checkStandard: 's', checkMethod: 'm', checkResult: 'NOT_CHECKED' },
      { itemName: '', checkStandard: '', checkMethod: '', checkResult: 'NOT_CHECKED' },
    ]
    // el-form.validate 在 happy-dom 下恒 resolve（字段未注册），直接驱动提交
    await st.handleSubmit()
    expect(mockCreateInspection).toHaveBeenCalledTimes(1)
    const payload = mockCreateInspection.mock.calls[0][0]
    expect(payload.details.length, '空行应被 filter').toBe(1)
    expect(payload.details[0].itemName).toBe('有效项')
  })
})
