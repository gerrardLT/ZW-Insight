/**
 * message/center/index.vue 消息中心组件测试（2026-08-15 P3 收尾批）
 * 未读/全部双 tab、已读标记、未读数（失败不阻塞页面）。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockUnread, mockAll, mockMarkRead, mockMarkAllRead, mockUnreadCount } = vi.hoisted(() => ({
  mockUnread: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockAll: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockMarkRead: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockMarkAllRead: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUnreadCount: vi.fn(async (): Promise<any> => ({ code: 200, data: 0 })),
}))

vi.mock('@/api/message', () => ({
  getUnreadMessages: mockUnread, getAllMessages: mockAll, markAsRead: mockMarkRead,
  markAllAsRead: mockMarkAllRead, getUnreadCount: mockUnreadCount,
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import MessageCenter from '@/views/message/center/index.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage() {
  wrapper = mount(MessageCenter, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('message/center/index.vue 消息中心', () => {
  it('挂载默认未读 tab：拉未读列表 + 未读数', async () => {
    mockUnreadCount.mockResolvedValue({ code: 200, data: 5 })
    const w = await mountPage()
    expect(mockUnread).toHaveBeenCalled()
    expect(mockAll).not.toHaveBeenCalled()
    expect(w.vm.$.setupState.unreadCount).toBe(5)
  })

  it('切换到全部 tab → 拉全部列表且重置页码', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.queryParams.pageNum = 3
    st.activeTab = 'all'
    mockUnread.mockClear()
    mockAll.mockClear()
    st.handleTabChange()
    await flushPromises()
    expect(mockAll).toHaveBeenCalled()
    expect(mockUnread).not.toHaveBeenCalled()
    expect(st.queryParams.pageNum).toBe(1)
  })

  it('标记单条已读：markAsRead + 未读数自减 + 未读 tab 下刷新列表', async () => {
    mockUnreadCount.mockResolvedValue({ code: 200, data: 5 })
    await mountPage()
    const st = wrapper.vm.$.setupState
    const row: any = { id: 11, isRead: false }
    mockUnread.mockClear()
    await st.handleMarkRead(row)
    await flushPromises()
    expect(mockMarkRead).toHaveBeenCalledWith(11)
    expect(row.isRead).toBe(true)
    expect(st.unreadCount).toBe(4) // 未读数自减而非重拉
    expect(mockUnread).toHaveBeenCalled() // 未读 tab 下刷新列表
  })

  it('全部已读 → 调 markAllAsRead、未读数归零并刷新', async () => {
    mockUnreadCount.mockResolvedValue({ code: 200, data: 3 })
    await mountPage()
    const st = wrapper.vm.$.setupState
    mockMarkAllRead.mockClear()
    mockUnread.mockClear()
    await st.handleMarkAllRead()
    await flushPromises()
    expect(mockMarkAllRead).toHaveBeenCalled()
    expect(st.unreadCount).toBe(0)
    expect(mockUnread).toHaveBeenCalled()
  })

  it('未读数获取失败不阻塞页面（静默降级仅计数，列表正常）', async () => {
    mockUnreadCount.mockRejectedValue(new Error('count down'))
    const w = await mountPage()
    expect(w.vm.$.setupState.unreadCount).toBe(0)
    expect(mockUnread).toHaveBeenCalled() // 列表加载不受影响
  })
})
