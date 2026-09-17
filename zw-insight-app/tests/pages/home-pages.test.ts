// @vitest-environment happy-dom
/**
 * home/workbench/message-center/project-archive 页面级组件测试
 *（2026-08-16 P3 方向2 批 4）
 *
 * @dcloudio/uni-app 生命周期钩子（onShow/onLoad/onPullDownRefresh）经
 * vi.hoisted 捕获回调，测试中手工触发以驱动数据加载（真实行为不变，
 * 仅替换平台生命周期注册点）。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

const hooks = vi.hoisted(() => ({
  onShowCb: null as any,
  onHideCb: null as any,
  onLoadCb: null as any,
  pullRefreshCb: null as any,
}))

vi.mock('@dcloudio/uni-app', () => ({
  onShow: (cb: any) => { hooks.onShowCb = cb },
  onHide: (cb: any) => { hooks.onHideCb = cb },
  onLoad: (cb: any) => { hooks.onLoadCb = cb },
  onPullDownRefresh: (cb: any) => { hooks.pullRefreshCb = cb },
}))

vi.mock('@/api/common', () => ({
  getCompanyOverview: vi.fn(),
  getUnreadCount: vi.fn(),
  getUnreadMessages: vi.fn(),
  getProjectList: vi.fn(),
  getTodoTasks: vi.fn(),
  getAnnouncements: vi.fn(),
  getNotices: vi.fn(),
  getAllMessages: vi.fn(),
  markMessageRead: vi.fn(),
  markAllMessagesRead: vi.fn(),
  getProjectArchive: vi.fn(),
}))

vi.mock('@/api/shortcut', () => ({
  getUserShortcuts: vi.fn(),
  getAvailableShortcuts: vi.fn(),
  batchSaveShortcuts: vi.fn(),
}))

import HomePage from '@/pages/home/index.vue'
import WorkbenchPage from '@/pages/workbench/index.vue'
import MessageCenter from '@/pages/message-center/index.vue'
import ArchivePage from '@/pages/project/archive.vue'
import {
  getCompanyOverview, getUnreadCount, getUnreadMessages, getProjectList,
  getTodoTasks, getAnnouncements, getNotices, getAllMessages,
  markMessageRead, markAllMessagesRead, getProjectArchive,
} from '@/api/common'
import { getUserShortcuts } from '@/api/shortcut'
import { resetUniStorage, getUni } from '../setup'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  hooks.onShowCb = null
  hooks.onHideCb = null
  hooks.onLoadCb = null
  hooks.pullRefreshCb = null
  ;(getUni() as any).navigateTo = vi.fn()
  ;(getUni() as any).stopPullDownRefresh = vi.fn()
})

describe('home/index.vue 首页', () => {
  it('统计卡金额按万换算，快捷入口按 sortOrder 排序，未读徽标展示', async () => {
    vi.mocked(getCompanyOverview).mockResolvedValue({ code: 200, data: { projectTotal: 12, totalContractAmount: 5_000_000, totalIncome: 1_230_000, advanceFund: 0 } })
    vi.mocked(getUnreadCount).mockResolvedValue({ code: 200, data: 3 })
    vi.mocked(getUnreadMessages).mockResolvedValue({ code: 200, data: { records: [{ id: 1, title: '审批提醒', createdAt: '2026-08-16' }] } })
    vi.mocked(getUserShortcuts).mockResolvedValue({ code: 200, data: [
      { shortcutId: 2, menuName: '乙', menuIcon: 'b', menuPath: '/b', sortOrder: 2 },
      { shortcutId: 1, menuName: '甲', menuIcon: 'a', menuPath: '/a', sortOrder: 1 },
    ] })

    const wrapper = mount(HomePage)
    await flushPromises()

    const values = wrapper.findAll('.stat-value').map((n) => n.text())
    expect(values[0]).toBe('12')
    expect(values[1]).toBe('500.0')
    expect(values[2]).toBe('123.0')
    expect(values[3]).toBe('0') // advanceFund 0 → formatWan '0'
    expect(wrapper.find('.badge').text()).toBe('3')
    expect(wrapper.findAll('.shortcut-item').map((n) => n.text())).toEqual(['a甲', 'b乙'])
    expect(wrapper.text()).toContain('审批提醒')
    wrapper.unmount()
  })

  it('快捷入口点击 navigateTo 对应路径，编辑入口跳 shortcut-edit', async () => {
    vi.mocked(getCompanyOverview).mockResolvedValue({ code: 200, data: {} })
    vi.mocked(getUnreadCount).mockResolvedValue({ code: 200, data: 0 })
    vi.mocked(getUnreadMessages).mockResolvedValue({ code: 200, data: { records: [] } })
    vi.mocked(getUserShortcuts).mockResolvedValue({ code: 200, data: [
      { shortcutId: 1, menuName: '甲', menuIcon: 'a', menuPath: '/pages/x', sortOrder: 1 },
    ] })

    const wrapper = mount(HomePage)
    await flushPromises()

    await wrapper.find('.shortcut-item').trigger('click')
    expect((getUni() as any).navigateTo).toHaveBeenCalledWith({ url: '/pages/x' })

    await wrapper.find('.edit-entry').trigger('click')
    expect((getUni() as any).navigateTo).toHaveBeenCalledWith({ url: '/pages/mine/shortcut-edit' })
    wrapper.unmount()
  })
})

describe('workbench/index.vue 工作台', () => {
  function mockBase() {
    vi.mocked(getCompanyOverview).mockResolvedValue({ code: 200, data: {} })
    vi.mocked(getTodoTasks).mockResolvedValue({ code: 200, data: { total: 7, records: [{ id: 'T1', processName: '付款审批', startUserName: '张三', processInstanceId: 'PI-1' }] } })
  }

  it('待办总数展示；项目满 10 条可加载更多，不足则 hasMore=false', async () => {
    mockBase()
    const pageOne = Array.from({ length: 10 }, (_, i) => ({ id: i + 1, projectName: `P${i}` }))
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: pageOne } })

    const wrapper = mount(WorkbenchPage)
    hooks.onShowCb?.()
    await flushPromises()

    // P0 Req7：待办区取前 5 条，同次请求带回总数与列表
    expect(vi.mocked(getTodoTasks)).toHaveBeenCalledWith({ page: 1, size: 5 })
    expect(wrapper.vm.projects.length).toBe(10)
    expect(wrapper.vm.hasMore).toBe(true)

    // 第二页仅 3 条 → hasMore 置 false
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [{ id: 11 }, { id: 12 }, { id: 13 }] } })
    wrapper.vm.loadMore()
    await flushPromises()

    expect(vi.mocked(getProjectList)).toHaveBeenLastCalledWith({ page: 2, size: 10 })
    expect(wrapper.vm.projects.length).toBe(13)
    expect(wrapper.vm.hasMore).toBe(false)
    wrapper.unmount()
  })

  it('项目行点击跳档案页带 projectId', async () => {
    mockBase()
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [{ id: 88, projectName: 'X' }] } })
    const wrapper = mount(WorkbenchPage)
    hooks.onShowCb?.()
    await flushPromises()

    wrapper.vm.goArchive(88)
    expect((getUni() as any).navigateTo).toHaveBeenCalledWith({ url: '/pages/project/archive?projectId=88' })
    wrapper.unmount()
  })

  it('P0 Req7 三分区失败态+重试：接口失败不静默，重试重新发起同一请求', async () => {
    vi.mocked(getCompanyOverview).mockRejectedValue(new Error('服务异常'))
    vi.mocked(getTodoTasks).mockRejectedValue(new Error('服务异常'))
    vi.mocked(getProjectList).mockRejectedValue(new Error('服务异常'))

    const wrapper = mount(WorkbenchPage)
    hooks.onShowCb?.()
    await flushPromises()

    expect(wrapper.vm.overviewFailed).toBe(true)
    expect(wrapper.vm.todoFailed).toBe(true)
    expect(wrapper.vm.projectsFailed).toBe(true)
    expect(wrapper.text()).toContain('项目看板加载失败')
    expect(wrapper.text()).toContain('重试')

    // 恢复后重试：失败态清除并重新拉取
    vi.mocked(getCompanyOverview).mockResolvedValue({ code: 200, data: { inProgressCount: 2 } })
    vi.mocked(getTodoTasks).mockResolvedValue({ code: 200, data: { total: 0, records: [] } })
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [] } })
    await wrapper.vm.loadOverview()
    await wrapper.vm.refreshTodo()
    await wrapper.vm.loadProjects()
    expect(wrapper.vm.overviewFailed).toBe(false)
    expect(wrapper.vm.todoFailed).toBe(false)
    expect(wrapper.vm.projectsFailed).toBe(false)
    expect(wrapper.vm.overview.inProgressCount).toBe(2)
    wrapper.unmount()
  })

  it('P0 Req7 待办列表前 5 条展示，点击跳审批详情带 taskId/processInstanceId', async () => {
    mockBase()
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [] } })
    const wrapper = mount(WorkbenchPage)
    hooks.onShowCb?.()
    await flushPromises()

    expect(wrapper.vm.todoTasks).toHaveLength(1)
    expect(wrapper.text()).toContain('付款审批')
    wrapper.vm.goApprovalDetail({ id: 'T1', processInstanceId: 'PI-1' })
    expect((getUni() as any).navigateTo).toHaveBeenCalledWith({ url: '/pages/approval/detail?taskId=T1&processInstanceId=PI-1' })
    wrapper.unmount()
  })

  it('P0 Req7 待办 60s 轮询：onShow 启动，到点刷新，onHide 停止', async () => {
    vi.useFakeTimers()
    mockBase()
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [] } })
    const wrapper = mount(WorkbenchPage)
    hooks.onShowCb?.()
    await flushPromises()
    const callsAfterShow = vi.mocked(getTodoTasks).mock.calls.length

    vi.advanceTimersByTime(60 * 1000)
    await flushPromises()
    expect(vi.mocked(getTodoTasks).mock.calls.length).toBe(callsAfterShow + 1)

    hooks.onHideCb?.()
    vi.advanceTimersByTime(180 * 1000)
    await flushPromises()
    expect(vi.mocked(getTodoTasks).mock.calls.length).toBe(callsAfterShow + 1)

    // 重新显示后轮询恢复
    hooks.onShowCb?.()
    await flushPromises()
    const callsAfterReshow = vi.mocked(getTodoTasks).mock.calls.length
    vi.advanceTimersByTime(60 * 1000)
    await flushPromises()
    expect(vi.mocked(getTodoTasks).mock.calls.length).toBe(callsAfterReshow + 1)

    hooks.onHideCb?.()
    wrapper.unmount()
    vi.useRealTimers()
  })
})

describe('message-center/index.vue 信息中心', () => {
  it('公告 tab 请求带 status=PUBLISHED；切 tab 重置分页并请求对应接口', async () => {
    vi.mocked(getAnnouncements).mockResolvedValue({ code: 200, data: { records: [{ id: 1, title: '公告A' }] } })
    vi.mocked(getNotices).mockResolvedValue({ code: 200, data: { records: [{ id: 2, title: '通知B' }] } })

    const wrapper = mount(MessageCenter)
    hooks.onShowCb?.()
    await flushPromises()

    expect(vi.mocked(getAnnouncements)).toHaveBeenCalledWith(expect.objectContaining({ status: 'PUBLISHED', page: 1, size: 15 }))

    wrapper.vm.switchTab('notice')
    await flushPromises()
    expect(vi.mocked(getNotices)).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 15 }))
    expect(wrapper.vm.list.map((m: any) => m.id)).toEqual([2])
    wrapper.unmount()
  })

  it('消息 tab 点击未读项标记已读；全部已读批量调用', async () => {
    vi.mocked(getAllMessages).mockResolvedValue({ code: 200, data: { records: [{ id: 9, title: '预警', isRead: 0 }] } })
    vi.mocked(markMessageRead).mockResolvedValue({ code: 200 })
    vi.mocked(markAllMessagesRead).mockResolvedValue({ code: 200 })

    const wrapper = mount(MessageCenter)
    wrapper.vm.switchTab('message')
    await flushPromises()

    const item = wrapper.vm.list[0]
    await wrapper.vm.onItemClick(item)
    expect(vi.mocked(markMessageRead)).toHaveBeenCalledWith(9)
    expect(item.isRead).toBe(1)

    await wrapper.vm.handleReadAll()
    expect(vi.mocked(markAllMessagesRead)).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('S3.1 ZwiCell 化：未读红点仅消息 tab 渲染，时间走短格式，内容进 desc 行', async () => {
    vi.mocked(getAllMessages).mockResolvedValue({
      code: 200,
      data: {
        records: [
          { id: 1, title: '预警A', content: '内容A', isRead: 0, createTime: '2026-01-02 03:04:05' },
          { id: 2, title: '预警B', isRead: 1, createTime: '2026-01-03 03:04:05' },
        ],
      },
    })

    const wrapper = mount(MessageCenter)
    wrapper.vm.switchTab('message')
    await flushPromises()

    expect(wrapper.findAll('.cell-row')).toHaveLength(2)
    // 未读点仅 isRead=0 那一条
    expect(wrapper.findAll('.msg-dot')).toHaveLength(1)
    // 时间降噪：完整时间戳不上屏
    expect(wrapper.findAll('.msg-time')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('2026-01-02 03:04:05')
    // 内容摘要落在 .cell-desc（页面 :deep 限两行）
    expect(wrapper.find('.cell-desc').text()).toBe('内容A')

    // 公告 tab：后端无 isRead 语义，即使数据带 isRead=0 也不渲染未读点
    vi.mocked(getAnnouncements).mockResolvedValue({
      code: 200,
      data: { records: [{ id: 3, title: '公告C', isRead: 0, publishTime: '2026-01-04 03:04:05' }] },
    })
    wrapper.vm.switchTab('announcement')
    await flushPromises()
    expect(wrapper.findAll('.cell-row')).toHaveLength(1)
    expect(wrapper.findAll('.msg-dot')).toHaveLength(0)
    wrapper.unmount()
  })

  it('S3.1 空态走 ZwiEmptyState（契约 .empty + 双主题插画）', async () => {
    vi.mocked(getAnnouncements).mockResolvedValue({ code: 200, data: { records: [] } })
    const wrapper = mount(MessageCenter)
    hooks.onShowCb?.()
    await flushPromises()

    expect(wrapper.find('.empty').exists()).toBe(true)
    expect(wrapper.find('.empty-illustration').exists()).toBe(true)
    expect(wrapper.text()).toContain('暂无公告')
    wrapper.unmount()
  })
})

describe('project/archive.vue 项目档案页', () => {
  it('onLoad 带 projectId 加载档案数据（后端嵌套契约：data.project）', async () => {
    vi.mocked(getProjectArchive).mockResolvedValue({
      code: 200,
      data: { project: { id: 55, projectName: '滨江一期', contractAmount: 2_000_000 }, fundSummary: {} },
    })

    const wrapper = mount(ArchivePage)
    hooks.onLoadCb?.({ projectId: '55' })
    await flushPromises()

    expect(vi.mocked(getProjectArchive)).toHaveBeenCalledWith(55)
    expect(wrapper.vm.project.projectName).toBe('滨江一期')
    expect(wrapper.vm.loading).toBe(false)
    wrapper.unmount()
  })

  it('onLoad 无 projectId 不请求，loading 置 false', async () => {
    const wrapper = mount(ArchivePage)
    hooks.onLoadCb?.({})
    await flushPromises()

    expect(vi.mocked(getProjectArchive)).not.toHaveBeenCalled()
    expect(wrapper.vm.loading).toBe(false)
    wrapper.unmount()
  })

  it('S3.1 ZwiSectionCard + ZwiCell kv 化：字段对齐后端嵌套契约（project/fundSummary）', async () => {
    vi.mocked(getProjectArchive).mockResolvedValue({
      code: 200,
      data: {
        project: {
          id: 55,
          projectName: '滨江花园一期',
          projectCode: 'PRJ-001',
          status: 'CONSTRUCTION',
          ownerCompanyName: '某业主',
          projectNature: '住宅',
          projectType: '总包',
        },
        fundSummary: { totalIncome: 1_000_000, totalExpense: 500_000, contractAmount: 2_000_000 },
      },
    })

    const wrapper = mount(ArchivePage)
    hooks.onLoadCb?.({ projectId: '55' })
    await flushPromises()

    // 两分区均走 ZwiSectionCard（进度卡已移除：后端 getProjectArchive 无 progress 数据源）
    expect(wrapper.findAll('.section-title').map((t) => t.text())).toEqual(['项目信息', '财务概况'])
    // 7 条项目信息 + 3 条财务 = 10 条 kv 行
    expect(wrapper.findAll('.cell-kv')).toHaveLength(10)
    expect(wrapper.text()).toContain('滨江花园一期')
    expect(wrapper.text()).toContain('施工中') // 状态枚举→中文映射
    expect(wrapper.text()).toContain('200.00万') // 合同金额走 fundSummary
    expect(wrapper.text()).toContain('50.00万') // 利润 = 100万 - 50万
    wrapper.unmount()
  })

  it('S3.1 加载失败走 ZwiEmptyState error 变体 + 重试 CTA（critique P3）', async () => {
    vi.mocked(getProjectArchive).mockRejectedValue(new Error('服务异常'))

    const wrapper = mount(ArchivePage)
    hooks.onLoadCb?.({ projectId: '55' })
    await flushPromises()

    expect(wrapper.find('.empty-error').exists()).toBe(true)
    expect(wrapper.text()).toContain('项目档案加载失败')
    expect(wrapper.find('.retry-btn').exists()).toBe(true)
    wrapper.unmount()
  })
})
