import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import Approval from './index.vue'
const api = vi.hoisted(() => ({ getTodoTasks: vi.fn(), getDoneTasks: vi.fn(), getApprovalDetail: vi.fn(), getBusinessDetail: vi.fn(), completeTask: vi.fn(), rejectToPrevious: vi.fn(), rejectToStart: vi.fn(), terminateProcess: vi.fn(), batchApprove: vi.fn(), payment: vi.fn(), project: vi.fn(), confirm: vi.fn() }))
vi.mock('@/api/workflow', () => api)
vi.mock('@/api/finance', () => ({ getPaymentApplyDetail: api.payment }))
vi.mock('@/api/project', () => ({ getProjectDetail: api.project }))
vi.mock('@/composables/useColumnSetting', () => ({ useColumnSetting: () => ({ visible: [true, true, true, true], setVisible: vi.fn(), reset: vi.fn() }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }, ElMessageBox: { confirm: api.confirm } }))
const stub = { template: '<div><slot/><slot name="footer"/></div>' }
function page() {
  return mount(Approval, { global: { stubs: Object.fromEntries(['ElCard', 'ElTabs', 'ElTabPane', 'ElButton', 'ColumnSettingPopover', 'ElSkeleton', 'ElTable', 'ElTableColumn', 'ElPagination', 'ElDescriptions', 'ElDescriptionsItem', 'ElDrawer', 'ElDialog', 'ElForm', 'ElFormItem', 'ElInput', 'ElRadioGroup', 'ElRadio'].map(name => [name, stub])) } })
}
const detail = { taskId: 't1', status: 'pending', businessType: 'PAYMENT_APPLY', businessId: '12', processInstanceId: 'p1' }
describe('approval component gates (stubbed, not real UI)', () => {
  beforeEach(() => { vi.resetAllMocks(); api.getTodoTasks.mockResolvedValue({ data: { records: [], total: 0 } }); api.confirm.mockResolvedValue(true) })
  it('uses a skeleton instead of an Element Plus loading mask over content', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/workflow/approval/index.vue'), 'utf8')
    expect(source).toContain('<el-skeleton :loading="loading"')
    expect(source).not.toMatch(/<el-table[^>]*v-loading=/)
  })
  it('blocks every action after failed payment load', async () => {
    api.getApprovalDetail.mockResolvedValue({ data: detail }); api.payment.mockRejectedValue(new Error('403'))
    const wrapper = page(); await flushPromises(); const vm = wrapper.vm as any
    await vm.openDetail({ taskId: 't1' }); vm.comment = '原因'
    for (const action of ['approve', 'reject', 'terminate']) await vm.submitAction(action)
    expect(api.completeTask).not.toHaveBeenCalled(); expect(api.rejectToPrevious).not.toHaveBeenCalled(); expect(api.terminateProcess).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('全部审批动作已阻断'); wrapper.unmount()
  })
  it('uses source payment, then posts task/comment without workflow variables', async () => {
    api.getApprovalDetail.mockResolvedValue({ data: { ...detail, businessData: { paymentAmount: 999 } } }); api.payment.mockResolvedValue({ data: { id: '12', status: 'SUBMITTED', workflowInstanceId: 'p1', paymentAmount: 123, projectId: '21', projectName: '项目甲' } })
    const wrapper = page(); await flushPromises(); const vm = wrapper.vm as any
    await vm.openDetail({ taskId: 't1' }); vm.comment = '同意'; await vm.submitAction('approve')
    expect(api.confirm.mock.calls[0][0]).toContain('123.00'); expect(api.completeTask).toHaveBeenCalledWith({ taskId: 't1', comment: '同意' }); wrapper.unmount()
  })
  it('blocks approval when required project detail fails', async () => {
    api.getApprovalDetail.mockResolvedValue({ data: detail })
    api.payment.mockResolvedValue({ data: { id: '12', status: 'SUBMITTED', workflowInstanceId: 'p1', paymentAmount: 123, projectId: '21' } })
    api.project.mockRejectedValue(new Error('403'))
    const wrapper = page(); await flushPromises(); const vm = wrapper.vm as any
    await vm.openDetail({ taskId: 't1' }); await vm.submitAction('approve')
    expect(api.completeTask).not.toHaveBeenCalled(); expect(vm.detailError).toBeTruthy(); wrapper.unmount()
  })
  it('blocks payment batch and failed list offers retry', async () => {
    api.getTodoTasks.mockRejectedValueOnce(new Error('500'))
    api.getApprovalDetail.mockResolvedValue({ data: detail })
    const wrapper = page(); await flushPromises(); const vm = wrapper.vm as any
    expect(wrapper.text()).toContain('重新加载列表')
    await vm.loadData(); expect(vm.listError).toBe('')
    vm.selectedRows = [{ taskId: 't1', businessType: 'PAYMENT_APPLY' }]
    await vm.handleBatchApprove(); await vm.submitBatch()
    expect(api.batchApprove).not.toHaveBeenCalled(); wrapper.unmount()
  })
  it('keyboard does not intercept buttons and uses table selection API', async () => {
    const wrapper = page(); await flushPromises(); const vm = wrapper.vm as any
    const preventDefault = vi.fn()
    vm.handleTableKeydown({ target: document.createElement('button'), key: ' ', preventDefault })
    expect(preventDefault).not.toHaveBeenCalled()
    vm.tableData = [{ taskId: 't1' }]; vm.currentRowIndex = 0
    const selection = vi.fn(); vm.tableRef = { toggleRowSelection: selection }
    vm.handleTableKeydown({ target: vm.tableRegion, key: ' ', preventDefault })
    expect(selection).toHaveBeenCalledWith(vm.tableData[0]); wrapper.unmount()
  })
  it('ignores stale detail response after another task opens', async () => {
    let resolve: any; api.getApprovalDetail.mockImplementationOnce(() => new Promise(r => { resolve = r })).mockResolvedValueOnce({ data: { taskId: 't2', status: 'done', businessType: 'OTHER', businessId: '13' } })
    const wrapper = page(); await flushPromises(); const vm = wrapper.vm as any
    const first = vm.openDetail({ taskId: 't1' }); await vm.openDetail({ taskId: 't2' }); resolve({ data: detail }); await first
    expect(vm.detail.taskId).toBe('t2'); expect(api.payment).not.toHaveBeenCalled(); wrapper.unmount()
  })

  it('shows structured business fields for non-payment types', async () => {
    api.getApprovalDetail.mockResolvedValue({ data: { taskId: 't1', status: 'pending', businessType: 'CONSTRUCTION_CONTRACT', businessId: '12', processInstanceId: 'p1' } })
    api.getBusinessDetail.mockResolvedValue({ data: { supported: true, found: true, fields: [{ label: '合同编号', kind: 'T', value: 'HT-001' }, { label: '合同金额', kind: 'M', value: '1200000' }] } })
    const wrapper = page(); await flushPromises(); const vm = wrapper.vm as any
    await vm.openDetail({ taskId: 't1' }); await flushPromises()
    expect(api.getBusinessDetail).toHaveBeenCalledWith('t1'); expect(api.payment).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('HT-001'); expect(wrapper.text()).toContain('1,200,000.00'); wrapper.unmount()
  })
  it('business detail failure is shown but does not block approval', async () => {
    api.getApprovalDetail.mockResolvedValue({ data: { taskId: 't1', status: 'pending', businessType: 'SEAL_APPLY', businessId: '12', processInstanceId: 'p1' } })
    api.getBusinessDetail.mockRejectedValue(new Error('500'))
    const wrapper = page(); await flushPromises(); const vm = wrapper.vm as any
    await vm.openDetail({ taskId: 't1' }); await flushPromises()
    expect(vm.bizError).toContain('业务详情加载失败'); expect(vm.blocked).toBe(''); wrapper.unmount()
  })
})
