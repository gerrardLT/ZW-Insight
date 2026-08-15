/**
 * message/announcement/index.vue 公告管理组件测试（2026-08-15 P3 方向1 续）
 * @matrix P3 长尾：消息模块页面级覆盖（CRUD 标准 6 用例 + 发布/撤回状态机 + 状态标签映射）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockPage, mockCreate, mockUpdate, mockDelete, mockPublish, mockRevoke,
} = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockPublish: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockRevoke: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/message', () => ({
  getAnnouncementPage: mockPage,
  createAnnouncement: mockCreate,
  updateAnnouncement: mockUpdate,
  deleteAnnouncement: mockDelete,
  publishAnnouncement: mockPublish,
  revokeAnnouncement: mockRevoke,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Announcement from '@/views/message/announcement/index.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'announcement/index.vue 公告管理',
  component: Announcement,
  pageMock: mockPage,
  createMock: mockCreate,
  updateMock: mockUpdate,
  deleteMock: mockDelete,
  addButtonText: '新增公告',
  requiredError: '请输入公告标题',
  records: [
    { id: 1, title: '系统升级公告', content: '内容1', scope: 'ALL', status: 'DRAFT', createTime: '2026-08-01 10:00:00', publishTime: null },
    { id: 2, title: '放假通知', content: '内容2', scope: 'ALL', status: 'PUBLISHED', createTime: '2026-08-02 10:00:00', publishTime: '2026-08-02 12:00:00' },
  ],
})

// ─── 公告特有的发布/撤回状态机与状态标签映射 ───
describe('announcement/index.vue 状态机扩展用例', () => {
  let wrapper: any = null
  afterEach(() => {
    if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  })

  async function mountWith(records: any[]) {
    mockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Announcement, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('状态标签映射：DRAFT=草稿 / PUBLISHED=已发布 / REVOKED=已撤回', async () => {
    const w = await mountWith([
      { id: 1, title: 'A', status: 'DRAFT', createTime: '2026-08-01' },
      { id: 2, title: 'B', status: 'PUBLISHED', createTime: '2026-08-01' },
      { id: 3, title: 'C', status: 'REVOKED', createTime: '2026-08-01' },
    ])
    expect(w.text()).toContain('草稿')
    expect(w.text()).toContain('已发布')
    expect(w.text()).toContain('已撤回')
  })

  it('发布公告：确认后调 publishAnnouncement 并刷新列表', async () => {
    mockPublish.mockResolvedValue({ code: 200 })
    const w = await mountWith([
      { id: 1, title: '待发布公告', status: 'DRAFT', createTime: '2026-08-01' },
    ])
    mockPage.mockClear()
    await w.vm.$.setupState.handlePublish({ id: 1 })
    await flushPromises()
    expect(mockPublish).toHaveBeenCalledWith(1)
    expect(mockPage).toHaveBeenCalled() // 发布后刷新
  })

  it('撤回公告：确认后调 revokeAnnouncement 并刷新列表', async () => {
    mockRevoke.mockResolvedValue({ code: 200 })
    const w = await mountWith([
      { id: 2, title: '已发布公告', status: 'PUBLISHED', createTime: '2026-08-01' },
    ])
    mockPage.mockClear()
    await w.vm.$.setupState.handleRevoke({ id: 2 })
    await flushPromises()
    expect(mockRevoke).toHaveBeenCalledWith(2)
    expect(mockPage).toHaveBeenCalled()
  })

  it('编辑态提交走 updateAnnouncement（id + formData 双参）', async () => {
    mockUpdate.mockResolvedValue({ code: 200 })
    const w = await mountWith([
      { id: 3, title: '旧标题', content: '旧内容', scope: 'ALL', isTop: false, status: 'DRAFT', createTime: '2026-08-01' },
    ])
    const st = w.vm.$.setupState
    st.handleEdit({ id: 3, title: '新标题', content: '新内容', scope: 'ALL', isTop: true })
    await flushPromises()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockUpdate).toHaveBeenCalledTimes(1)
    expect((mockUpdate.mock.calls as any)[0][0]).toBe(3)
    expect((mockUpdate.mock.calls as any)[0][1]).toMatchObject({ title: '新标题', isTop: true })
  })
})
