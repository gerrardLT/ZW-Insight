/**
 * ExpenseContractRevisionDialog 组件测试（V2026_123 支出合同族修订）
 * 覆盖：按类别拉取修订列表 / 保存申请带 contractCategory / 快照展示。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const mockGetRevisions = vi.hoisted(() => vi.fn(async (): Promise<any> => ({ data: [] })))
const mockCreateRevision = vi.hoisted(() => vi.fn(async (): Promise<any> => ({ data: 1 })))

vi.mock('@/api/contractRevision', () => ({
  getContractRevisions: mockGetRevisions,
  createContractRevision: mockCreateRevision,
  submitContractRevision: vi.fn(async (): Promise<any> => ({ code: 200 })),
  cancelContractRevision: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

import ExpenseContractRevisionDialog from '@/components/ExpenseContractRevisionDialog.vue'

const baseProps = {
  visible: false,
  category: 'PURCHASE' as const,
  contractId: 88,
  baseVersion: 2,
  beforeAmount: 100,
}

function mountDialog(): any {
  return mount(ExpenseContractRevisionDialog, {
    props: { ...baseProps },
    global: { plugins: [ElementPlus], stubs: { teleport: true } },
  })
}

describe('components/ExpenseContractRevisionDialog.vue 支出合同修订', () => {
  beforeEach(() => {
    mockGetRevisions.mockClear()
    mockCreateRevision.mockClear()
  })

  it('打开时按合同类别拉取修订列表', async () => {
    const wrapper = mountDialog()
    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(mockGetRevisions).toHaveBeenCalledWith(88, 'PURCHASE')
    wrapper.unmount()
  })

  it('保存申请携带 contractCategory 与基准版本', async () => {
    const wrapper = mountDialog()
    await wrapper.setProps({ visible: true })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    st.reason = '工程量调整'
    st.afterAmount = 120
    await st.create()
    await flushPromises()
    expect(mockCreateRevision).toHaveBeenCalledWith({
      contractId: 88,
      contractCategory: 'PURCHASE',
      baseVersion: 2,
      afterAmount: 120,
      reason: '工程量调整',
    })
    wrapper.unmount()
  })

  it('快照按钮展示修订前后快照', async () => {
    const wrapper = mountDialog()
    await wrapper.setProps({ visible: true })
    await flushPromises()
    wrapper.vm.$.setupState.showSnapshot({ beforeSnapshot: 'before-json', afterSnapshot: 'after-json' })
    await flushPromises()
    expect(wrapper.text()).toContain('before-json')
    expect(wrapper.text()).toContain('after-json')
    wrapper.unmount()
  })
})
