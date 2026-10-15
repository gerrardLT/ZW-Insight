import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import ContractRevisionDialog from './ContractRevisionDialog.vue'
vi.mock('@/api/contract', () => ({ getContractDetail: vi.fn().mockResolvedValue({ data: { version: 3, contractAmount: 100 } }) }))
vi.mock('@/api/contractRevision', () => ({ getContractRevisions: vi.fn().mockResolvedValue({ data: [] }), createContractRevision: vi.fn(), submitContractRevision: vi.fn() }))
describe('ContractRevisionDialog', () => {
  it('loads the actual contract baseline and revision history', async () => {
    const wrapper = mount(ContractRevisionDialog, { global: { stubs: { ElDialog: { template: '<div><slot /></div>' }, ElForm: { template: '<div><slot /></div>' }, ElFormItem: { template: '<div><slot /></div>' }, ElTable: true, ElTableColumn: true, ElAlert: true, ElInput: true, ElInputNumber: true, ElButton: true } } })
    await (wrapper.vm as unknown as { open: (id: string) => Promise<void> }).open('1')
    await flushPromises()
    expect(wrapper.text()).toContain('基准版本 3')
    expect(wrapper.text()).toContain('当前合同金额 100')
  })
})
