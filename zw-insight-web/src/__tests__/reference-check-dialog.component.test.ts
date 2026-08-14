/**
 * ReferenceCheckDialog 组件测试（2026-08-14 前端深度补测·组件试点）
 *
 * 引用校验删除对话框：打开即试删，400+references 时展示引用列表并禁用删除。
 * 覆盖：删除成功闭环（提示+事件+关闭）、400 引用拦截渲染与按钮禁用、
 * 其他错误直接关闭。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() } }
})

import { ElMessage } from 'element-plus'
import ReferenceCheckDialog from '@/components/ReferenceCheckDialog.vue'

const mockSuccess = vi.mocked(ElMessage.success)

/** 后端 ReferenceExistsException 的 400 响应形态（与 GlobalExceptionHandler 一致） */
function referenceError(refs: Array<{ refType: string; refCode: string; refTime: string }>) {
  return { response: { status: 400, data: { data: { references: refs } } } }
}

function mountDialog(deleteAction: () => Promise<any>) {
  return mount(ReferenceCheckDialog, {
    props: { visible: false, title: '删除测试', deleteAction },
    global: { plugins: [ElementPlus] },
    attachTo: document.body
  })
}

afterEach(() => {
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('ReferenceCheckDialog 组件', () => {
  it('无引用：打开即删除成功 → 提示 + success 事件 + 关闭', async () => {
    const deleteAction = vi.fn().mockResolvedValue(undefined)
    const wrapper = mountDialog(deleteAction)

    await wrapper.setProps({ visible: true })
    await flushPromises()

    expect(deleteAction).toHaveBeenCalledTimes(1)
    expect(mockSuccess).toHaveBeenCalledWith('删除成功')
    expect(wrapper.emitted('success')).toBeTruthy()
    // 关闭回传
    const visibleEmits = wrapper.emitted('update:visible') as boolean[][]
    expect(visibleEmits.flat()).toContain(false)

    wrapper.unmount()
  })

  it('有引用：400+references → 渲染引用列表且确认删除按钮禁用', async () => {
    const refs = [
      { refType: '采购合同', refCode: 'CG-2026-001', refTime: '2026-08-01 10:00:00' },
      { refType: '付款申请', refCode: 'FK-2026-008', refTime: '2026-08-02 11:30:00' }
    ]
    const deleteAction = vi.fn().mockRejectedValue(referenceError(refs))
    const wrapper = mountDialog(deleteAction)

    await wrapper.setProps({ visible: true })
    await flushPromises()

    expect(wrapper.text()).toContain('无法删除')
    expect(wrapper.text()).toContain('CG-2026-001')
    expect(wrapper.text()).toContain('FK-2026-008')
    expect(wrapper.text()).toContain('采购合同')

    const dangerBtn = wrapper.findAll('button').find(b => b.text().includes('确认删除'))
    expect(dangerBtn).toBeDefined()
    expect(dangerBtn!.attributes('disabled')).toBeDefined()

    wrapper.unmount()
  })

  it('其他错误（非引用 400）：直接关闭对话框不提示成功', async () => {
    const deleteAction = vi.fn().mockRejectedValue(new Error('服务器异常'))
    const wrapper = mountDialog(deleteAction)

    await wrapper.setProps({ visible: true })
    await flushPromises()

    const visibleEmits = wrapper.emitted('update:visible') as boolean[][]
    expect(visibleEmits.flat()).toContain(false)
    expect(wrapper.emitted('success')).toBeFalsy()
    expect(mockSuccess).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('重新打开时重置状态（引用残留不延续到下一次）', async () => {
    const refs = [{ refType: '合同', refCode: 'X-1', refTime: 't' }]
    const deleteAction = vi.fn()
      .mockRejectedValueOnce(referenceError(refs))
      .mockResolvedValueOnce(undefined)
    const wrapper = mountDialog(deleteAction)

    // 第一次打开：有引用
    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(wrapper.text()).toContain('X-1')

    // 关闭后第二次打开：删除成功，引用列表不残留
    await wrapper.setProps({ visible: false })
    await wrapper.setProps({ visible: true })
    await flushPromises()

    expect(deleteAction).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).not.toContain('X-1')
    expect(wrapper.emitted('success')).toBeTruthy()

    wrapper.unmount()
  })
})
