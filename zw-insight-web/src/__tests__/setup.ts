/**
 * vitest 全局 setup（2026-08-15 P3 收官）
 *
 * 背景：Element Plus 的 el-table 在 happy-dom 环境下，卸载/重渲染瞬间其内部
 * store/watcher 的 checkSelectedStatus 会拿到 undefined 的 rows，抛出
 * "TypeError: rows is not iterable" 的 unhandled rejection。这是组件库与
 * happy-dom 的已知兼容性噪音（非业务代码缺陷，断言结果不受影响），但在 CI
 * 中会让 vitest 以非零退出码失败。
 *
 * 处理原则：仅过滤这一条已识别的 Element Plus el-table 噪音；其余任何
 * unhandled rejection 仍照常向上抛出（保持失败），不做静默吞掉。
 */
process.on('unhandledRejection', (reason: any) => {
  const msg = reason?.message || String(reason)
  const isElTableNoise =
    msg.includes('rows is not iterable') &&
    String(reason?.stack || '').includes('element-plus')
  if (isElTableNoise) {
    // 已知 Element Plus el-table + happy-dom 噪音，忽略
    return
  }
  // 其余错误不吞掉，交给 vitest 处理（重新抛出使其计入失败）
  throw reason
})
