/**
 * message/notice/index.vue 通知管理组件测试（2026-08-15 P3 长尾补测）
 * @matrix P3 长尾：消息模块页面级覆盖（列表/搜索/新增/发布状态机）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockPage, mockCreate, mockPublish } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockPublish: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/message', () => ({
  getNoticePage: mockPage,
  createNotice: mockCreate,
  publishNotice: mockPublish,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Notice from '@/views/message/notice/index.vue'

const RECORDS = [
  { id: 1, title: 'E2E通知A', status: 'DRAFT', createTime: '2026-08-01 10:00:00' },
  { id: 2, title: 'E2E通知B', status: 'PUBLISHED', createTime: '2026-08-02 10:00:00' },
]

let wrapper: any = null
async function mountPage() {
  wrapper = mount(Notice, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}
const st = () => wrapper.vm.$.setupState
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

describe('notice.vue 通知管理（@matrix P3 长尾）', () => {
  it('挂载加载列表并渲染状态标签', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: RECORDS, total: 2 } })
    const w = await mountPage()
    expect(mockPage).toHaveBeenCalled()
    expect(w.text()).toContain('草稿')
    expect(w.text()).toContain('已发布')
  })

  it('搜索重置 pageNum 并重新查询', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: RECORDS, total: 2 } })
    const w = await mountPage()
    mockPage.mockClear()
    st().queryParams.pageNum = 2
    st().handleSearch()
    await flushPromises()
    expect(st().queryParams.pageNum).toBe(1)
    expect((mockPage.mock.calls as any)[0][0].pageNum).toBe(1)
  })

  it('新增必填守卫配置 + 创建组装 formData', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    mockCreate.mockResolvedValue({ code: 200 })
    const w = await mountPage()
    const rules = st().formRules
    const msgs = Object.values(rules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请输入通知标题')
    expect(msgs).toContain('请输入通知内容')
    st().handleAdd()
    await flushPromises()
    st().formData.title = 'E2E新通知'
    st().formData.content = '内容'
    await st().handleFormSubmit()
    await flushPromises()
    expect(mockCreate).toHaveBeenCalledWith({ title: 'E2E新通知', content: '内容' })
  })

  it('发布确认后调 publishNotice 并刷新', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: RECORDS, total: 2 } })
    mockPublish.mockResolvedValue({ code: 200 })
    const w = await mountPage()
    mockPage.mockClear()
    await st().handlePublish(RECORDS[0])
    await flushPromises()
    expect(mockPublish).toHaveBeenCalledWith(1)
    expect(mockPage).toHaveBeenCalled()
  })

  it('已发布行发布按钮禁用（状态机 UI 守卫）', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: RECORDS, total: 2 } })
    const w = await mountPage()
    const rows = w.findAll('.el-table__row')
    expect(rows.length).toBe(2)
    const draftBtn = rows[0].find('button')
    const publishedBtn = rows[1].find('button')
    expect((draftBtn.element as HTMLButtonElement).disabled, '草稿行发布按钮应可用').toBe(false)
    expect((publishedBtn.element as HTMLButtonElement).disabled, '已发布行发布按钮应禁用').toBe(true)
  })
})
