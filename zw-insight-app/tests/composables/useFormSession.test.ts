// @vitest-environment happy-dom
/**
 * useFormSession 表单会话持久化测试 (S3.3)
 * mock @dcloudio/uni-app 生命周期钩子，用 setup.ts 的 uni storage 桩端到端验证
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { resetUniStorage } from '../setup'

vi.mock('@dcloudio/uni-app', () => ({
  onHide: vi.fn(),
  onShow: vi.fn(),
}))

import { useFormSession } from '@/composables/useFormSession'

interface TestForm {
  projectName: string
  amount: number
}

function createHarness(initial: TestForm, emptyCheckOverride?: (d: TestForm) => boolean) {
  const form = { ...initial }
  const restored: TestForm[] = []
  const isEmpty = emptyCheckOverride || ((d: TestForm) => !d.projectName && !d.amount)
  const session = useFormSession<TestForm>('test-form', {
    getSnapshot: () => ({ ...form }),
    restoreTo: (data) => { restored.push(data); Object.assign(form, data) },
    isEmpty,
  })
  return { form, restored, session }
}

describe('useFormSession', () => {
  beforeEach(() => {
    resetUniStorage()
    vi.clearAllMocks()
  })

  it('save：非空表单写入快照（含时间戳）', () => {
    const h = createHarness({ projectName: '滨江花园', amount: 100 })
    h.session.save()
    const raw = uni.getStorageSync('form-session:test-form') as string
    expect(raw).toBeTruthy()
    const stored = JSON.parse(raw)
    expect(stored.data.projectName).toBe('滨江花园')
    expect(stored.savedAt).toBeGreaterThan(0)
  })

  it('save：空表单不存快照且清除旧快照', () => {
    const h = createHarness({ projectName: '有数据', amount: 1 })
    h.session.save()
    expect(uni.getStorageSync('form-session:test-form')).toBeTruthy()
    // 清空表单再 save
    h.form.projectName = ''
    h.form.amount = 0
    h.session.save()
    expect(uni.getStorageSync('form-session:test-form')).toBe('')
  })

  it('restore：空表单时还原快照数据', () => {
    const h1 = createHarness({ projectName: '保存的项目', amount: 500 })
    h1.session.save()

    // 模拟新会话：空表单 + 同 key
    const h2 = createHarness({ projectName: '', amount: 0 })
    h2.session.restore()
    expect(h2.form.projectName).toBe('保存的项目')
    expect(h2.form.amount).toBe(500)
  })

  it('restore：表单非空时不覆盖（防覆盖用户已输入）', () => {
    const h1 = createHarness({ projectName: '旧数据', amount: 500 })
    h1.session.save()

    const h2 = createHarness({ projectName: '用户正在输入', amount: 1 })
    h2.session.restore()
    expect(h2.form.projectName).toBe('用户正在输入') // 未被覆盖
  })

  it('restore：过期快照（>24h）丢弃', () => {
    // 手动写入过期快照
    uni.setStorageSync('form-session:test-form', JSON.stringify({
      savedAt: Date.now() - 25 * 60 * 60 * 1000,
      data: { projectName: '过期数据', amount: 9 },
    }))
    const h = createHarness({ projectName: '', amount: 0 })
    h.session.restore()
    expect(h.form.projectName).toBe('') // 未还原
    expect(uni.getStorageSync('form-session:test-form')).toBe('') // 已清除
  })

  it('clear：提交成功后清除快照', () => {
    const h = createHarness({ projectName: '待提交', amount: 1 })
    h.session.save()
    expect(uni.getStorageSync('form-session:test-form')).toBeTruthy()
    h.session.clear()
    expect(uni.getStorageSync('form-session:test-form')).toBe('')
  })

  it('注册 onHide/onShow 生命周期钩子', async () => {
    const { onHide, onShow } = await import('@dcloudio/uni-app')
    createHarness({ projectName: '', amount: 0 })
    expect(onHide).toHaveBeenCalledTimes(1)
    expect(onShow).toHaveBeenCalledTimes(1)
  })

  it('损坏快照静默丢弃不抛错', () => {
    uni.setStorageSync('form-session:test-form', '{broken json')
    const h = createHarness({ projectName: '', amount: 0 })
    expect(() => h.session.restore()).not.toThrow()
    expect(h.form.projectName).toBe('')
  })
})
