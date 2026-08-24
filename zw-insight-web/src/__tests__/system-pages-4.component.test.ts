/**
 * system 域角色/用户页组件测试（2026-08-15 P3 收尾批 8d）
 * user 页依赖 pinia userStore（当前登录用户自保护），经 setActivePinia 注入。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'

const {
  mockRoleList, mockRoleCreate, mockRoleUpdate, mockRoleDelete, mockRoleMenuIds, mockAssignMenus, mockMenuTree,
  mockUserPage, mockUserCreate, mockUserUpdate, mockUserDelete, mockUserStatus, mockBatchStatus,
  mockResetPwd, mockAssignRoles, mockOrgTree, mockRoleOptions, mockPostList,
  mockWarning,
} = vi.hoisted(() => {
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockRoleList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockRoleCreate: ok(), mockRoleUpdate: ok(), mockRoleDelete: ok(),
    mockRoleMenuIds: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockAssignMenus: ok(),
    mockMenuTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockUserPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockUserCreate: ok(), mockUserUpdate: ok(), mockUserDelete: ok(), mockUserStatus: ok(),
    mockBatchStatus: ok(), mockResetPwd: ok(), mockAssignRoles: ok(),
    mockOrgTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockRoleOptions: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockPostList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockWarning: vi.fn(),
  }
})

vi.mock('@/api/system', () => ({
  getRoleList: mockRoleList,
  createRole: mockRoleCreate, updateRole: mockRoleUpdate, deleteRole: mockRoleDelete,
  getRoleMenuIds: mockRoleMenuIds, assignRoleMenus: mockAssignMenus, getMenuTree: mockMenuTree,
  getUserPage: mockUserPage, createUser: mockUserCreate, updateUser: mockUserUpdate, deleteUser: mockUserDelete,
  updateUserStatus: mockUserStatus, batchUpdateUserStatus: mockBatchStatus,
  resetUserPassword: mockResetPwd, assignUserRoles: mockAssignRoles,
  getOrgTree: mockOrgTree, getPostList: mockPostList,
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Role from '@/views/system/role/index.vue'
import User from '@/views/system/user/index.vue'
import { useUserStore } from '@/stores/user'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('system/role/index.vue 角色管理', () => {
  async function mountPage() {
    mockRoleList.mockResolvedValue({ code: 200, data: [{ id: 1, roleName: '管理员', roleCode: 'ADMIN', dataScope: 'ALL' }] })
    mockMenuTree.mockResolvedValue({ code: 200, data: [{ id: 1, menuName: '系统', children: [{ id: 2, menuName: '用户' }] }] })
    wrapper = mount(Role, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载并行加载角色列表与菜单树', async () => {
    const w = await mountPage()
    expect(mockMenuTree).toHaveBeenCalled()
    expect(w.vm.$.setupState.menuTree).toHaveLength(1)
  })

  it('平铺接口数据树化后 getAllMenuKeys 总数不变（修复角色授权树平铺缺陷）', async () => {
    // 后端实际返回平铺列表；树化仅改变层级展示，节点集合不变，
    // 且 el-tree 为 check-strictly 父子不联动，勾选行为与层级无关
    mockRoleList.mockResolvedValue({ code: 200, data: [] })
    mockMenuTree.mockResolvedValue({ code: 200, data: [
      { id: 1, parentId: 0, menuName: '系统' },
      { id: 2, parentId: 1, menuName: '用户' },
      { id: 3, parentId: 1, menuName: '角色' },
    ] })
    const w = await mount(Role, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = w.vm.$.setupState
    expect(st.menuTree).toHaveLength(1)
    expect(st.menuTree[0].children.map((c: any) => c.id)).toEqual([2, 3])
    expect(st.getAllMenuKeys(st.menuTree).sort()).toEqual([1, 2, 3])
  })

  it('getDataScopeLabel：已知 scope 映射、空/未知回退「仅本人」', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    expect(st.getDataScopeLabel(undefined)).toBe('仅本人')
    expect(st.getDataScopeLabel('NOT_EXIST')).toBe('仅本人')
    expect(st.getDataScopeLabel('ALL')).not.toBe('')
  })

  it('getAllMenuKeys：树形遍历收集全部节点 id', async () => {
    const w = await mountPage()
    const keys = w.vm.$.setupState.getAllMenuKeys([
      { id: 1, children: [{ id: 2, children: [{ id: 3 }] }, { id: 4 }] },
    ])
    expect(keys).toEqual([1, 2, 3, 4])
  })

  it('选中角色拉取已分配菜单 id', async () => {
    const w = await mountPage()
    mockRoleMenuIds.mockResolvedValue({ code: 200, data: [1, 2] })
    mockRoleMenuIds.mockClear()
    await w.vm.$.setupState.handleSelectRole({ id: 1, roleName: '管理员' })
    await flushPromises()
    expect(mockRoleMenuIds).toHaveBeenCalledWith(1)
    expect(w.vm.$.setupState.currentRole.roleName).toBe('管理员')
  })

  it('新增角色走 createRole', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.roleName = '新角色'
    st.formData.roleCode = 'NEW'
    await st.handleSubmit()
    await flushPromises()
    expect(mockRoleCreate).toHaveBeenCalledTimes(1)
    expect(mockRoleUpdate).not.toHaveBeenCalled()
  })
})

describe('system/user/index.vue 用户管理', () => {
  async function mountPage() {
    setActivePinia(createPinia())
    const store = useUserStore()
    store.userInfo = { id: 99, username: 'admin' } // 当前登录用户（自保护测试基准）
    mockUserPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, realName: '张三' }], total: 1 } })
    wrapper = mount(User, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载用户分页与组织/角色/岗位下拉数据源', async () => {
    await mountPage()
    expect(mockUserPage).toHaveBeenCalled()
    expect(mockOrgTree).toHaveBeenCalled()
    expect(mockRoleList).toHaveBeenCalled() // 角色下拉选项复用 getRoleList
  })

  it('搜索重置页码、重置清空条件', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.queryParams.pageNum = 3
    mockUserPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
  })

  it('批量启停排除当前登录用户（自保护）', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleSelectionChange([{ id: 99 }, { id: 1 }])
    mockBatchStatus.mockClear()
    await st.handleBatchStatus(0)
    await flushPromises()
    // 当前用户 99 被过滤，仅提交 id=1
    expect(mockBatchStatus).toHaveBeenCalledWith([1], 0)
  })

  it('仅选中自己时批量操作被拦截提示', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleSelectionChange([{ id: 99 }])
    mockBatchStatus.mockClear()
    mockWarning.mockClear()
    await st.handleBatchStatus(1)
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('不能对自己执行此操作')
    expect(mockBatchStatus).not.toHaveBeenCalled()
  })

  it('重置密码：确认后调 resetUserPassword', async () => {
    await mountPage()
    await wrapper.vm.$.setupState.handleResetPwd({ id: 7, realName: '李四' })
    await flushPromises()
    expect(mockResetPwd).toHaveBeenCalledWith(7)
  })

  it('分配角色：打开弹窗回显已有角色，提交调 assignUserRoles', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAssignRole({ id: 5, roleIds: [1, 2] })
    await flushPromises()
    expect(st.roleDialogVisible).toBe(true)
    expect(st.selectedRoleIds).toEqual([1, 2])
    await st.handleRoleSubmit()
    await flushPromises()
    expect(mockAssignRoles).toHaveBeenCalledWith(5, [1, 2])
  })
})
