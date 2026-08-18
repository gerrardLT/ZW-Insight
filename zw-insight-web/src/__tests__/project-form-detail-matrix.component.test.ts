/**
 * project/form.vue + detail.vue 账本补测（2026-08 账本全量补齐 M1）
 *
 * @matrix A2-03 编号只读 / A2-04 业主远程搜索 / A2-05 选中同步名称快照 /
 *   A2-07 雪花 ID 字符串传递 / A2-08 预算金额 min=0 precision=2 / A2-09 needTender 默认 0 /
 *   A2-10 校验失败不发请求 / A2-12 取消返回不落库 /
 *   A3-02 CLOSING 状态标签缺失回退原始枚举串（源码实证缺陷钉住）/
 *   A3-03 URL ?tab=team 直达 / A3-04 返回列表 / A3-07 预算金额 0 显示
 *
 * 分层纪律：纯前端行为断言（api 层 mock）；真实保存/回显链路由 e2e-real
 * project-crud.spec.ts 覆盖。既有覆盖见 project-pages.component.test.ts
 * （必填五项、create/update 分流、公司预载、详情挂载拉取）。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockProjectDetail, mockProjectCreate, mockProjectUpdate, mockOwnerList, mockCompanyList,
  mockRouteParams, mockRouteQuery, mockRouterPush,
} = vi.hoisted(() => ({
  mockProjectDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: { id: 1, projectName: 'P1' } })),
  mockProjectCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProjectUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockOwnerList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockCompanyList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockRouteParams: { value: {} as Record<string, string> },
  mockRouteQuery: { value: {} as Record<string, string> },
  mockRouterPush: vi.fn(),
}))

vi.mock('@/api/project', () => ({
  getProjectDetail: mockProjectDetail, createProject: mockProjectCreate, updateProject: mockProjectUpdate,
  getOwnerList: mockOwnerList, getCompanyList: mockCompanyList,
  // detail.vue 挂载 ProjectMember 子组件消费（防 unhandled error）
  getProjectMembers: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  addProjectMember: vi.fn(async (): Promise<any> => ({ code: 200 })),
  removeProjectMember: vi.fn(async (): Promise<any> => ({ code: 200 })),
  updateMemberRoles: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))
vi.mock('@/api/system', () => ({ getUserPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })) }))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: mockRouterPush, back: vi.fn() }),
    useRoute: () => ({ query: mockRouteQuery.value, params: mockRouteParams.value }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm'), alert: vi.fn(async () => 'ok') },
  }
})

import ProjectForm from '@/views/project/form.vue'
import ProjectDetail from '@/views/project/detail.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
  mockRouteParams.value = {}
  mockRouteQuery.value = {}
})

describe('project/form.vue 账本补测（@matrix A2）', () => {
  async function mountForm() {
    wrapper = mount(ProjectForm, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('@matrix A2-03 项目编号输入框 disabled 且 placeholder「系统自动生成」', async () => {
    const w = await mountForm()
    const codeInput = w.findAll('input').find((i: any) => i.attributes('placeholder') === '系统自动生成')
    expect(codeInput, '应存在项目编号输入框').toBeTruthy()
    expect(codeInput!.attributes('disabled')).toBeDefined()
  })

  it('@matrix A2-04 业主远程搜索触发 getOwnerList 且带 ownerName 参数', async () => {
    const w = await mountForm()
    const st = wrapper.vm.$.setupState
    mockOwnerList.mockClear()
    mockOwnerList.mockResolvedValue({ code: 200, data: [{ id: 7, ownerName: '某某城投' }] })
    await st.searchOwner('城投')
    await flushPromises()
    expect(mockOwnerList).toHaveBeenCalledWith({ ownerName: '城投' })
    expect(st.ownerList).toEqual([{ id: 7, ownerName: '某某城投' }])
  })

  it('@matrix A2-05 选中业主/签约公司后同步名称快照字段', async () => {
    const w = await mountForm()
    const st = wrapper.vm.$.setupState
    st.ownerList = [{ id: 7, ownerName: '某某城投' }]
    st.companyList = [{ id: 9, companyName: '中维建设' }]
    st.handleOwnerChange(7)
    st.handleCompanyChange(9)
    expect(st.formData.ownerCompanyName).toBe('某某城投')
    expect(st.formData.signingCompanyName).toBe('中维建设')
  })

  it('@matrix A2-07 雪花 ID 以字符串传参 getProjectDetail（无 Number 转换）', async () => {
    const snowflake = '3112223334445556667' // 超 Number.MAX_SAFE_INTEGER
    mockRouteParams.value = { id: snowflake }
    await mountForm()
    expect(mockProjectDetail).toHaveBeenCalledWith(snowflake)
    const arg = (mockProjectDetail.mock.calls as any)[0][0]
    expect(typeof arg).toBe('string')
  })

  it('@matrix A2-08 预算金额 input-number 配置 min=0 precision=2', async () => {
    const w = await mountForm()
    const num = w.findComponent({ name: 'ElInputNumber' })
    expect(num.exists()).toBe(true)
    expect(num.props('min')).toBe(0)
    expect(num.props('precision')).toBe(2)
  })

  it('@matrix A2-09 新增态 needTender 默认 0（否）', async () => {
    await mountForm()
    expect(wrapper.vm.$.setupState.formData.needTender).toBe(0)
  })

  it('@matrix A2-10 表单校验失败不发创建请求且 submitLoading 不置位', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.formRef = { validate: vi.fn(async () => { throw new Error('validation failed') }) }
    mockProjectCreate.mockClear()
    await st.handleSubmit().catch(() => { /* validate reject 向外抛 */ })
    expect(mockProjectCreate).not.toHaveBeenCalled()
    expect(st.submitLoading).toBe(false)
  })

  it('@matrix A2-12 取消/返回跳列表且无写请求', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.formData.projectName = '已修改但不保存'
    mockRouterPush.mockClear()
    st.handleBack()
    expect(mockRouterPush).toHaveBeenCalledWith('/project/list')
    expect(mockProjectCreate).not.toHaveBeenCalled()
    expect(mockProjectUpdate).not.toHaveBeenCalled()
  })
})

describe('project/detail.vue 账本补测（@matrix A3）', () => {
  async function mountDetail(data: any = { id: 1, projectName: '滨江花园一期', status: 'CONSTRUCTION', budgetAmount: 100 }) {
    mockProjectDetail.mockResolvedValue({ code: 200, data })
    mockRouteParams.value = { id: '1' }
    wrapper = mount(ProjectDetail, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('@matrix A3-02 CLOSING 状态标签缺失 → 回退显示原始枚举串（源码实证缺陷钉住）', async () => {
    const w = await mountDetail({ id: 2, projectName: 'P2', status: 'CLOSING' })
    // detail.vue statusMap 仅 7 态无 CLOSING（index.vue 有 8 态）——getStatusLabel 回退原始串
    const tag = w.find('.el-descriptions .el-tag')
    expect(tag.text()).toBe('CLOSING')
  })

  it('@matrix A3-03 URL ?tab=team 初始激活项目团队 tab', async () => {
    mockRouteQuery.value = { tab: 'team' }
    const w = await mountDetail()
    expect(wrapper.vm.$.setupState.activeTab).toBe('team')
  })

  it('@matrix A3-04 返回按钮跳 /project/list', async () => {
    await mountDetail()
    mockRouterPush.mockClear()
    wrapper.vm.$.setupState.handleBack()
    expect(mockRouterPush).toHaveBeenCalledWith('/project/list')
  })

  it('@matrix A3-07 budgetAmount=0 显示 0 而非空', async () => {
    const w = await mountDetail({ id: 3, projectName: 'P3', status: 'DRAFT', budgetAmount: 0 })
    // el-descriptions 渲染为 table：label/content 均为 .el-descriptions__cell
    const cells = w.findAll('.el-descriptions__cell')
    const idx = cells.findIndex((c: any) => c.text().includes('预算金额'))
    expect(idx, '应存在预算金额描述项').toBeGreaterThanOrEqual(0)
    expect(cells[idx + 1].text()).toContain('0')
  })

  it('@matrix A3-06 卡片标题含项目名称', async () => {
    const w = await mountDetail({ id: 4, projectName: '高新区产业园', status: 'WON' })
    expect(w.find('.card-header').text()).toContain('项目详情：高新区产业园')
  })
})
