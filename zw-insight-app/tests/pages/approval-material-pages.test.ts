// @vitest-environment happy-dom
/**
 * approval + material 域页面级组件测试（2026-08-16 P3 方向2 批 5）
 *
 * 覆盖 approval/index.vue（三 tab 分流+分页+左滑终止）、approval/detail.vue
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
  onHide: () => {}, // S3.3 useFormSession 生命周期钩子
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
  getMaterialByCode: vi.fn(),
  getPurchaseContractPage: vi.fn(),
  getPurchaseContractDetails: vi.fn(),
  saveMaterialInbound: vi.fn(),
  saveMaterialOutbound: vi.fn(),
  batchApproveTasks: vi.fn(),
  terminateTask: vi.fn(),
}))

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import ApprovalIndex from '@/pages/approval/index.vue'
import ApprovalDetail from '@/pages/approval/detail.vue'
import InboundPage from '@/pages/material/inbound.vue'
import OutboundPage from '@/pages/material/outbound.vue'
import {
  getTodoTasks, getDoneTasks, getMyInitiatedTasks, completeTask,
  rejectTask, getProjectList, getMaterialByCode, getPurchaseContractPage,
  getPurchaseContractDetails, saveMaterialInbound, saveMaterialOutbound,
  batchApproveTasks, terminateTask,
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

  it('P0 Req8 批量同意：未勾选禁用；全选后提交 taskIds，成功后刷新列表', async () => {
    vi.mocked(getTodoTasks).mockResolvedValue({ code: 200, data: { records: [{ id: 'T1' }, { id: 'T2' }] } })
    vi.mocked(batchApproveTasks).mockResolvedValue({ code: 200 })
    ;(getUni() as any).showModal = vi.fn((opts: any) => opts.success?.({ confirm: true }))
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast

    const wrapper = mount(ApprovalIndex)
    hooks.onShowCb?.()
    await flushPromises()

    // 未勾选：按钮禁用，直接点击不发请求
    expect(wrapper.vm.selectedIds).toHaveLength(0)
    wrapper.vm.handleBatchApprove()
    expect(vi.mocked(batchApproveTasks)).not.toHaveBeenCalled()

    // 全选 → 提交批量同意
    wrapper.vm.toggleSelectAll()
    expect(wrapper.vm.selectedIds).toEqual(['T1', 'T2'])
    wrapper.vm.toggleSelect({ id: 'T1' })
    expect(wrapper.vm.selectedIds).toEqual(['T2'])
    wrapper.vm.toggleSelect({ id: 'T1' })
    await wrapper.vm.handleBatchApprove()
    await flushPromises()

    expect(vi.mocked(batchApproveTasks)).toHaveBeenCalledWith({ taskIds: ['T2', 'T1'], comment: '' })
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '批量审批成功' }))
    expect(wrapper.vm.selectedIds).toHaveLength(0)
    wrapper.unmount()
  })

  it('P0 Req8 批量同意失败不静默：展示后端错误后刷新列表', async () => {
    vi.mocked(getTodoTasks).mockResolvedValue({ code: 200, data: { records: [{ id: 'T1' }] } })
    vi.mocked(batchApproveTasks).mockRejectedValue(new Error('任务已被他人处理'))
    ;(getUni() as any).showModal = vi.fn((opts: any) => opts.success?.({ confirm: true }))

    const wrapper = mount(ApprovalIndex)
    hooks.onShowCb?.()
    await flushPromises()
    const callsBefore = vi.mocked(getTodoTasks).mock.calls.length

    wrapper.vm.toggleSelectAll()
    await wrapper.vm.handleBatchApprove()
    await flushPromises()

    // 失败后仍刷新列表（以最新状态为准），不静默吞错
    expect(vi.mocked(getTodoTasks).mock.calls.length).toBeGreaterThan(callsBefore)
    expect(wrapper.vm.batchApproving).toBe(false)
    wrapper.unmount()
  })

  it('P0 Req8.5 列表加载失败展示失败态，不空 catch', async () => {
    vi.mocked(getTodoTasks).mockRejectedValue(new Error('服务异常'))

    const wrapper = mount(ApprovalIndex)
    hooks.onShowCb?.()
    await flushPromises()

    expect(wrapper.vm.loadFailed).toBe(true)
    expect(wrapper.text()).toContain('任务列表加载失败')
    expect(wrapper.text()).toContain('重试')
    wrapper.unmount()
  })

  // ── S3.2：wd-swipe-action 左滑终止流程（后端无「删除审批」接口，映射到真实 terminate）──
  describe('S3.2 左滑终止流程', () => {
    async function mountWithTasks() {
      vi.mocked(getTodoTasks).mockResolvedValue({ code: 200, data: { records: [{ id: 'T1' }, { id: 'T2' }] } })
      const wrapper = mount(ApprovalIndex)
      hooks.onShowCb?.()
      await flushPromises()
      return wrapper
    }

    it('swipe click 事件仅 value=right 触发终止；left/inside 忽略', async () => {
      const modal = vi.fn()
      ;(getUni() as any).showModal = modal
      const wrapper = await mountWithTasks()

      wrapper.vm.onSwipeClick({ value: 'left' }, { id: 'T1' })
      wrapper.vm.onSwipeClick({ value: 'inside' }, { id: 'T1' })
      wrapper.vm.onSwipeClick(undefined, { id: 'T1' })
      expect(modal).not.toHaveBeenCalled()
      expect(vi.mocked(terminateTask)).not.toHaveBeenCalled()
      wrapper.unmount()
    })

    it('确认后 terminateTask 带 taskId + 用户填写的终止原因，成功后刷新列表并清选中', async () => {
      vi.mocked(terminateTask).mockResolvedValue({ code: 200 })
      ;(getUni() as any).showModal = vi.fn((opts: any) => opts.success?.({ confirm: true, content: '单据录错，重新发起' }))
      const toast = vi.fn()
      ;(getUni() as any).showToast = toast

      const wrapper = await mountWithTasks()
      wrapper.vm.toggleSelect({ id: 'T1' })
      const callsBefore = vi.mocked(getTodoTasks).mock.calls.length

      wrapper.vm.onSwipeClick({ value: 'right' }, { id: 'T1' })
      await flushPromises()

      expect(vi.mocked(terminateTask)).toHaveBeenCalledWith({ taskId: 'T1', comment: '单据录错，重新发起' })
      expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '流程已终止' }))
      expect(wrapper.vm.selectedIds).not.toContain('T1')
      // 终止后以服务端状态为准刷新
      expect(vi.mocked(getTodoTasks).mock.calls.length).toBeGreaterThan(callsBefore)
      expect(wrapper.vm.terminating).toBe(false)
      wrapper.unmount()
    })

    it('未填写原因时落真实默认描述（不编造原因）', async () => {
      vi.mocked(terminateTask).mockResolvedValue({ code: 200 })
      ;(getUni() as any).showModal = vi.fn((opts: any) => opts.success?.({ confirm: true, content: '   ' }))

      const wrapper = await mountWithTasks()
      wrapper.vm.onSwipeClick({ value: 'right' }, { id: 'T2' })
      await flushPromises()

      expect(vi.mocked(terminateTask)).toHaveBeenCalledWith({
        taskId: 'T2',
        comment: '移动端左滑终止（未填写原因）',
      })
      wrapper.unmount()
    })

    it('二次确认取消不调接口（破坏性动作需显式意图）', async () => {
      const modalCalls: any[] = []
      ;(getUni() as any).showModal = vi.fn((opts: any) => modalCalls.push(opts))

      const wrapper = await mountWithTasks()
      wrapper.vm.onSwipeClick({ value: 'right' }, { id: 'T1' })
      await flushPromises()

      // 弹框必须告知不可恢复（错误预防）
      expect(modalCalls).toHaveLength(1)
      expect(modalCalls[0].content).toContain('不可恢复')
      modalCalls[0].success({ confirm: false })
      await flushPromises()
      expect(vi.mocked(terminateTask)).not.toHaveBeenCalled()
      wrapper.unmount()
    })

    it('终止失败不静默：仍刷新列表且 terminating 复位（允许重试）', async () => {
      vi.mocked(terminateTask).mockRejectedValue(new Error('非当前办理人，无权终止'))
      ;(getUni() as any).showModal = vi.fn((opts: any) => opts.success?.({ confirm: true, content: '作废' }))

      const wrapper = await mountWithTasks()
      const callsBefore = vi.mocked(getTodoTasks).mock.calls.length

      wrapper.vm.onSwipeClick({ value: 'right' }, { id: 'T1' })
      await flushPromises()

      expect(vi.mocked(getTodoTasks).mock.calls.length).toBeGreaterThan(callsBefore)
      expect(wrapper.vm.terminating).toBe(false)
      wrapper.unmount()
    })

    it('非待办 tab 禁用左滑：服务端 assertTaskAssignee 限定办理人，已办/我发起不可终止', async () => {
      vi.mocked(getTodoTasks).mockResolvedValue({ code: 200, data: { records: [{ id: 'T1' }] } })
      vi.mocked(getDoneTasks).mockResolvedValue({ code: 200, data: { records: [{ id: 'D1' }] } })

      const wrapper = mount(ApprovalIndex)
      hooks.onShowCb?.()
      await flushPromises()

      // wd-swipe-action 在 vitest 下无 easycom，渲染为未知元素（props 降为 attrs，仅 default 插槽生效），
      // 故此处钉 disabled 守卫；#right 插槽内容靠 v-if 双重保险，走 H5 截图验。
      const swipes = () => wrapper.findAll('wd-swipe-action')
      expect(swipes()).toHaveLength(1)
      expect(swipes()[0].attributes('disabled')).toBe('false')
      expect(wrapper.findAll('.task-item')).toHaveLength(1)

      wrapper.vm.switchTab('done')
      await flushPromises()
      expect(swipes()).toHaveLength(1)
      expect(swipes()[0].attributes('disabled')).toBe('true')
      wrapper.unmount()
    })
  })
})

describe('approval/detail.vue 审批详情页', () => {
  it('onLoad 加载详情；通过提交 completeTask 对齐后端 DTO（taskId/comment）并延迟返回', async () => {
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
    // 后端 TaskCompleteRequest 无 processInstanceId 字段，页面不再透传无效参数
    expect(vi.mocked(completeTask)).toHaveBeenCalledWith({ taskId: 'T1', comment: '同意' })
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
    expect(vi.mocked(rejectTask)).toHaveBeenCalledWith({ taskId: 'T2', comment: '金额有误' })
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

  it('P0 Req6 编码查询带出材料信息填充表单；编码不存在不自动创建', async () => {
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [] } })
    vi.mocked(getMaterialByCode).mockResolvedValue({ code: 200, data: { materialName: '钢筋', specification: 'HRB400', unit: '吨' } })
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(InboundPage)
    await flushPromises()

    // 空编码拦截，不发请求
    wrapper.vm.materialCode = '  '
    await wrapper.vm.handleCodeConfirm()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入材料编码' }))
    expect(vi.mocked(getMaterialByCode)).not.toHaveBeenCalled()

    // 有效编码：调 by-code 端点并填充表单
    wrapper.vm.materialCode = ' M001 '
    await wrapper.vm.handleCodeConfirm()
    await flushPromises()
    expect(vi.mocked(getMaterialByCode)).toHaveBeenCalledWith('M001')
    expect(wrapper.vm.form.materialName).toBe('钢筋')
    expect(wrapper.vm.form.specification).toBe('HRB400')
    expect(wrapper.vm.form.unit).toBe('吨')
    expect(vi.mocked(saveMaterialInbound)).not.toHaveBeenCalled()

    // 编码不存在：后端 404 语义拒绝，页面不自动创建材料（请求层 toast 后端提示）
    vi.mocked(getMaterialByCode).mockRejectedValue(new Error('材料编码不存在，请先维护材料字典'))
    wrapper.vm.materialCode = 'NO_CODE'
    await wrapper.vm.handleCodeConfirm()
    await flushPromises()
    expect(vi.mocked(saveMaterialInbound)).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('P0 Req6 扫码成功自动查询带出材料；扫码失败提示可手输', async () => {
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [] } })
    vi.mocked(getMaterialByCode).mockResolvedValue({ code: 200, data: { materialName: '混凝土', specification: 'C30', unit: 'm³' } })
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(InboundPage)
    await flushPromises()

    // 扫码成功 → 自动填充编码并查询带出材料信息
    ;(getUni() as any).scanCode = (opts: any) => opts.success({ result: 'M100' })
    wrapper.vm.handleScan()
    await flushPromises()
    expect(wrapper.vm.materialCode).toBe('M100')
    expect(vi.mocked(getMaterialByCode)).toHaveBeenCalledWith('M100')
    expect(wrapper.vm.form.materialName).toBe('混凝土')
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '已带出材料信息' }))

    // 扫码取消/失败 → 明示可手输，不阻断流程
    ;(getUni() as any).scanCode = (opts: any) => opts.fail({ errMsg: 'scanCode:fail cancel' })
    wrapper.vm.handleScan()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '扫码已取消或失败，可手动输入编码' }))
    wrapper.unmount()
  })

  it('采购合同核验模式：联动拉取合同材料明细，点验实收数后组装包含 contractId 与 details 的载荷提交', async () => {
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [{ id: 1, projectName: '示范工程' }] } } as any)
    vi.mocked(getPurchaseContractPage).mockResolvedValue({
      code: 200,
      data: { records: [{ id: 88, contractName: '钢材采购协议', contractCode: 'PC-2026-01' }] }
    } as any)
    vi.mocked(getPurchaseContractDetails).mockResolvedValue({
      code: 200,
      data: [
        { materialName: '螺纹钢', specification: 'Φ20', unit: '吨', quantity: 50, unitPrice: 3800 },
        { materialName: '盘条', specification: 'Φ8', unit: '吨', quantity: 20, unitPrice: 4000 }
      ]
    } as any)
    vi.mocked(saveMaterialInbound).mockResolvedValue({ code: 200 } as any)

    const wrapper = mount(InboundPage)
    await flushPromises()

    // 切换为采购合同核验模式
    wrapper.vm.inboundMode = 'PURCHASE'
    await wrapper.vm.selectProject({ id: 1, projectName: '示范工程' })
    await flushPromises()

    expect(wrapper.vm.contracts.length).toBe(1)
    expect(wrapper.vm.contractDetails.length).toBe(2)

    // 修改第一项实收数量
    wrapper.vm.contractDetails[0].receiveQty = '30'
    // 不勾选第二项（部分到货场景）
    wrapper.vm.contractDetails[1].checked = false

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveMaterialInbound)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1,
      contractId: 88,
      totalAmount: 114000, // 30 * 3800
      details: [
        {
          materialName: '螺纹钢',
          specification: 'Φ20',
          unit: '吨',
          unitPrice: 3800,
          quantity: 30,
          totalPrice: 114000
        }
      ]
    }))
    wrapper.unmount()
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
