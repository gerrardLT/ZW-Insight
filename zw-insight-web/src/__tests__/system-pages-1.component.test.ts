/**
 * system 域岗位/版本/日志页组件测试（2026-08-15 P3 收尾批 8a）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockPostPage, mockPostCreate, mockPostUpdate, mockPostDelete, mockPostStatus,
  mockVersionList, mockCurrentVersion, mockVersionCreate,
  mockOperLog, mockLoginLog, mockExceptionLog,
} = vi.hoisted(() => ({
  mockPostPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockPostCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockPostUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockPostDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockPostStatus: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockVersionList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockCurrentVersion: vi.fn(async (): Promise<any> => ({ code: 200, data: null })),
  mockVersionCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockOperLog: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockLoginLog: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockExceptionLog: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
}))

vi.mock('@/api/system', () => ({
  getPostPage: mockPostPage, createPost: mockPostCreate, updatePost: mockPostUpdate,
  deletePost: mockPostDelete, updatePostStatus: mockPostStatus,
  getOperLogPage: mockOperLog, getLoginLogPage: mockLoginLog, getExceptionLogPage: mockExceptionLog,
}))
vi.mock('@/api/version', () => ({
  getVersionList: mockVersionList, getCurrentVersion: mockCurrentVersion, createVersion: mockVersionCreate,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Post from '@/views/system/post/index.vue'
import Version from '@/views/system/version/index.vue'
import Log from '@/views/system/log/index.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('system/post/index.vue 岗位管理', () => {
  async function mountPage(records: any[] = []) {
    mockPostPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Post, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载并渲染行', async () => {
    const w = await mountPage([{ id: 1, postName: '项目经理', postCode: 'PM', status: 1 }, { id: 2, postName: '施工员', postCode: 'CE', status: 0 }])
    expect(w.findAll('.el-table__row')).toHaveLength(2)
    expect(w.text()).toContain('项目经理')
  })

  it('必填规则：岗位名称 + 岗位编码', async () => {
    await mountPage()
    const msgs = Object.values(wrapper.vm.$.setupState.formRules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请输入岗位名称')
    expect(msgs).toContain('请输入岗位编码')
  })

  it('新增走 createPost、编辑走 updatePost（按 id 分流）', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.postName = '资料员'
    await st.handleSubmit()
    await flushPromises()
    expect(mockPostCreate).toHaveBeenCalledTimes(1)
    expect(mockPostUpdate).not.toHaveBeenCalled()
    // 编辑分流
    st.handleEdit({ id: 5, postName: '施工员', postCode: 'CE', sort: 1, remark: '' })
    await flushPromises()
    await st.handleSubmit()
    await flushPromises()
    expect(mockPostUpdate).toHaveBeenCalledTimes(1)
    expect((mockPostUpdate.mock.calls as any)[0][0].id).toBe(5)
  })

  it('状态切换：启用↔停用 确认后调 updatePostStatus', async () => {
    await mountPage([{ id: 3, postName: 'X', status: 1 }])
    await wrapper.vm.$.setupState.handleToggleStatus({ id: 3, status: 1 })
    await flushPromises()
    expect(mockPostStatus).toHaveBeenCalledWith(3, 0) // 1→0 停用
  })

  it('搜索重置页码、重置清空条件', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.queryParams.pageNum = 3
    mockPostPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ pageNum: 1, pageSize: 10, postName: '', status: undefined })
  })
})

describe('system/version/index.vue 版本管理', () => {
  async function mountPage() {
    mockVersionList.mockResolvedValue({ code: 200, data: [{ id: 1, versionNo: '1.2.0', changelog: '修复A\n修复B' }] })
    mockCurrentVersion.mockResolvedValue({ code: 200, data: { id: 1, versionNo: '1.2.0' } })
    wrapper = mount(Version, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载并行拉版本列表与当前版本', async () => {
    await mountPage()
    expect(mockVersionList).toHaveBeenCalled()
    expect(mockCurrentVersion).toHaveBeenCalled()
    expect(wrapper.vm.$.setupState.currentVersion?.versionNo).toBe('1.2.0')
  })

  it('版本号语义化规则：x.y.z 格式校验配置钉住', async () => {
    await mountPage()
    const rules: any = wrapper.vm.$.setupState.rules
    const versionRules = rules.versionNo
    expect(versionRules.some((r: any) => r.required)).toBe(true)
    const patternRule = versionRules.find((r: any) => r.pattern)
    expect(patternRule).toBeTruthy()
    expect(patternRule.pattern.test('1.2.3')).toBe(true)
    expect(patternRule.pattern.test('1.2')).toBe(false)
  })

  it('summarize：取更新日志首行非空内容，空值显 -', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    expect(st.summarize('修复A\n修复B')).toBe('修复A')
    expect(st.summarize('\n  \n首个有效行')).toBe('首个有效行')
    expect(st.summarize('')).toBe('-')
    expect(st.summarize(undefined)).toBe('-')
  })
})

describe('system/log/index.vue 日志查询', () => {
  async function mountPage() {
    wrapper = mount(Log, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载默认操作日志 tab', async () => {
    await mountPage()
    expect(mockOperLog).toHaveBeenCalled()
    expect(mockLoginLog).not.toHaveBeenCalled()
  })

  it('tab 切换路由到对应日志 API', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.activeTab = 'login'
    mockLoginLog.mockClear()
    st.handleTabChange()
    await flushPromises()
    expect(mockLoginLog).toHaveBeenCalled()
    st.activeTab = 'exception'
    mockExceptionLog.mockClear()
    st.handleTabChange()
    await flushPromises()
    expect(mockExceptionLog).toHaveBeenCalled()
  })

  it('时间区间参数组装 startTime/endTime、搜索重置页码', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.queryParams.timeRange = ['2026-08-01', '2026-08-15']
    st.queryParams.pageNum = 3
    mockOperLog.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    expect((mockOperLog.mock.calls as any)[0][0]).toMatchObject({
      pageNum: 1, operator: '', startTime: '2026-08-01', endTime: '2026-08-15',
    })
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.timeRange).toBeNull()
  })
})
