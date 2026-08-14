/**
 * ConfirmPasswordDialog 组件测试（2026-08-14 前端深度补测·组件试点）
 *
 * 安全关键组件：449 二次确认密码对话框。
 * 覆盖：挂载注册 opener、输入密码确认 → resolve 密码、取消 → resolve null、
 * 空密码校验拦截不 resolve、卸载注销 opener。
 *
 * 试点约定（后续组件测试遵循）：
 * - happy-dom 环境 + @vue/test-utils mount 真实 Element Plus 组件（不 stub 业务交互）
 * - 与全局协调器的集成经由真实 import 验证，不 mock 被测协作方
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import ConfirmPasswordDialog from '@/components/ConfirmPasswordDialog.vue'
import { requestSecondaryConfirm } from '@/utils/secondaryConfirm'

type Wrapper = ReturnType<typeof mount>
let wrapper: Wrapper | null = null

function mountDialog() {
  wrapper = mount(ConfirmPasswordDialog, {
    global: { plugins: [ElementPlus] },
    attachTo: document.body
  })
  return wrapper
}

/** 对话框 teleport 到 body，从全局 DOM 取密码输入框 */
function getPasswordInput(): HTMLInputElement | null {
  return document.querySelector('.el-dialog input[type="password"]')
}

/** 按文案取对话框 footer 按钮 */
function getDialogButton(text: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.el-dialog__footer button, .el-dialog button'))
    .find(b => b.textContent?.includes(text))
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('ConfirmPasswordDialog 组件', () => {
  it('挂载即注册 opener：requestSecondaryConfirm 唤起对话框并显示提示文案', async () => {
    mountDialog()
    await nextTick()

    const pending = requestSecondaryConfirm('删除操作需要安全验证')
    await nextTick()
    await flushPromises()

    const input = getPasswordInput()
    expect(input).not.toBeNull()
    expect(document.body.textContent).toContain('删除操作需要安全验证')

    // 收尾：取消避免悬挂 promise
    getDialogButton('取消')?.click()
    await expect(pending).resolves.toBeNull()
  })

  it('输入密码点确认：promise resolve 密码值', async () => {
    mountDialog()
    await nextTick()

    const pending = requestSecondaryConfirm('请确认')
    await nextTick()

    const input = getPasswordInput()!
    input.value = 'abc-123'
    input.dispatchEvent(new Event('input'))
    await nextTick()

    getDialogButton('确认')?.click()
    await flushPromises()

    await expect(pending).resolves.toBe('abc-123')
  })

  it('点取消：promise resolve null', async () => {
    mountDialog()
    await nextTick()

    const pending = requestSecondaryConfirm('请确认')
    await nextTick()

    getDialogButton('取消')?.click()
    await flushPromises()

    await expect(pending).resolves.toBeNull()
  })

  it('空密码点确认：校验拦截，promise 保持 pending（不 resolve）', async () => {
    mountDialog()
    await nextTick()

    let resolved = false
    const pending = requestSecondaryConfirm('请确认')
    pending.then(() => { resolved = true })
    await nextTick()

    getDialogButton('确认')?.click()
    await flushPromises()

    expect(resolved).toBe(false)

    // 收尾
    getDialogButton('取消')?.click()
    await pending
  })

  it('卸载即注销 opener：回到返回 null 的兜底行为', async () => {
    const w = mountDialog()
    await nextTick()
    w.unmount()
    wrapper = null

    await expect(requestSecondaryConfirm('请确认')).resolves.toBeNull()
  })
})
