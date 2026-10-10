/**
 * 弹窗（ElMessageBox）「取消/关闭」结果识别。
 *
 * Element Plus 的 ElMessageBox.confirm/prompt 在用户点「取消」时 reject **字符串 'cancel'**
 * （开启 distinguishCancelAndClose 时点关闭 reject 'close'），这属于正常交互而非错误。
 * 若调用方未 catch，Vue 会把异步事件处理器的 rejection 交给 app.config.errorHandler，
 * 于是用户只是点了「取消」，页面却弹「页面渲染异常，请刷新重试」（2026-10-10 回款登记页实证）。
 *
 * 全局错误边界用它把这类结果过滤掉，避免每个调用点都要写 try/catch。
 */
export function isDialogDismiss(reason: unknown): boolean {
  if (reason === 'cancel' || reason === 'close') return true
  const message = (reason as { message?: unknown } | null | undefined)?.message
  return message === 'cancel' || message === 'close'
}
