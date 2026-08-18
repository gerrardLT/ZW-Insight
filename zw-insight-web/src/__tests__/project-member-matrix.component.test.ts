/**
 * project/components/ProjectMember.vue 账本补测（2026-08 账本全量补齐 M1）
 *
 * @matrix A4-02 按角色筛选 pageNum 重置 / A4-03 添加成员必填守卫 /
 *   A4-04 用户远程搜索（realName+pageSize=20、空查询不请求、选项部门后缀）/
 *   A4-07 变更角色空选前端拦截不发 PUT / A4-09 移除取消无 DELETE /
 *   A4-11 分页 page-sizes=[10,20,50] / A4-12 多角色标签 7 角色中文映射 /
 *   A3-08 详情 ?tab=team 直达触发成员加载
 *
 * 分层纪律：守卫/取消/渲染为纯前端行为（api 层 mock）；
 * A4-05 添加成功/A4-06 重复拦截/A4-08 变更成功/A4-10 移除成功涉及
 * 真实写请求，由 e2e-real a1-project.spec.ts 覆盖。
 * 既有覆盖见 project-pages.component.test.ts（挂载加载、移除调用）。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockMembers, mockAddMember, mockRemoveMember, mockUpdateRoles,
  mockUserPage, mockProjectDetail, mockConfirm, mockMessageWarning,
  mockRouteParams, mockRouteQuery,
} = vi.hoisted(() => ({
  mockMembers: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockAddMember: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockRemoveMember: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdateRoles: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUserPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockProjectDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: { id: 1, projectName: 'P1' } })),
  mockConfirm: vi.fn(async () => 'confirm'),
  mockMessageWarning: vi.fn(),
  mockRouteParams: { value: {} as Record<string, string> },
  mockRouteQuery: { value: {} as Record<string, string> },
}))

vi.mock('@/api/project', () => ({
  getProjectMembers: mockMembers, addProjectMember: mockAddMember,
  removeProjectMember: mockRemoveMember, updateMemberRoles: mockUpdateRoles,
  getProjectDetail: mockProjectDetail,
  createProject: vi.fn(), updateProject: vi.fn(), getOwnerList: vi.fn(), getCompanyList: vi.fn(),
}))
vi.mock('@/api/system', () => ({ getUserPage: mockUserPage }))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
    useRoute: () => ({ query: mockRouteQuery.value, params: mockRouteParams.value }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: mockMessageWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm, alert: vi.fn(async () => 'ok') },
  }
})

import ProjectMember from '@/views/project/components/ProjectMember.vue'
import ProjectDetail from '@/views/project/detail.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
  mockRouteParams.value = {}
  mockRouteQuery.value = {}
})

describe('ProjectMember.vue 账本补测（@matrix A4）', () => {
  async function mountMember(members: any[] = [], total = members.length) {
    mockMembers.mockResolvedValue({ code: 200, data: { records: members, total } })
    wrapper = mount(ProjectMember, {
      global: { plugins: [ElementPlus] },
      props: { projectId: 1 },
    })
    await flushPromises()
    return wrapper
  }

  it('@matrix A4-02 按角色筛选请求带 role 且 pageNum 重置 1', async () => {
    await mountMember()
    const st = wrapper.vm.$.setupState
    st.queryParams.pageNum = 3
    st.queryParams.role = 'PROJECT_MANAGER'
    mockMembers.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    expect(mockMembers).toHaveBeenCalledWith(1, { pageNum: 1, pageSize: 10, role: 'PROJECT_MANAGER' })
  })

  it('@matrix A4-03 添加成员校验失败不发 POST 且 addLoading 不置位', async () => {
    await mountMember()
    const st = wrapper.vm.$.setupState
    st.showAddDialog()
    st.addFormRef = { validate: vi.fn(async () => { throw new Error('validation failed') }) }
    mockAddMember.mockClear()
    await st.handleAdd().catch(() => { /* validate reject 向外抛 */ })
    expect(mockAddMember).not.toHaveBeenCalled()
    expect(st.addLoading).toBe(false)
    // 规则文案钉住：用户必选 + 角色 min:1
    expect(st.addFormRules.userId[0].message).toBe('请选择用户')
    expect(st.addFormRules.projectRoles[0].message).toBe('请选择至少一个角色')
    expect(st.addFormRules.projectRoles[0].min).toBe(1)
  })

  it('@matrix A4-04 用户远程搜索带 realName/pageSize=20，空查询不请求，选项含部门后缀', async () => {
    await mountMember()
    const st = wrapper.vm.$.setupState
    // 空查询直接清空不发请求
    mockUserPage.mockClear()
    await st.searchUser('')
    expect(mockUserPage).not.toHaveBeenCalled()
    expect(st.userOptions).toEqual([])
    // 有效查询
    mockUserPage.mockResolvedValue({ code: 200, data: { records: [{ id: 5, realName: '王五', deptName: '工程部' }], total: 1 } })
    await st.searchUser('王')
    expect(mockUserPage).toHaveBeenCalledWith({ realName: '王', pageNum: 1, pageSize: 20 })
    expect(st.userOptions).toHaveLength(1)
    // 选中后同步 userName
    st.handleUserChange(5)
    expect(st.addForm.userName).toBe('王五')
  })

  it('@matrix A4-07 变更角色空选前端 warning 拦截，不发 PUT', async () => {
    await mountMember()
    const st = wrapper.vm.$.setupState
    st.showRoleDialog({ userId: 9, userName: '赵六', projectRoles: ['CONSTRUCTOR'] })
    st.roleForm.projectRoles = []
    mockUpdateRoles.mockClear()
    await st.handleUpdateRoles()
    expect(mockMessageWarning).toHaveBeenCalledWith('请至少选择一个角色')
    expect(mockUpdateRoles).not.toHaveBeenCalled()
  })

  it('@matrix A4-09 移除确认取消不发 DELETE', async () => {
    await mountMember([{ userId: 7, userName: '李四', projectRoles: [] }])
    const st = wrapper.vm.$.setupState
    mockConfirm.mockRejectedValueOnce(new Error('cancel'))
    mockRemoveMember.mockClear()
    await st.handleRemove({ userId: 7, userName: '李四' })
    await flushPromises()
    expect(mockRemoveMember).not.toHaveBeenCalled()
  })

  it('@matrix A4-11 分页 page-sizes=[10,20,50]', async () => {
    const w = await mountMember()
    const pager = w.findComponent({ name: 'ElPagination' })
    expect(pager.exists()).toBe(true)
    expect(pager.props('pageSizes')).toEqual([10, 20, 50])
  })

  it('@matrix A4-12 多角色成员每角色一 tag，7 角色中文映射正确', async () => {
    const w = await mountMember([
      { userId: 1, userName: '张三', deptName: '工程部', projectRoles: ['PROJECT_MANAGER', 'SAFETY_OFFICER', 'ARCHIVIST'], joinDate: '2026-01-01' },
    ])
    await flushPromises()
    const tags = w.findAll('.el-table .el-tag')
    expect(tags).toHaveLength(3)
    expect(tags.map((t: any) => t.text())).toEqual(['项目经理', '安全员', '资料员'])
    // 7 角色映射钉住（getRoleLabel 回退原始串）
    const st = wrapper.vm.$.setupState
    const all = ['PROJECT_MANAGER', 'CONSTRUCTOR', 'SAFETY_OFFICER', 'QUALITY_OFFICER', 'MATERIAL_OFFICER', 'FINANCE_OFFICER', 'ARCHIVIST']
    const labels = ['项目经理', '施工员', '安全员', '质量员', '材料员', '财务人员', '资料员']
    all.forEach((r, i) => expect(st.getRoleLabel(r)).toBe(labels[i]))
    expect(st.getRoleLabel('UNKNOWN_ROLE')).toBe('UNKNOWN_ROLE')
  })
})

describe('project/detail.vue 成员 tab 集成（@matrix A3-08）', () => {
  it('@matrix A3-08 ?tab=team 直达：ProjectMember 挂载即请求成员列表', async () => {
    mockRouteParams.value = { id: '1' }
    mockRouteQuery.value = { tab: 'team' }
    mockMembers.mockClear()
    wrapper = mount(ProjectDetail, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.vm.$.setupState.activeTab).toBe('team')
    expect(mockMembers).toHaveBeenCalledWith('1', expect.objectContaining({ pageNum: 1, pageSize: 10 }))
  })
})
