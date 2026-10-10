/**
 * 弹窗「取消/关闭」不是错误（2026-10-10 修复「点取消弹页面渲染异常」）
 *
 * 背景：ElMessageBox.confirm 在用户点取消时 reject 字符串 'cancel'（element-plus
 * messageBox.mjs: currentMsg.reject("cancel")）。未 catch 的调用点会被 Vue 的异步事件
 * 处理器交给 app.config.errorHandler，全局兜底于是弹「页面渲染异常，请刷新重试」
 * ——回款登记页删除/核销确认框实证。
 */
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { isDialogDismiss } from '@/utils/dialog'

const norm = (s: string) => s.replace(/\r\n/g, '\n')
const src = (rel: string) => norm(readFileSync(resolve(__dirname, '..', rel), 'utf-8'))

describe('isDialogDismiss 弹窗取消识别', () => {
  it('识别 ElMessageBox 取消/关闭的字符串 rejection', () => {
    expect(isDialogDismiss('cancel')).toBe(true)
    expect(isDialogDismiss('close')).toBe(true)
  })

  it('识别 Error 包装的取消（message 为 cancel/close）', () => {
    expect(isDialogDismiss(new Error('cancel'))).toBe(true)
    expect(isDialogDismiss(new Error('close'))).toBe(true)
  })

  it('真实错误与空值不被误判为取消', () => {
    expect(isDialogDismiss(new Error('网络异常'))).toBe(false)
    expect(isDialogDismiss('系统异常')).toBe(false)
    expect(isDialogDismiss({ message: 'x' })).toBe(false)
    expect(isDialogDismiss(undefined)).toBe(false)
    expect(isDialogDismiss(null)).toBe(false)
  })
})

describe('全局错误边界接入取消识别（main.ts）', () => {
  const main = src('main.ts')

  it('errorHandler 与 unhandledrejection 两处均先过滤弹窗取消', () => {
    expect(main).toContain("import { isDialogDismiss } from './utils/dialog'")
    expect((main.match(/isDialogDismiss\(/g) || []).length).toBe(2)
  })
})

describe('回款登记页：登记即已认领，且确认框取消不误报', () => {
  const page = src('views/finance/payment-received.vue')

  it('不再提供「认领」入口（按钮、处理函数、接口调用均已移除）', () => {
    expect(page).not.toContain('handleClaim')
    expect(page).not.toContain('claimPaymentReceived')
  })

  it('删除/核销确认框就地吞掉取消（try/catch + return）', () => {
    expect((page.match(/try\s*\{\s*await ElMessageBox\.confirm/g) || []).length).toBe(2)
  })
})
