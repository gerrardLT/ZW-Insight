/**
 * 系统域矩阵补盲测试（2026-08-20 账本全量补齐 M8 @matrix D-2 系统管理 D4~D17）
 *
 * 覆盖账本缺口（既有 system-pages-1~4 未覆盖部分）：
 *   - D-4-4/5  组织停用/启用（handleToggleStatus 双向）
 *   - D-5-8    用户手机号仅 required 无格式校验（现状钉住）
 *   - D-6-4/5  角色菜单树 check-strictly + 全选/半选态（updateCheckAllState）
 *   - D-7-2/3/4/5 菜单折叠重建 + 必填规则 + DIR/MENU/BUTTON 条件字段
 *   - D-8-3/4/5  字典前端过滤 + 字典项必填 + 子项 parentId
 *   - D-9-3    岗位 sort min=0/max=9999 源码钉住
 *   - D-10-2/7 设置 NUMBER valueRange min/max 解析 + 恢复默认值
 *   - D-11-4/6/7 模板文件选择未真正上传（缺陷钉住）+ PRINT 编辑内容 + 必填
 *   - D-12-3/6/7/8 打印模板 6 选项 + 前端过滤 + 预览变量提取 + 空渲染
 *   - D-13-7   日志无批量删除按钮（差距钉住）
 *   - D-16-4/5/6/8 版本 releaseDate 必填/changelog 非必填/摘要截断/日志弹窗
 *   - D-15-4/7 备份恢复 confirm type=error + SCHEDULED/MANUAL 标签
 *   - D-17-1/3 监控 el-empty 占位 + api/monitor.ts 前端零调用（盲点钉住）
 *
 * 后端约束类缺口（D-4-7/D-5-7/D-6-7/D-8-6/D-14-8/D-16-3 等重复冲突 409）
 * 由 L5-API/E2E 层覆盖，vitest 不重复模拟。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { resolve, join } from 'node:path'
import ElementPlus from 'element-plus'

const {
  // org
  mockOrgTree, mockOrgStatus,
  // menu
  mockMenuTree, mockMenuCreate,
  // dict
  mockDictList, mockDictItemTree,
  // config
  mockConfigByGroup, mockResetDefault,
  // role
  mockRoleList, mockRoleMenuIds,
  // template
  mockTemplateList, mockTemplateCreate, mockTemplateUpdate,
  // print-template
  mockPrintPage, mockPrintDetail, mockRender,
  // version
  mockVersionList, mockCurrentVersion,
  // backup
  mockBackupPage,
  mockConfirm, mockError,
} = vi.hoisted(() => {
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockOrgTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockOrgStatus: ok(),
    mockMenuTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockMenuCreate: ok(),
    mockDictList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockDictItemTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockConfigByGroup: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockResetDefault: ok(),
    mockRoleList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockRoleMenuIds: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockTemplateList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockTemplateCreate: ok(),
    mockTemplateUpdate: ok(),
    mockPrintPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockPrintDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockRender: vi.fn(async (): Promise<any> => ({ code: 200, data: '<html></html>' })),
    mockVersionList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockCurrentVersion: vi.fn(async (): Promise<any> => ({ code: 200, data: null })),
    mockBackupPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockConfirm: vi.fn(async (_m: string, _t: string, _o?: any): Promise<string> => 'confirm'),
    mockError: vi.fn(),
  }
})

vi.mock('@/api/system', () => ({
  getOrgTree: mockOrgTree, getOrgDetail: vi.fn(), createOrg: vi.fn(), updateOrg: vi.fn(),
  deleteOrg: vi.fn(), updateOrgStatus: mockOrgStatus,
  getMenuTree: mockMenuTree, createMenu: mockMenuCreate, updateMenu: vi.fn(), deleteMenu: vi.fn(),
  getDictList: mockDictList, createDict: vi.fn(), updateDict: vi.fn(), deleteDict: vi.fn(),
  getDictItemTree: mockDictItemTree, createDictItem: vi.fn(), updateDictItem: vi.fn(), deleteDictItem: vi.fn(),
  getPostPage: vi.fn(), createPost: vi.fn(), updatePost: vi.fn(), deletePost: vi.fn(), updatePostStatus: vi.fn(),
  getConfigByGroup: mockConfigByGroup, batchUpdateConfig: vi.fn(), resetConfigToDefault: mockResetDefault,
  getRoleList: mockRoleList, createRole: vi.fn(), updateRole: vi.fn(), deleteRole: vi.fn(),
  getRoleMenuIds: mockRoleMenuIds, assignRoleMenus: vi.fn(), updateRoleDataScope: vi.fn(),
  getUserPage: vi.fn(), createUser: vi.fn(), updateUser: vi.fn(), deleteUser: vi.fn(),
  updateUserStatus: vi.fn(), batchUpdateUserStatus: vi.fn(), resetUserPassword: vi.fn(), assignUserRoles: vi.fn(),
  getOrgTree2: vi.fn(), getPostList: vi.fn(),
}))
vi.mock('@/api/batch', () => ({
  getTemplateList: mockTemplateList, createTemplate: mockTemplateCreate,
  updateTemplate: mockTemplateUpdate, deleteTemplate: vi.fn(),
}))
vi.mock('@/api/print-template', () => ({
  getPrintTemplatePage: mockPrintPage, getPrintTemplateDetail: mockPrintDetail,
  createPrintTemplate: vi.fn(), updatePrintTemplate: vi.fn(), deletePrintTemplate: vi.fn(),
  renderPrintTemplate: mockRender,
}))
vi.mock('@/api/version', () => ({
  getVersionList: mockVersionList, getCurrentVersion: mockCurrentVersion, createVersion: vi.fn(),
}))
vi.mock('@/api/backup', () => ({
  getBackupPage: mockBackupPage, executeBackup: vi.fn(), downloadBackup: vi.fn(),
  deleteBackup: vi.fn(), restoreBackup: vi.fn(),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: mockError, warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

import Org from '@/views/system/org/index.vue'
import Role from '@/views/system/role/index.vue'
import Menu from '@/views/system/menu/index.vue'
import Dict from '@/views/system/dict/index.vue'
import Config from '@/views/system/config/index.vue'
import Template from '@/views/system/template/index.vue'
import PrintTemplate from '@/views/system/print-template/index.vue'
import Version from '@/views/system/version/index.vue'
import Backup from '@/views/system/backup/index.vue'
import Monitor from '@/views/system/monitor/index.vue'

/** 源码守卫：CRLF 归一化后做包含断言 */
function norm(p: string): string {
  return readFileSync(resolve(__dirname, '..', p), 'utf-8').replace(/\r\n/g, '\n')
}

/** 递归收集目录下所有 .ts/.vue 文件 */
function collectFiles(dir: string, acc: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) collectFiles(full, acc)
    else if (/\.(ts|vue)$/.test(name)) acc.push(full)
  }
  return acc
}

let wrapper: any = null
beforeEach(() => {
  vi.clearAllMocks()
  mockConfirm.mockResolvedValue('confirm')
})
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

describe('system/org 启停（@matrix D-4-4/5）', () => {
  async function mountOrg() {
    mockOrgTree.mockResolvedValue({ code: 200, data: [{ id: 1, orgName: '总公司', status: 1, children: [] }] })
    wrapper = mount(Org, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-4-4 停用：status=1 节点 toggle → updateOrgStatus(id, 0) + confirm', async () => {
    const w = await mountOrg()
    const st = w.vm.$.setupState
    st.handleNodeClick({ id: 1, orgName: '总公司', status: 1 })
    await st.handleToggleStatus()
    await flushPromises()
    expect(mockConfirm).toHaveBeenCalled()
    expect(mockOrgStatus).toHaveBeenCalledWith(1, 0)
    expect(st.currentOrg.status).toBe(0)
  })

  it('D-4-5 启用：status=0 节点 toggle → updateOrgStatus(id, 1)', async () => {
    const w = await mountOrg()
    const st = w.vm.$.setupState
    st.handleNodeClick({ id: 2, orgName: '工程部', status: 0 })
    await st.handleToggleStatus()
    await flushPromises()
    expect(mockOrgStatus).toHaveBeenCalledWith(2, 1)
    expect(st.currentOrg.status).toBe(1)
  })
})

describe('system/role 权限树（@matrix D-6-4/5）', () => {
  async function mountRole() {
    mockRoleList.mockResolvedValue({ code: 200, data: [{ id: 1, roleName: '管理员', roleCode: 'ADMIN', dataScope: 'ALL' }] })
    mockMenuTree.mockResolvedValue({ code: 200, data: [{ id: 1, menuName: '系统', children: [{ id: 2, menuName: '用户' }, { id: 3, menuName: '角色' }] }] })
    wrapper = mount(Role, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-6-4 菜单树 check-strictly 父子不联动（源码钉住）', () => {
    const src = norm('views/system/role/index.vue')
    expect(src).toContain('show-checkbox')
    expect(src).toContain('check-strictly')
    expect(src).toContain('node-key="id"')
  })

  it('D-6-5 全选/半选态：updateCheckAllState 按勾选数切换，handleCheckAll 全勾/清空', async () => {
    const w = await mountRole()
    const st = w.vm.$.setupState
    const setCheckedKeys = vi.fn()
    st.menuTreeRef = { getCheckedKeys: () => [1], setCheckedKeys }
    st.updateCheckAllState()
    expect(st.checkAll, '勾选 1/3 非全选').toBe(false)
    expect(st.isIndeterminate, '部分勾选为半选态').toBe(true)

    st.menuTreeRef = { getCheckedKeys: () => [1, 2, 3], setCheckedKeys }
    st.updateCheckAllState()
    expect(st.checkAll).toBe(true)
    expect(st.isIndeterminate).toBe(false)

    st.handleCheckAll(true)
    expect(setCheckedKeys).toHaveBeenCalledWith([1, 2, 3])
    st.handleCheckAll(false)
    expect(setCheckedKeys).toHaveBeenCalledWith([])
    expect(st.isIndeterminate).toBe(false)
  })
})

describe('system/menu 条件字段（@matrix D-7-2/3/4/5）', () => {
  it('D-7-2 折叠/展开：refreshTable 先置 false 后 nextTick 恢复 true', async () => {
    mockMenuTree.mockResolvedValue({ code: 200, data: [{ id: 1, menuName: '系统', children: [] }] })
    wrapper = mount(Menu, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    expect(st.isExpandAll).toBe(true)
    st.toggleExpandAll()
    expect(st.refreshTable, '切换瞬间置 false 触发重渲染').toBe(false)
    expect(st.isExpandAll).toBe(false)
    await flushPromises() // nextTick
    expect(st.refreshTable).toBe(true)
  })

  it('D-7-3 必填规则：menuName/menuType 两键', async () => {
    wrapper = mount(Menu, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const rules = wrapper.vm.$.setupState.formRules
    expect(Object.keys(rules).sort()).toEqual(['menuName', 'menuType'])
    expect(rules.menuName[0].required).toBe(true)
    expect(rules.menuType[0].required).toBe(true)
  })

  it('D-7-4/5 BUTTON/MENU/DIR 条件字段 v-if 源码钉住', () => {
    const src = norm('views/system/menu/index.vue')
    expect(src).toContain('v-if="formData.menuType !== \'BUTTON\'" label="路由路径"')
    expect(src).toContain('v-if="formData.menuType === \'MENU\'" label="组件路径"')
    expect(src).toContain('v-if="formData.menuType === \'BUTTON\'" label="权限标识"')
    expect(src).toContain('v-if="formData.menuType !== \'BUTTON\'" label="图标"')
  })
})

describe('system/dict 过滤与字典项（@matrix D-8-3/4/5）', () => {
  async function mountDict() {
    mockDictList.mockResolvedValue({ code: 200, data: [
      { id: 1, dictName: '项目状态', dictCode: 'project_status' },
      { id: 2, dictName: '合同类型', dictCode: 'contract_type' },
    ] })
    mockDictItemTree.mockResolvedValue({ code: 200, data: [] })
    wrapper = mount(Dict, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-8-3 前端过滤：按名称/编码即时过滤（不发请求）', async () => {
    const w = await mountDict()
    const st = w.vm.$.setupState
    mockDictList.mockClear()
    st.searchText = '合同'
    await flushPromises()
    expect(st.filteredDictList).toHaveLength(1)
    expect(st.filteredDictList[0].dictCode).toBe('contract_type')
    st.searchText = 'project_status'
    expect(st.filteredDictList.map((d: any) => d.id)).toEqual([1])
    expect(mockDictList, '前端过滤不重新请求').not.toHaveBeenCalled()
  })

  it('D-8-4 字典项必填：label/value 两键 required', async () => {
    const w = await mountDict()
    const rules = w.vm.$.setupState.itemFormRules
    expect(Object.keys(rules).sort()).toEqual(['label', 'value'])
    expect(rules.label[0].required).toBe(true)
    expect(rules.value[0].required).toBe(true)
  })

  it('D-8-5 新增子项：parentId 传入且 dictId 关联当前字典', async () => {
    const w = await mountDict()
    const st = w.vm.$.setupState
    st.handleDictSelect({ id: 1, dictName: '项目状态', dictCode: 'project_status' })
    await flushPromises()
    st.handleAddItem(88)
    await flushPromises()
    expect(st.itemFormData.parentId).toBe(88)
    expect(st.itemFormData.dictId).toBe(1)
  })
})

describe('system/config 分型与恢复默认（@matrix D-9-3/D-10-2/7）', () => {
  it('D-9-3 岗位 sort min=0/max=9999 源码钉住', () => {
    const src = norm('views/system/post/index.vue')
    expect(src, '排序 input-number 上下界钉住').toContain(':min="0" :max="9999"')
  })

  it('D-10-2 NUMBER 型：valueRange 正则解析 min/max', async () => {
    mockConfigByGroup.mockResolvedValue({ code: 200, data: [
      { configKey: 'login.max-fail', configName: '最大失败次数', configValue: '5', valueType: 'NUMBER', valueRange: '1-10' },
    ] })
    wrapper = mount(Config, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    const item = st.configList[0]
    expect(st.getNumberMin(item)).toBe(1)
    expect(st.getNumberMax(item)).toBe(10)
    expect(st.getNumberMin({ ...item, valueRange: null })).toBeUndefined()
  })

  it('D-10-7 恢复默认值：confirm 后调 resetConfigToDefault(key) 并刷新分组', async () => {
    mockConfigByGroup.mockResolvedValue({ code: 200, data: [
      { configKey: 'site.name', configName: '站点名称', configValue: 'ZW', valueType: 'STRING', valueRange: null },
    ] })
    wrapper = mount(Config, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mockConfigByGroup.mockClear()
    await wrapper.vm.$.setupState.handleResetDefault({ configKey: 'site.name', configName: '站点名称' })
    await flushPromises()
    expect(mockConfirm).toHaveBeenCalled()
    expect(mockResetDefault).toHaveBeenCalledWith('site.name')
    expect(mockConfigByGroup).toHaveBeenCalledWith('security') // 刷新当前分组
  })
})

describe('system/template 上传缺陷钉住（@matrix D-11-4/6/7）', () => {
  async function mountTemplate() {
    mockTemplateList.mockResolvedValue({ code: 200, data: [] })
    wrapper = mount(Template, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-11-4 文件选择未真正上传：仅弹 info、fileId 恒 null（缺陷现状钉住）', async () => {
    const w = await mountTemplate()
    const st = w.vm.$.setupState
    st.handleAdd()
    st.handleFileChange({ name: 'a.xlsx', raw: new Blob(['x']) } as any)
    expect(st.formData.fileId, 'fileId 未被赋值（盲点）').toBeNull()
    const src = norm('views/system/template/index.vue')
    expect(src).toContain("ElMessage.info(`已选择文件: ${file.name}`)")
    expect(src).toContain(':auto-upload="false"')
  })

  it('D-11-6 PRINT 编辑内容弹窗：占位符提示 + 保存走 updateTemplate(id, {templateContent})', async () => {
    const w = await mountTemplate()
    const st = w.vm.$.setupState
    st.handleEditContent({ id: 9, templateContent: '<p>{{projectName}}</p>' })
    expect(st.contentDialogVisible).toBe(true)
    expect(st.templateContent).toBe('<p>{{projectName}}</p>')
    const src = norm('views/system/template/index.vue')
    expect(src).toContain('作为占位符')
    await st.handleSaveContent()
    await flushPromises()
    expect(mockTemplateUpdate).toHaveBeenCalledWith(9, { templateContent: '<p>{{projectName}}</p>' })
  })

  it('D-11-7 必填三键：templateName/moduleCode/templateType', async () => {
    const w = await mountTemplate()
    const rules = w.vm.$.setupState.formRules
    expect(Object.keys(rules).sort()).toEqual(['moduleCode', 'templateName', 'templateType'])
    Object.values(rules).forEach((r: any) => expect(r[0].required).toBe(true))
  })
})

describe('system/print-template 预览链（@matrix D-12-3/6/7/8）', () => {
  async function mountPrint(records: any[] = [], total?: number) {
    mockPrintPage.mockResolvedValue({ code: 200, data: { records, total: total ?? records.length } })
    wrapper = mount(PrintTemplate, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-12-3 businessType 六选项完整', async () => {
    const w = await mountPrint()
    expect(w.vm.$.setupState.businessTypeOptions.map((o: any) => o.value)).toEqual(
      ['CONTRACT', 'BUDGET', 'MATERIAL', 'FINANCE', 'LABOR', 'MACHINE'],
    )
  })

  it('D-12-6 名称前端过滤（后端无模糊查询参数，源码注释明示）', async () => {
    await mountPrint([
      { id: 1, templateName: '合同打印模板', businessType: 'CONTRACT' },
      { id: 2, templateName: '预算打印模板', businessType: 'BUDGET' },
    ])
    const st = wrapper.vm.$.setupState
    st.queryParams.templateName = '合同'
    await st.handleSearch()
    await flushPromises()
    expect(st.tableData.map((r: any) => r.id)).toEqual([1])
    expect(norm('views/system/print-template/index.vue')).toContain('模板名称为前端过滤')
  })

  it('D-12-7 预览：{{var}} 正则提取变量填「示例-x」后调 renderPrintTemplate', async () => {
    mockPrintDetail.mockResolvedValue({ code: 200, data: { id: 5, templateContent: '<p>{{ projectName }}/{{amount}}</p>' } })
    await mountPrint([{ id: 5, templateName: 'T' }])
    const write = vi.fn(); const open = vi.fn(); const close = vi.fn()
    const spy = vi.spyOn(window, 'open').mockReturnValue({ document: { open, write, close } } as any)
    await wrapper.vm.$.setupState.handlePreview({ id: 5 })
    await flushPromises()
    expect(mockPrintDetail).toHaveBeenCalledWith(5)
    expect(mockRender).toHaveBeenCalledWith({ templateId: 5, variables: { projectName: '示例-projectName', amount: '示例-amount' } })
    expect(write).toHaveBeenCalledWith('<html></html>')
    spy.mockRestore()
  })

  it('D-12-8 渲染结果为空：error 提示且不开窗', async () => {
    mockPrintDetail.mockResolvedValue({ code: 200, data: { id: 6, templateContent: 'x' } })
    mockRender.mockResolvedValueOnce({ code: 200, data: '' })
    await mountPrint([{ id: 6, templateName: 'T' }])
    const spy = vi.spyOn(window, 'open')
    await wrapper.vm.$.setupState.handlePreview({ id: 6 })
    await flushPromises()
    expect(mockError).toHaveBeenCalledWith('渲染结果为空，无法预览')
    expect(spy).not.toHaveBeenCalled()
    spy.mockRestore()
  })

  it('D-12 删除末页唯一行回退页码', async () => {
    await mountPrint([{ id: 7, templateName: 'T' }], 21)
    const st = wrapper.vm.$.setupState
    st.queryParams.pageNum = 3
    mockPrintPage.mockClear()
    await st.handleDelete({ id: 7, templateName: 'T' })
    await flushPromises()
    expect(st.queryParams.pageNum, '删空当前页回退一页').toBe(2)
    expect(mockPrintPage).toHaveBeenCalled()
  })
})

describe('system/version 规则与弹窗（@matrix D-16-4/5/6/8）', () => {
  async function mountVersion() {
    mockVersionList.mockResolvedValue({ code: 200, data: [{ id: 1, versionNo: '1.2.0', changelog: '修复A\n修复B' }] })
    mockCurrentVersion.mockResolvedValue({ code: 200, data: null })
    wrapper = mount(Version, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-16-4/5 releaseDate 必填、changelog 无规则（非必填）', async () => {
    const w = await mountVersion()
    const rules = w.vm.$.setupState.rules
    expect(rules.releaseDate[0].required).toBe(true)
    expect(rules.changelog).toBeUndefined()
  })

  it('D-16-6 摘要超 60 字截断 + …', async () => {
    const w = await mountVersion()
    const st = w.vm.$.setupState
    const longLine = '甲'.repeat(70)
    expect(st.summarize(longLine)).toBe('甲'.repeat(60) + '…')
    expect(st.summarize('短行')).toBe('短行')
  })

  it('D-16-8 查看日志弹窗：detailRow 全文 + 空日志兜底文案源码钉住', async () => {
    const w = await mountVersion()
    const src = norm('views/system/version/index.vue')
    expect(src).toContain('（无更新日志）')
    expect(src).toContain('<pre>{{ detailRow?.changelog')
    const st = w.vm.$.setupState
    st.detailRow = { versionNo: '1.2.0', changelog: '全文' }
    st.detailVisible = true
    await flushPromises()
    expect(st.detailVisible).toBe(true)
    expect(w.text()).toContain('v1.2.0 更新日志')
  })
})

describe('system/backup 恢复确认（@matrix D-15-4/7）', () => {
  it('D-15-7 SCHEDULED/MANUAL 标签渲染源码钉住', () => {
    const src = norm('views/system/backup/index.vue')
    expect(src).toContain("row.backupType === 'SCHEDULED' ? '定时' : '手动'")
  })

  it('D-15-4 恢复二次确认：confirm type=error + 确认后调 restoreBackup', async () => {
    const { restoreBackup } = await import('@/api/backup') as any
    mockBackupPage.mockResolvedValue({ code: 200, data: { records: [{ id: 7, fileName: 'bk.sql', status: 'SUCCESS', storagePath: '/bk' }], total: 1 } })
    wrapper = mount(Backup, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.$.setupState.handleRestore({ id: 7, fileName: 'bk.sql' })
    await flushPromises()
    expect(mockConfirm).toHaveBeenCalledWith(
      expect.stringContaining('不可撤销'),
      '数据库恢复确认',
      expect.objectContaining({ type: 'error' }),
    )
    expect(restoreBackup).toHaveBeenCalledWith(7)
  })
})

describe('system/user 手机号规则现状（@matrix D-5-8）', () => {
  it('D-5-8 手机号仅 required 无格式校验（现状钉住，盲点）', () => {
    const src = norm('views/system/user/index.vue')
    expect(src).toContain("phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }]")
    expect(src).not.toMatch(/phone[\s\S]{0,100}pattern/)
  })
})

describe('system/log 批删差距（@matrix D-13-7）', () => {
  it('D-13-7 源码无批量删除入口（差距现状钉住）', () => {
    const src = norm('views/system/log/index.vue')
    expect(src).not.toContain('批量删除')
    expect(src).not.toContain('type="selection"')
  })
})

describe('system/monitor 占位与闲置 API（@matrix D-17-1/3）', () => {
  it('D-17-1 占位页渲染 el-empty「待实现（任务 10.3）」', async () => {
    wrapper = mount(Monitor, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.find('.el-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('待实现（任务 10.3）')
  })

  it('D-17-3 api/monitor.ts 存在但前端业务代码零调用（盲点钉住）', () => {
    const srcDir = resolve(__dirname, '..')
    const candidates = [...collectFiles(join(srcDir, 'views')), ...collectFiles(join(srcDir, 'components')), ...collectFiles(join(srcDir, 'stores')), ...collectFiles(join(srcDir, 'composables'))]
    const callers = candidates.filter(f => readFileSync(f, 'utf-8').includes('@/api/monitor'))
    expect(callers, 'monitor API 无任何前端调用方').toEqual([])
    // api/monitor.ts 文件本身存在（闲置资产实证）
    expect(readFileSync(join(srcDir, 'api', 'monitor.ts'), 'utf-8').length).toBeGreaterThan(0)
  })
})
