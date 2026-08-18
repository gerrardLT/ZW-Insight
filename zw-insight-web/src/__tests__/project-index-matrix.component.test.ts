/**
 * project/index.vue 项目列表页账本补测（2026-08 账本全量补齐 M1）
 *
 * @matrix A1-02 名称搜索重置页码 / A1-03 状态筛选 8 枚举 / A1-04 重置清空条件 /
 *   A1-05 分页 sizes / A1-06 DRAFT 行三按钮 / A1-07 COMPLETED 行结项按钮 /
 *   A1-08 非草稿行无编辑提交删除 / A1-09 提交二次确认取消不发请求 /
 *   A1-14 状态标签 8 态映射 / A-X4 删除引用拦截前端透传 Toast
 *
 * 分层纪律：本页断言均为纯前端行为（不发真实请求，api 层 mock）；
 * 提交/删除的后端状态流转由 e2e-real a1-project.spec.ts 覆盖。
 * 既有覆盖见 project-pages.component.test.ts（提交删除 API 调用、结项预检两分支）。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockProjectPage, mockProjectDelete, mockProjectSubmit, mockProjectClose, mockCloseCheck,
  mockConfirm, mockMessageSuccess, mockMessageError,
} = vi.hoisted(() => ({
  mockProjectPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockProjectDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProjectSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProjectClose: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockCloseCheck: vi.fn(async (): Promise<any> => ({ code: 200, data: { allPassed: true, failedReasons: [] } })),
  mockConfirm: vi.fn(async (): Promise<any> => 'confirm'),
  mockMessageSuccess: vi.fn(),
  mockMessageError: vi.fn(),
}))

vi.mock('@/api/project', () => ({
  getProjectPage: mockProjectPage, deleteProject: mockProjectDelete, submitProject: mockProjectSubmit,
  closeProject: mockProjectClose, getProjectCloseCheck: mockCloseCheck,
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return { ...actual, useRouter: () => ({ push: vi.fn(), back: vi.fn() }), useRoute: () => ({ query: {}, params: {} }) }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mockMessageSuccess, error: mockMessageError, warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm, alert: vi.fn(async () => 'ok') },
  }
})

import ProjectIndex from '@/views/project/index.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// ESM 兼容（package.json type=module 无 __dirname）
const __testDir = dirname(fileURLToPath(import.meta.url))

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
  mockConfirm.mockResolvedValue('confirm')
})

async function mountPage(records: any[]) {
  mockProjectPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(ProjectIndex, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('project/index.vue 账本补测（@matrix A1）', () => {
  it('@matrix A1-02 名称搜索重置 pageNum 为 1 并重新请求', async () => {
    await mountPage([])
    const st = wrapper.vm.$.setupState
    mockProjectPage.mockClear()
    st.queryParams.pageNum = 3
    st.queryParams.projectName = '滨江'
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    expect(mockProjectPage).toHaveBeenCalledTimes(1)
    expect(mockProjectPage.mock.calls[0][0]).toMatchObject({ pageNum: 1, projectName: '滨江' })
  })

  it('@matrix A1-03 状态筛选随查询下发 status 参数', async () => {
    await mountPage([])
    const st = wrapper.vm.$.setupState
    mockProjectPage.mockClear()
    st.queryParams.status = 'CONSTRUCTION'
    st.handleSearch()
    await flushPromises()
    expect(mockProjectPage.mock.calls[0][0].status).toBe('CONSTRUCTION')
  })

  it('@matrix A1-03 状态下拉提供 8 枚举选项', async () => {
    // el-option teleport 到 body，happy-dom 下容器外不可见；从模板源码静态钉住
    // 8 枚举与下拉渲染成对出现（源码 index.vue 筛选区状态 select）
    const tpl = readFileSync(resolve(__testDir, '../views/project/index.vue'), 'utf-8')
    const statusSelect = tpl.match(/<el-select v-model="queryParams\.status"[\s\S]*?<\/el-select>/)?.[0] || ''
    for (const s of ['DRAFT', 'FILED', 'TENDERING', 'WON', 'CONSTRUCTION', 'COMPLETED', 'CLOSING', 'CLOSED']) {
      expect(statusSelect, `状态下拉应含 ${s}`).toContain(`value="${s}"`)
    }
  })

  it('@matrix A1-04 重置清空条件并以 pageSize=10 重载', async () => {
    await mountPage([])
    const st = wrapper.vm.$.setupState
    st.queryParams.projectName = '脏值'
    st.queryParams.status = 'WON'
    st.queryParams.pageSize = 50
    st.queryParams.pageNum = 4
    mockProjectPage.mockClear()
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toMatchObject({ pageNum: 1, pageSize: 10, projectName: '', status: '', projectType: '' })
    expect(mockProjectPage).toHaveBeenCalledTimes(1)
  })

  it('@matrix A1-05 分页 page-sizes=[10,20,50,100] 配置钉住', async () => {
    const w = await mountPage([])
    const pager = w.findComponent({ name: 'ElPagination' })
    expect(pager.exists()).toBe(true)
    expect(pager.props('pageSizes')).toEqual([10, 20, 50, 100])
  })

  it('@matrix A1-06 DRAFT 行渲染 查看/编辑/提交/删除 四按钮', async () => {
    const w = await mountPage([{ id: 1, projectName: 'P1', status: 'DRAFT' }])
    const row = w.findAll('.el-table__row')[0]
    const btns = row.findAll('button').map((b: any) => b.text())
    expect(btns).toContain('查看')
    expect(btns).toContain('编辑')
    expect(btns).toContain('提交')
    expect(btns).toContain('删除')
    expect(btns).not.toContain('结项')
  })

  it('@matrix A1-07 COMPLETED 行显示结项按钮且无编辑/提交/删除', async () => {
    const w = await mountPage([{ id: 2, projectName: 'P2', status: 'COMPLETED' }])
    const btns = w.findAll('.el-table__row')[0].findAll('button').map((b: any) => b.text())
    expect(btns).toContain('结项')
    expect(btns).not.toContain('编辑')
    expect(btns).not.toContain('提交')
    expect(btns).not.toContain('删除')
  })

  it('@matrix A1-08 非草稿行（FILED/WON）无编辑/提交/删除按钮', async () => {
    const w = await mountPage([
      { id: 3, projectName: 'P3', status: 'FILED' },
      { id: 4, projectName: 'P4', status: 'WON' },
    ])
    for (const row of w.findAll('.el-table__row')) {
      const btns = row.findAll('button').map((b: any) => b.text())
      expect(btns).not.toContain('编辑')
      expect(btns).not.toContain('提交')
      expect(btns).not.toContain('删除')
    }
  })

  it('@matrix A1-09 提交二次确认取消 → 不发 submit 请求', async () => {
    await mountPage([{ id: 5, projectName: 'P5', status: 'DRAFT' }])
    mockConfirm.mockRejectedValueOnce(new Error('cancel'))
    mockProjectSubmit.mockClear()
    await wrapper.vm.$.setupState.handleSubmit({ id: 5 }).catch(() => { /* confirm reject 向外抛 */ })
    await flushPromises()
    expect(mockProjectSubmit).not.toHaveBeenCalled()
  })

  it('@matrix A1-14 状态标签 8 态中文映射与源码 statusMap 一致', async () => {
    const rows = [
      { id: 1, projectName: 'p', status: 'DRAFT' },
      { id: 2, projectName: 'p', status: 'FILED' },
      { id: 3, projectName: 'p', status: 'TENDERING' },
      { id: 4, projectName: 'p', status: 'WON' },
      { id: 5, projectName: 'p', status: 'CONSTRUCTION' },
      { id: 6, projectName: 'p', status: 'COMPLETED' },
      { id: 7, projectName: 'p', status: 'CLOSING' },
      { id: 8, projectName: 'p', status: 'CLOSED' },
    ]
    const w = await mountPage(rows)
    const tags = w.findAll('.el-table__row .el-tag').map((t: any) => t.text())
    expect(tags).toEqual(['草稿', '已报备', '招标中', '已中标', '施工中', '已竣工', '结项审批中', '已关闭'])
  })

  it('@matrix A-X4 删除失败（引用拦截）时前端不吞错——delete reject 向外抛出', async () => {
    await mountPage([{ id: 9, projectName: 'P9', status: 'DRAFT' }])
    mockProjectDelete.mockRejectedValueOnce(new Error('该项目已挂接合同，无法删除'))
    // handleSubmit/handleDelete 未 try-catch：异常经全局请求拦截器 Toast 后向外抛，
    // 此处钉住「无静默吞错」——调用方可见 reject（拦截器 Toast 由 request.test.ts 覆盖）
    await expect(wrapper.vm.$.setupState.handleDelete({ id: 9 })).rejects.toThrow()
  })
})
