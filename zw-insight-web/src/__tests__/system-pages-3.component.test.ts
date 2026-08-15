/**
 * system 域机构/菜单/字典（双表）页组件测试（2026-08-15 P3 收尾批 8c）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockOrgTree, mockOrgDetail, mockOrgCreate, mockOrgUpdate, mockOrgDelete, mockOrgStatus,
  mockMenuTree, mockMenuCreate, mockMenuUpdate, mockMenuDelete,
  mockDictList, mockDictCreate, mockDictUpdate, mockDictDelete,
  mockDictItemTree, mockDictItemCreate, mockDictItemUpdate, mockDictItemDelete,
} = vi.hoisted(() => {
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockOrgTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockOrgDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockOrgCreate: ok(), mockOrgUpdate: ok(), mockOrgDelete: ok(), mockOrgStatus: ok(),
    mockMenuTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockMenuCreate: ok(), mockMenuUpdate: ok(), mockMenuDelete: ok(),
    mockDictList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockDictCreate: ok(), mockDictUpdate: ok(), mockDictDelete: ok(),
    mockDictItemTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockDictItemCreate: ok(), mockDictItemUpdate: ok(), mockDictItemDelete: ok(),
  }
})

vi.mock('@/api/system', () => ({
  getOrgTree: mockOrgTree, getOrgDetail: mockOrgDetail, createOrg: mockOrgCreate, updateOrg: mockOrgUpdate,
  deleteOrg: mockOrgDelete, updateOrgStatus: mockOrgStatus,
  getMenuTree: mockMenuTree, createMenu: mockMenuCreate, updateMenu: mockMenuUpdate, deleteMenu: mockMenuDelete,
  getDictList: mockDictList, createDict: mockDictCreate, updateDict: mockDictUpdate, deleteDict: mockDictDelete,
  getDictItemTree: mockDictItemTree, createDictItem: mockDictItemCreate, updateDictItem: mockDictItemUpdate, deleteDictItem: mockDictItemDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Org from '@/views/system/org/index.vue'
import Menu from '@/views/system/menu/index.vue'
import Dict from '@/views/system/dict/index.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('system/org/index.vue 机构管理（树形）', () => {
  async function mountPage() {
    mockOrgTree.mockResolvedValue({ code: 200, data: [{ id: 1, orgName: '总公司', children: [{ id: 2, orgName: '工程部' }] }] })
    wrapper = mount(Org, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载机构树', async () => {
    const w = await mountPage()
    expect(mockOrgTree).toHaveBeenCalled()
    expect(w.vm.$.setupState.orgTree).toHaveLength(1)
  })

  it('filterNode：空值全通过、按 orgName 包含过滤', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    expect(st.filterNode('', { orgName: '任意' })).toBe(true)
    expect(st.filterNode('工程', { orgName: '工程部' })).toBe(true)
    expect(st.filterNode('财务', { orgName: '工程部' })).toBe(false)
  })

  it('节点点击设置 currentOrg、新增子机构带 parentId', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.handleNodeClick({ id: 1, orgName: '总公司' })
    expect(st.currentOrg.orgName).toBe('总公司')
    st.handleAdd(1)
    await flushPromises()
    expect(st.dialogTitle).toBe('新增机构')
    expect(st.formData.parentId).toBe(1)
    expect(st.formData.orgType).toBe('DEPARTMENT') // 默认类型钉住
  })

  it('编辑回显 currentOrg、提交按 id 分流 create/update', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    // 未选中时编辑不打开弹窗
    st.handleEdit()
    expect(st.dialogVisible).toBe(false)
    st.handleNodeClick({ id: 2, orgName: '工程部', orgCode: 'GC', orgType: 'DEPARTMENT', parentId: 1, sortOrder: 1 })
    st.handleEdit()
    await flushPromises()
    expect(st.formData.id).toBe(2)
    await st.handleSubmit()
    await flushPromises()
    expect(mockOrgUpdate).toHaveBeenCalledTimes(1)
    expect(mockOrgCreate).not.toHaveBeenCalled()
  })
})

describe('system/menu/index.vue 菜单管理（树形）', () => {
  async function mountPage() {
    mockMenuTree.mockResolvedValue({ code: 200, data: [{ id: 1, menuName: '系统管理', children: [] }] })
    wrapper = mount(Menu, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载菜单树并包装顶级菜单选项', async () => {
    const w = await mountPage()
    expect(mockMenuTree).toHaveBeenCalled()
    const st = w.vm.$.setupState
    expect(st.menuTreeForSelect).toHaveLength(1)
    expect(st.menuTreeForSelect[0].menuName).toBe('顶级菜单') // 包装节点钉住
    expect(st.menuTreeForSelect[0].id).toBe(0)
  })

  it('展开/折叠切换触发树重渲染', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    expect(st.isExpandAll).toBe(true)
    st.toggleExpandAll()
    expect(st.isExpandAll).toBe(false)
  })

  it('新增菜单带 parentId、默认类型 DIR', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.handleAdd(0)
    await flushPromises()
    expect(st.formData.parentId).toBe(0)
    expect(st.formData.menuType).toBe('DIR')
  })

  it('提交按 id 分流 create/update', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.handleAdd(0)
    await flushPromises()
    st.formData.menuName = '新菜单'
    await st.handleSubmit()
    await flushPromises()
    expect(mockMenuCreate).toHaveBeenCalledTimes(1)
    expect(mockMenuUpdate).not.toHaveBeenCalled()
  })
})

describe('system/dict/index.vue 字典管理（双表）', () => {
  async function mountPage() {
    mockDictList.mockResolvedValue({ code: 200, data: [{ id: 1, dictName: '状态', dictCode: 'status' }] })
    mockDictItemTree.mockResolvedValue({ code: 200, data: [{ id: 11, label: '启用', value: '1' }] })
    wrapper = mount(Dict, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载字典列表', async () => {
    const w = await mountPage()
    expect(mockDictList).toHaveBeenCalled()
    expect(w.vm.$.setupState.dictList).toHaveLength(1)
  })

  it('选中字典联动加载字典项', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    mockDictItemTree.mockClear()
    st.handleDictSelect({ id: 1, dictName: '状态', dictCode: 'status' })
    await flushPromises()
    expect(mockDictItemTree).toHaveBeenCalled()
    expect(st.currentDict.dictCode).toBe('status')
  })

  it('新增字典走 createDict、编辑走 updateDict', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.handleAddDict()
    await flushPromises()
    st.dictFormData.dictName = '新字典'
    st.dictFormData.dictCode = 'new_dict'
    await st.handleDictSubmit()
    await flushPromises()
    expect(mockDictCreate).toHaveBeenCalledTimes(1)
    // 编辑分流
    st.handleEditDict({ id: 4, dictName: '状态', dictCode: 'status', sortOrder: 1 })
    await flushPromises()
    await st.handleDictSubmit()
    await flushPromises()
    expect(mockDictUpdate).toHaveBeenCalledTimes(1)
    expect((mockDictUpdate.mock.calls as any)[0][0].id).toBe(4)
  })

  it('新增字典项：需先选中字典（dictId 取自 currentDict）', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.handleDictSelect({ id: 1, dictName: '状态', dictCode: 'status' })
    await flushPromises()
    st.handleAddItem(0)
    await flushPromises()
    expect(st.itemDialogVisible).toBe(true)
    expect(st.itemFormData.dictId).toBe(1) // 关联当前字典钉住
  })
})
