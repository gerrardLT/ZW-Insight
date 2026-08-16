// @vitest-environment happy-dom
/**
 * approval + material 域页面级组件测试（2026-08-16 P3 方向2 批 5）
 *
 * 覆盖 approval/index.vue（三 tab 分流+分页）、approval/detail.vue
 *（详情加载/通过/退回意见守卫）、material/inbound.vue 与 outbound.vue
 *（三段校验+离线项目加载+提交载荷）。
 * 豁免：material/return.vue——pages.json 未注册路由（孤儿页），且 api/common
 * 无 saveMaterialReturn、后端 MaterialRefundController 无提交端点，双端断链，
 * 已登记台账待决策（删除或补功能），不做页面测试。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

const hooks = vi.hoisted(() => ({
  onShowCb: null as any,
  onLoadCb: null as any,
}))

vi.mock('@dcloudio/uni-app', () => ({
  onShow: (cb: any) => { hooks.onShowCb = cb },
  onLoad: (cb: any) => { hooks.onLoadCb = cb },
  onPullDownRefresh: () => {},
}))

vi.mock('@/api/common', () => ({
  getTodoTasks: vi.fn(),
  getDoneTasks: vi.fn(),
  getMyInitiatedTasks: vi.fn(),
  completeTask: vi.fn(),
  rejectTask: vi.fn(),
  getProjectList: vi.fn(),
  getMaterialDict: vi.fn(),
  saveMaterialInbound: vi.fn(),
  saveMaterialOutbound: vi.fn(),
}))

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import ApprovalIndex from '@/pages/approval/index.vue'
import ApprovalDetail from '@/pages/approval/detail.vue'
import InboundPage from '@/pages/material/inbound.vue'
import OutboundPage from '@/pages/material/outbound.vue'
import {
  getTodoTasks, getDoneTasks, getMyInitiatedTasks, completeTask,
  rejectTask, getProjectList, saveMaterialInbound, saveMaterialOutbound,
} from '@/api/common'
import request from '@/utils/request'
import { resetUniStorage, getUni } from '../setup'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  hooks.onShowCb = null
  hooks.onLoadCb = null
  ;(getUni() as any).navigateTo = vi.fn()
  ;(getUni() as any).navigateBack = vi.fn()
})

describe('approval/index.vue 审批列表页', () => {
  it('三 tab 分流调用对应接口，我发起的 tab 状态映射审批中/已完成', async () => {
    vi.mocked(getTodoTasks).mockResolvedValue({ code: 200, data: { records: [{ id: 1 }] } })
    vi.mocked(getDoneTasks).mockResolvedValue({ code: 200, data: { records: [{ id: 2 }] } })
    vi.mocked(getMyInitiatedTasks).mockResolvedValue({ code: 200, data: { records: [{ id: 3, status: 'RUNNING' }] } })

    const wrapper = mount(ApprovalIndex)
    hooks.onShowCb?.()
    await flushPromises()
    expect(vi.mocked(getTodoTasks)).toHaveBeenCalledWith({ page: 1, size: 15 })

    wrapper.vm.switchTab('done')
    await flushPromises()
    expect(vi.mocked(getDoneTasks)).toHaveBeenCalledWith({ page: 1, size: 15 })

    wrapper.vm.switchTab('initiated')
    await flushPromises()
    expect(vi.mocked(getMyInitiatedTasks)).toHaveBeenCalled()
    expect(wrapper.vm.statusText({ status: 'RUNNING' })).toBe('审批中')
    expect(wrapper.vm.statusText({ status: 'APPROVED' })).toBe('已完成')
    wrapper.unmount()
  })

  it('行点击跳详情页带 taskId 与 processInstanceId', async () => {
    vi.mocked(getTodoTasks).mockResolvedValue({ code: 200, data: { records: [] } })
    const wrapper = mount(ApprovalIndex)
    hooks.onShowCb?.()
    await flushPromises()

    wrapper.vm.goDetail({ taskId: 'T9', processInstanceId: 'PI-1' })
    expect((getUni() as any).navigateTo).toHaveBeenCalledWith({
      url: '/pages/approval/detail?taskId=T9&processInstanceId=PI-1',
    })
    wrapper.unmount()
  })
})

describe('approval/detail.vue 审批详情页', () => {
  it('onLoad 加载详情；通过提交 completeTask 三参并延迟返回', async () => {
    vi.useFakeTimers()
    vi.mocked(request).mockResolvedValue({ code: 200, data: { title: '付款审批', applicant: '张三' } })
    vi.mocked(completeTask).mockResolvedValue({ code: 200 })

    const wrapper = mount(ApprovalDetail)
    hooks.onLoadCb?.({ taskId: 'T1', processInstanceId: 'PI-9' })
    await flushPromises()

    expect(vi.mocked(request)).toHaveBeenCalledWith(expect.objectContaining({ url: '/v1/workflow/approval/detail/T1' }))
    expect(wrapper.vm.detail.title).toBe('付款审批')

    wrapper.vm.comment = '同意'
    await wrapper.vm.handleApprove()
    await flushPromises()
    expect(vi.mocked(completeTask)).toHaveBeenCalledWith({ taskId: 'T1', comment: '同意', processInstanceId: 'PI-9' })
    expect((getUni() as any).navigateBack).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1500)
    expect((getUni() as any).navigateBack).toHaveBeenCalled()
    wrapper.unmount()
    vi.useRealTimers()
  })

  it('退回未填意见拦截；填意见后 rejectTask', async () => {
    vi.mocked(request).mockResolvedValue({ code: 200, data: {} })
    vi.mocked(rejectTask).mockResolvedValue({ code: 200 })
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast

    const wrapper = mount(ApprovalDetail)
    hooks.onLoadCb?.({ taskId: 'T2', processInstanceId: 'PI-2' })
    await flushPromises()

    await wrapper.vm.handleReject()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '退回需填写意见' }))
    expect(vi.mocked(rejectTask)).not.toHaveBeenCalled()

    wrapper.vm.comment = '金额有误'
    await wrapper.vm.handleReject()
    await flushPromises()
    expect(vi.mocked(rejectTask)).toHaveBeenCalledWith({ taskId: 'T2', comment: '金额有误', processInstanceId: 'PI-2' })
    wrapper.unmount()
  })
})

describe('material/inbound.vue 材料入库页', () => {
  it('项目列表经离线优先加载；三段校验拦截', async () => {
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [{ id: 1, projectName: 'P1' }] } })
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(InboundPage)
    await flushPromises()

    expect(wrapper.vm.projects.length).toBe(1)

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入材料名称' }))
    wrapper.vm.form.materialName = '钢筋'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入数量' }))
    expect(vi.mocked(saveMaterialInbound)).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('提交载荷按后端契约组装 details 数组（单头+明细），totalAmount 随明细计算', async () => {
    vi.useFakeTimers()
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [{ id: 5, projectName: 'P5' }] } })
    vi.mocked(saveMaterialInbound).mockResolvedValue({ code: 200 })
    const wrapper = mount(InboundPage)
    await flushPromises()

    wrapper.vm.selectProject({ id: 5, projectName: 'P5' })
    Object.assign(wrapper.vm.form, { materialName: '水泥', specification: 'P.O42.5', unit: '吨', quantity: '10', unitPrice: '320.5', inboundDate: '2026-08-16' })
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveMaterialInbound)).toHaveBeenCalledWith({
      projectId: 5,
      inboundDate: '2026-08-16',
      totalAmount: 3205,
      details: [{ materialName: '水泥', specification: 'P.O42.5', unit: '吨', quantity: 10, unitPrice: 320.5 }],
    })
    vi.advanceTimersByTime(1500)
    expect((getUni() as any).navigateBack).toHaveBeenCalled()
    wrapper.unmount()
    vi.useRealTimers()
  })
})

describe('material/outbound.vue 材料出库页', () => {
  it('三段校验 + 提交载荷 outboundType=PICK 且 details 数组，领用人映射 operatorName', async () => {
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [{ id: 7, projectName: 'P7' }] } })
    vi.mocked(saveMaterialOutbound).mockResolvedValue({ code: 200 })
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(OutboundPage)
    await flushPromises()

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))

    wrapper.vm.selectProject({ id: 7, projectName: 'P7' })
    Object.assign(wrapper.vm.form, { materialName: '木方', specification: '5x10', unit: '根', quantity: '3', receiver: '李四', outboundDate: '2026-08-16' })
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveMaterialOutbound)).toHaveBeenCalledWith({
      projectId: 7,
      outboundType: 'PICK',
      outboundDate: '2026-08-16',
      operatorName: '李四',
      details: [{ materialName: '木方', specification: '5x10', unit: '根', quantity: 3 }],
    })
    wrapper.unmount()
  })
})
