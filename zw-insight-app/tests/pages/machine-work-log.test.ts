// @vitest-environment happy-dom
/**
 * 机械台班现场上报组件测试 (machine-work-log)
 * 覆盖 pages/machine/work-log/index.vue 与 pages/machine/work-log/create.vue
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
  getMachineLedgerPage: vi.fn(),
  getMachineWorkLogPage: vi.fn(),
  saveMachineWorkLog: vi.fn(),
}))

import WorkLogIndex from '@/pages/machine/work-log/index.vue'
import WorkLogCreate from '@/pages/machine/work-log/create.vue'
import {
  getProjectList,
  getMachineLedgerPage,
  getMachineWorkLogPage,
  saveMachineWorkLog,
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
  vi.mocked(getMachineLedgerPage).mockResolvedValue({
    code: 200,
    data: { records: [{ id: 10, machineName: '小松PC200挖掘机', machineCode: 'M-001' }] },
  } as any)
  vi.mocked(getMachineWorkLogPage).mockResolvedValue({
    code: 200,
    data: {
      records: [
        {
          id: 101,
          machineId: 10,
          machineName: '小松PC200挖掘机',
          workDate: '2026-09-09',
          shiftCount: 1.5,
          workQuantity: 280,
          oilConsumption: 45,
          settlementStatus: 'UNSETTLED',
          remark: '基坑开挖',
        },
      ],
      total: 1,
    },
  } as any)
  ;(getUni() as any).navigateBack = vi.fn()
  ;(getUni() as any).navigateTo = vi.fn()
})

function today() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

describe('machine/work-log/create.vue 填报机械台班', () => {
  it('默认日期为今天，初始台班数默认为 1', async () => {
    const wrapper = mount(WorkLogCreate)
    await flushPromises()

    expect(wrapper.vm.form.workDate).toBe(today())
    expect(wrapper.vm.form.shiftCount).toBe('1')
    wrapper.unmount()
  })

  it('多阶段校验：未选项目、未选设备、台班数超限时 toast 拦截', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(WorkLogCreate)
    await flushPromises()

    // 1. 未选项目
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))

    // 2. 已选项目未选设备
    wrapper.vm.selectProject({ id: 1, projectName: '测试项目' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择机械设备' }))

    // 3. 设备已选，但台班数填 5 (超过 3)
    wrapper.vm.selectMachine({ id: 10, machineName: '小松挖掘机' })
    wrapper.vm.form.shiftCount = '5'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '台班数须在0到3之间' }))

    expect(vi.mocked(saveMachineWorkLog)).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('校验通过后正确提交真实字段载荷并调用 saveMachineWorkLog', async () => {
    vi.mocked(saveMachineWorkLog).mockResolvedValue({ code: 200 } as any)
    const wrapper = mount(WorkLogCreate)
    await flushPromises()

    wrapper.vm.selectProject({ id: 1, projectName: '测试示范项目' })
    wrapper.vm.selectMachine({ id: 10, machineName: '小松PC200挖掘机' })
    wrapper.vm.form.shiftCount = '1.5'
    wrapper.vm.form.workQuantity = '300'
    wrapper.vm.form.oilConsumption = '50'
    wrapper.vm.form.remark = '南区基础开挖'

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveMachineWorkLog)).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 1,
        machineId: 10,
        workDate: today(),
        shiftCount: 1.5,
        workQuantity: 300,
        oilConsumption: 50,
        remark: '南区基础开挖',
      })
    )
    wrapper.unmount()
  })
})

describe('machine/work-log/index.vue 机械台班记录列表', () => {
  it('挂载后加载历史台班记录并正确渲染卡片指标', async () => {
    const wrapper = mount(WorkLogIndex)
    await flushPromises()

    expect(wrapper.text()).toContain('小松PC200挖掘机')
    expect(wrapper.text()).toContain('未结算')
    expect(wrapper.text()).toContain('1.5')
    expect(wrapper.text()).toContain('280')
    expect(wrapper.text()).toContain('45')
    wrapper.unmount()
  })

  it('点击右下角悬浮按钮一键跳转填报页面', async () => {
    const wrapper = mount(WorkLogIndex)
    await flushPromises()

    const fab = wrapper.find('.fab-btn')
    expect(fab.exists()).toBe(true)
    await fab.trigger('click')

    expect((getUni() as any).navigateTo).toHaveBeenCalledWith({
      url: '/pages/machine/work-log/create',
    })
    wrapper.unmount()
  })

  it('当列表为空时展示暂无台班记录与填报引导', async () => {
    vi.mocked(getMachineWorkLogPage).mockResolvedValue({
      code: 200,
      data: { records: [], total: 0 },
    } as any)
    const wrapper = mount(WorkLogIndex)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无台班记录，点击右下角填报')
    wrapper.unmount()
  })
})
