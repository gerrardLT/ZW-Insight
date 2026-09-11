// @vitest-environment happy-dom
/**
 * 劳务点工现场签认组件测试 (labor-work-order)
 * 覆盖 pages/labor/work-order/index.vue 与 pages/labor/work-order/create.vue
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@dcloudio/uni-app', () => ({
  onShow: (cb: any) => { cb && cb() },
  onHide: vi.fn(),
  onLoad: vi.fn(),
}))

vi.mock('@/api/common', () => ({
  getProjectList: vi.fn(),
  getLaborTeamPage: vi.fn(),
  getWorkOrderPage: vi.fn(),
  saveWorkOrder: vi.fn(),
}))

import WorkOrderIndex from '@/pages/labor/work-order/index.vue'
import WorkOrderCreate from '@/pages/labor/work-order/create.vue'
import {
  getProjectList,
  getLaborTeamPage,
  getWorkOrderPage,
  saveWorkOrder,
} from '@/api/common'
import { resetUniStorage, getUni } from '../setup'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(getProjectList).mockResolvedValue({
    code: 200,
    data: { records: [{ id: 1, projectName: '测试示范项目' }] },
  } as any)
  vi.mocked(getLaborTeamPage).mockResolvedValue({
    code: 200,
    data: { records: [{ id: 5, teamName: '钢筋一班', workType: '钢筋工' }] },
  } as any)
  vi.mocked(getWorkOrderPage).mockResolvedValue({
    code: 200,
    data: {
      records: [
        {
          id: 201,
          projectId: 1,
          workerName: '王五',
          orderType: 'TEMPORARY',
          hours: 8,
          overtime: 2,
          hourlyRate: 35,
          totalAmount: 350,
          workDate: '2026-09-09',
          status: 'APPROVED',
        },
      ],
      total: 1,
    },
  } as any)
  ;(getUni() as any).navigateBack = vi.fn()
  ;(getUni() as any).navigateTo = vi.fn()
})

describe('labor/work-order/create.vue 点工工时签认填报', () => {
  it('表单初始工时与单价联动计算合计金额 (8 * 35 = 280)', async () => {
    const wrapper = mount(WorkOrderCreate)
    await flushPromises()

    expect(wrapper.vm.form.hours).toBe('8')
    expect(wrapper.vm.form.hourlyRate).toBe('35')
    expect(wrapper.vm.calcTotal).toBe('280.00')
    wrapper.unmount()
  })

  it('校验拦截：未选项目、工人姓名为空、工时异常 toast 提示', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(WorkOrderCreate)
    await flushPromises()

    // 1. 未选项目
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))

    // 2. 未填工人姓名
    wrapper.vm.selectProject({ id: 1, projectName: '测试示范项目' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入工人姓名' }))

    // 3. 工时异常（> 24 小时）
    wrapper.vm.form.workerName = '李师傅'
    wrapper.vm.form.hours = '28'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '工时须在0到24小时之间' }))

    expect(vi.mocked(saveWorkOrder)).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('校验通过后按后端契约提交载荷并调用 saveWorkOrder', async () => {
    vi.mocked(saveWorkOrder).mockResolvedValue({ code: 200 } as any)
    const wrapper = mount(WorkOrderCreate)
    await flushPromises()

    wrapper.vm.selectProject({ id: 1, projectName: '测试示范项目' })
    wrapper.vm.selectTeam({ id: 5, teamName: '钢筋一班' })
    wrapper.vm.form.workerName = '张小强'
    wrapper.vm.form.hours = '9'
    wrapper.vm.form.hourlyRate = '40'
    wrapper.vm.form.overtime = '2'
    wrapper.vm.form.overtimeRate = '50'

    // 合计金额 = 9 * 40 + 2 * 50 = 460
    expect(wrapper.vm.calcTotal).toBe('460.00')

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveWorkOrder)).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 1,
        teamId: 5,
        workerName: '张小强',
        hours: 9,
        hourlyRate: 40,
        overtime: 2,
        overtimeRate: 50,
        totalAmount: 460,
        orderType: 'TEMPORARY',
        status: 'APPROVED',
      })
    )
    wrapper.unmount()
  })
})

describe('labor/work-order/index.vue 劳务点工记录列表', () => {
  it('挂载后加载历史派工记录并正确渲染卡片指标', async () => {
    const wrapper = mount(WorkOrderIndex)
    await flushPromises()

    expect(wrapper.text()).toContain('王五')
    expect(wrapper.text()).toContain('临时点工')
    expect(wrapper.text()).toContain('8 小时')
    expect(wrapper.text()).toContain('加班 2h')
    expect(wrapper.text()).toContain('350')
    wrapper.unmount()
  })

  it('点击右下角悬浮按钮一键跳转点工签认填报页', async () => {
    const wrapper = mount(WorkOrderIndex)
    await flushPromises()

    const fab = wrapper.find('.fab-btn')
    expect(fab.exists()).toBe(true)
    await fab.trigger('click')

    expect((getUni() as any).navigateTo).toHaveBeenCalledWith({
      url: '/pages/labor/work-order/create',
    })
    wrapper.unmount()
  })
})
