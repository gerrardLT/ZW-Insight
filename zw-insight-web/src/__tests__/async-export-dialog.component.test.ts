/**
 * AsyncExportDialog 组件测试（2026-08-14 前端深度补测·组件试点）
 *
 * 异步导出对话框：启动导出 → 2s 轮询状态机（PENDING→COMPLETED/FAILED）。
 * 覆盖：启动导出与轮询、COMPLETED 停止轮询并出下载按钮、
 * 启动失败/轮询失败置 FAILED、卸载清除定时器（防泄漏）。
 * 使用 fake timers 精确控制 2s 轮询节拍。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() } }
})
vi.mock('@/api/batch', () => ({
  startExport: vi.fn(),
  getExportStatus: vi.fn(),
  downloadExportFile: vi.fn()
}))

import { startExport, getExportStatus, downloadExportFile } from '@/api/batch'
import AsyncExportDialog from '@/components/AsyncExportDialog.vue'

const mockStart = vi.mocked(startExport)
const mockStatus = vi.mocked(getExportStatus)
const mockDownload = vi.mocked(downloadExportFile)

function mountDialog() {
  return mount(AsyncExportDialog, {
    props: { visible: false, moduleCode: 'project' },
    global: { plugins: [ElementPlus] },
    attachTo: document.body
  })
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
  document.body.innerHTML = ''
})

describe('AsyncExportDialog 组件', () => {
  it('打开：启动导出并进入轮询（PENDING 加载态）', async () => {
    mockStart.mockResolvedValue({ data: 99 } as any)
    mockStatus.mockResolvedValue({ data: { status: 'PROCESSING', progress: 30 } } as any)
    const wrapper = mountDialog()

    await wrapper.setProps({ visible: true })
    await flushPromises()

    expect(mockStart).toHaveBeenCalledWith('project', undefined)
    expect(wrapper.text()).toContain('正在导出数据')

    // 推进 2s 触发一次轮询
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    expect(mockStatus).toHaveBeenCalledWith(99)

    wrapper.unmount()
  })

  it('轮询到 COMPLETED：停止轮询并展示下载按钮', async () => {
    mockStart.mockResolvedValue({ data: 1 } as any)
    mockStatus.mockResolvedValue({ data: { status: 'COMPLETED', progress: 100 } } as any)
    const wrapper = mountDialog()

    await wrapper.setProps({ visible: true })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    expect(wrapper.text()).toContain('导出完成')
    expect(wrapper.findAll('button').some(b => b.text().includes('下载文件'))).toBe(true)

    // 已停止轮询：再推进多个周期无新调用
    const callsAfterComplete = mockStatus.mock.calls.length
    await vi.advanceTimersByTimeAsync(10000)
    expect(mockStatus.mock.calls.length).toBe(callsAfterComplete)

    wrapper.unmount()
  })

  it('点击「下载文件」：调用下载接口并触发浏览器下载', async () => {
    mockStart.mockResolvedValue({ data: 2 } as any)
    mockStatus.mockResolvedValue({ data: { status: 'COMPLETED', progress: 100 } } as any)
    mockDownload.mockResolvedValue(new ArrayBuffer(8) as any)

    const createObjectURL = vi.fn(() => 'blob:mock')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL })
    HTMLAnchorElement.prototype.click = vi.fn()

    const wrapper = mountDialog()
    await wrapper.setProps({ visible: true })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    const dlBtn = wrapper.findAll('button').find(b => b.text().includes('下载文件'))
    await dlBtn!.trigger('click')
    await flushPromises()

    expect(mockDownload).toHaveBeenCalledWith(2)
    expect(createObjectURL).toHaveBeenCalled()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock')

    wrapper.unmount()
    vi.unstubAllGlobals()
  })

  it('启动导出失败：置 FAILED 并展示「启动导出失败」', async () => {
    mockStart.mockRejectedValue(new Error('boom'))
    const wrapper = mountDialog()

    await wrapper.setProps({ visible: true })
    await flushPromises()

    expect(wrapper.text()).toContain('导出失败')
    expect(wrapper.text()).toContain('启动导出失败')

    wrapper.unmount()
  })

  it('轮询到 FAILED：停止轮询并展示后端错误信息', async () => {
    mockStart.mockResolvedValue({ data: 3 } as any)
    mockStatus.mockResolvedValue({ data: { status: 'FAILED', errorMsg: '模板不存在' } } as any)
    const wrapper = mountDialog()

    await wrapper.setProps({ visible: true })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    expect(wrapper.text()).toContain('导出失败')
    expect(wrapper.text()).toContain('模板不存在')

    const calls = mockStatus.mock.calls.length
    await vi.advanceTimersByTimeAsync(6000)
    expect(mockStatus.mock.calls.length).toBe(calls)

    wrapper.unmount()
  })

  it('卸载清除轮询定时器（无泄漏）', async () => {
    mockStart.mockResolvedValue({ data: 4 } as any)
    mockStatus.mockResolvedValue({ data: { status: 'PROCESSING', progress: 10 } } as any)
    const wrapper = mountDialog()

    await wrapper.setProps({ visible: true })
    await flushPromises()
    wrapper.unmount()

    await vi.advanceTimersByTimeAsync(10000)
    expect(mockStatus).not.toHaveBeenCalled()
  })
})
