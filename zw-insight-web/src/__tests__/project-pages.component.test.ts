/**
 * project 域列表/表单/详情/成员组件测试（2026-08-15 P3 收尾批 11）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockProjectPage, mockProjectDelete, mockProjectSubmit, mockProjectClose, mockCloseCheck,
  mockProjectDetail, mockProjectCreate, mockProjectUpdate, mockOwnerList, mockCompanyList,
  mockMembers, mockAddMember, mockRemoveMember, mockUpdateRoles, mockUserPage,
  mockMessageBoxAlert,
} = vi.hoisted(() => {
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockProjectPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockProjectDelete: ok(), mockProjectSubmit: ok(), mockProjectClose: ok(),
    mockCloseCheck: vi.fn(async (): Promise<any> => ({ code: 200, data: { allPassed: true, failedReasons: [] } })),
    mockProjectDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: { id: 1, projectName: 'P1' } })),
    mockProjectCreate: ok(), mockProjectUpdate: ok(),
    mockOwnerList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockCompanyList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockMembers: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockAddMember: ok(), mockRemoveMember: ok(), mockUpdateRoles: ok(),
    mockUserPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockMessageBoxAlert: vi.fn(async (): Promise<any> => 'ok'),
  }
})

vi.mock('@/api/project', () => ({
  getProjectPage: mockProjectPage, deleteProject: mockProjectDelete, submitProject: mockProjectSubmit,
  closeProject: mockProjectClose, getProjectCloseCheck: mockCloseCheck,
  getProjectDetail: mockProjectDetail, createProject: mockProjectCreate, updateProject: mockProjectUpdate,
  getOwnerList: mockOwnerList, getCompanyList: mockCompanyList,
  getProjectMembers: mockMembers, addProjectMember: mockAddMember, removeProjectMember: mockRemoveMember, updateMemberRoles: mockUpdateRoles,
}))
vi.mock('@/api/system', () => ({
  getUserPage: mockUserPage,
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
    useRoute: () => ({ query: {}, params: { id: '1' } }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm'), alert: mockMessageBoxAlert },
  }
})

import ProjectIndex from '@/views/project/index.vue'
import ProjectForm from '@/views/project/form.vue'
import ProjectDetail from '@/views/project/detail.vue'
import ProjectMember from '@/views/project/components/ProjectMember.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('project/index.vue 项目列表', () => {
  async function mountPage(records: any[] = []) {
    mockProjectPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(ProjectIndex, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载并渲染行', async () => {
    const w = await mountPage([{ id: 1, projectName: '滨江花园一期', status: 'IN_PROGRESS' }])
    expect(mockProjectPage).toHaveBeenCalled()
    expect(w.text()).toContain('滨江花园一期')
  })

  it('行提交/删除调对应 API', async () => {
    await mountPage([{ id: 2, status: 'REPORTED' }])
    const st = wrapper.vm.$.setupState
    await st.handleSubmit({ id: 2 })
    await flushPromises()
    expect(mockProjectSubmit).toHaveBeenCalledWith(2)
    await st.handleDelete({ id: 2 })
    await flushPromises()
    expect(mockProjectDelete).toHaveBeenCalledWith(2)
  })

  it('结项预检不通过 → alert 原因且不发起结项', async () => {
    mockCloseCheck.mockResolvedValue({ code: 200, data: { allPassed: false, failedReasons: ['存在未完结合同', '存在未结清款项'] } })
    await mountPage([{ id: 3, status: 'COMPLETED' }])
    mockProjectClose.mockClear()
    await wrapper.vm.$.setupState.handleClose({ id: 3 })
    await flushPromises()
    expect(mockMessageBoxAlert).toHaveBeenCalledWith(expect.stringContaining('存在未完结合同'), '无法结项', expect.anything())
    expect(mockProjectClose).not.toHaveBeenCalled()
  })

  it('结项预检通过 → 确认后调 closeProject', async () => {
    mockCloseCheck.mockResolvedValue({ code: 200, data: { allPassed: true, failedReasons: [] } })
    await mountPage([{ id: 4, status: 'COMPLETED' }])
    await wrapper.vm.$.setupState.handleClose({ id: 4 })
    await flushPromises()
    expect(mockProjectClose).toHaveBeenCalledWith(4)
  })
})

describe('project/form.vue 项目表单', () => {
  async function mountPage() {
    wrapper = mount(ProjectForm, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载预载签约公司下拉（业主单位为远程搜索不预载）', async () => {
    await mountPage()
    expect(mockCompanyList).toHaveBeenCalled()
    expect(mockOwnerList).not.toHaveBeenCalled() // searchOwner 由输入触发
  })

  it('编辑态提交走 updateProject（handleEdit 回显后 formData.id 存在）', async () => {
    mockProjectDetail.mockResolvedValue({ code: 200, data: { id: 88, projectName: '旧项目', projectNature: 'CONSTRUCTION', projectType: 'BUILDING', ownerCompanyId: 1, signingCompanyId: 2 } })
    await mountPage()
    const st = wrapper.vm.$.setupState
    // 模拟编辑态：直接设置 formData.id（页面级提交流由 e2e-real project-crud 覆盖，
    // 此处钉住 create/update 分流逻辑：id 存在走 update 分支）
    st.formRef = { validate: vi.fn(async () => true) }
    st.formData = { id: 88, projectName: '旧项目改', projectNature: 'CONSTRUCTION', projectType: 'BUILDING', ownerCompanyId: 1, signingCompanyId: 2 }
    await st.handleSubmit()
    await flushPromises()
    expect(mockProjectUpdate).toHaveBeenCalledTimes(1)
    expect((mockProjectUpdate.mock.calls as any)[0][0].id).toBe(88)
    expect(mockProjectCreate).not.toHaveBeenCalled()
  })

  it('必填规则配置：名称/性质/类型/业主/签约公司五项钉住', async () => {
    await mountPage()
    const msgs = Object.values(wrapper.vm.$.setupState.formRules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请输入项目名称')
    expect(msgs).toContain('请选择项目性质')
    expect(msgs).toContain('请选择项目类型')
    expect(msgs).toContain('请选择业主单位')
    expect(msgs).toContain('请选择签约公司')
  })
})

describe('project/detail.vue 项目详情', () => {
  it('挂载按路由 id 拉详情', async () => {
    wrapper = mount(ProjectDetail, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(mockProjectDetail).toHaveBeenCalled()
  })
})

describe('project/components/ProjectMember.vue 项目成员', () => {
  async function mountPage(members: any[] = []) {
    mockMembers.mockResolvedValue({ code: 200, data: members })
    wrapper = mount(ProjectMember, {
      global: { plugins: [ElementPlus], provide: { projectId: 1 } },
      props: { projectId: 1 },
    })
    await flushPromises()
    return wrapper
  }

  it('挂载加载成员列表与用户下拉数据源', async () => {
    await mountPage([{ id: 1, realName: '张三', roles: ['PM'] }])
    expect(mockMembers).toHaveBeenCalled()
  })

  it('移除成员调 removeProjectMember', async () => {
    await mountPage([{ id: 7, realName: '李四' }])
    await wrapper.vm.$.setupState.handleRemove({ id: 7 })
    await flushPromises()
    expect(mockRemoveMember).toHaveBeenCalled()
  })
})
