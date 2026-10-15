/**
 * material/refund.vue（退货退款只读页）与 user/devices.vue（登录设备管理）
 * 组件测试（2026-08-15 P3 收尾批）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockRefundPage, mockRefundDetail, mockDevices, mockRevoke } = vi.hoisted(() => ({
  mockRefundPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockRefundDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockDevices: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockRevoke: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/material', () => ({
  getMaterialRefundPage: mockRefundPage, getMaterialRefundDetail: mockRefundDetail,
}))
vi.mock('@/api/device', () => ({
  getLoginDevices: mockDevices, revokeLoginDevice: mockRevoke,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Refund from '@/views/material/refund.vue'
import Devices from '@/views/user/devices.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('material/refund.vue 退货退款记录（只读）', () => {
  async function mountPage(records: any[] = []) {
    mockRefundPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Refund, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载并渲染行', async () => {
    const w = await mountPage([{ id: 1, status: 'DRAFT' }, { id: 2, status: 'APPROVED' }])
    expect(mockRefundPage).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(2)
  })

  it('状态映射：DRAFT/PENDING/APPROVED/REJECTED + 未知透传', async () => {
    const w = await mountPage([
      { id: 1, status: 'DRAFT' }, { id: 2, status: 'PENDING' },
      { id: 3, status: 'APPROVED' }, { id: 4, status: 'REJECTED' }, { id: 5, status: 'X1' },
    ])
    expect(w.text()).toContain('草稿')
    expect(w.text()).toContain('待审批')
    expect(w.text()).toContain('已通过')
    expect(w.text()).toContain('已驳回')
    expect(w.text()).toContain('X1')
  })

  it('搜索重置页码、重置清空 contractId', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.queryParams.contractId = 5
    st.queryParams.page = 3
    mockRefundPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.page).toBe(1)
    expect((mockRefundPage.mock.calls as any)[0][0].contractId).toBe(5)
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ page: 1, size: 10, contractId: undefined })
  })

  it('查看详情：拉 detail 并打开弹窗', async () => {
    mockRefundDetail.mockResolvedValue({ code: 200, data: { id: 7, refundAmount: 100 } })
    await mountPage([{ id: 7 }])
    const st = wrapper.vm.$.setupState
    await st.handleViewDetail({ id: 7 })
    await flushPromises()
    expect(mockRefundDetail).toHaveBeenCalledWith(7)
    expect(st.detailVisible).toBe(true)
    expect(st.detail.refundAmount).toBe(100)
  })
})

describe('user/devices.vue 登录设备管理', () => {
  async function mountPage(devices: any[] = []) {
    mockDevices.mockResolvedValue({ code: 200, data: devices })
    wrapper = mount(Devices, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载设备列表', async () => {
    await mountPage([{ id: 1, deviceName: 'Chrome/Win' }, { id: 2, deviceName: 'App/iOS' }])
    expect(mockDevices).toHaveBeenCalled()
    expect(wrapper.vm.$.setupState.deviceList).toHaveLength(2)
  })

  it('formatLocation：省份|城市 → 空格连接，空值显 -', async () => {
    await mountPage([])
    const st = wrapper.vm.$.setupState
    expect(st.formatLocation('浙江|杭州')).toBe('浙江 杭州')
    expect(st.formatLocation('')).toBe('-')
    expect(st.formatLocation(undefined)).toBe('-')
    expect(st.formatLocation('|')).toBe('-') // 全分隔符过滤后为空
  })

  it('远程注销：确认后调 revoke 并刷新列表', async () => {
    await mountPage([{ id: 3, deviceName: 'Chrome/Win' }])
    mockDevices.mockClear()
    await wrapper.vm.$.setupState.handleRevoke({ id: 3, deviceName: 'Chrome/Win' })
    await flushPromises()
    expect(mockRevoke).toHaveBeenCalledWith(3)
    expect(mockDevices).toHaveBeenCalled() // 注销后刷新
  })
})
