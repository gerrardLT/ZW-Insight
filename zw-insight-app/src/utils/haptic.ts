/**
 * 触觉反馈工具（Batch 2-B2，2026-09-17）
 *
 * 场景：工地现场戴手套、强光、注意力分散——关键操作（提交/审批/入队）加一次轻触觉，
 * 用皮肤通道确认「动作已发生」，与 DESIGN 动效承重墙「动效职责是确认动作发生了」同源，
 * 只是把确认从视觉扩到触觉（视觉反馈在强光/手套下易被忽略）。
 *
 * 纪律（克制）：
 * - 仅用户主动触发的关键节点，单次短振；不在每次输入/滚动上振（会疲劳、干扰）。
 * - 失败静默降级：uni.vibrateShort 在 H5 无振动硬件、部分平台未实现、或用户关闭系统
 *   触觉时会 fail/抛错——一律 try/catch + fail 回调吞掉，绝不阻断业务主流程（真实降级非静默假成功）。
 * - 提供全局开关（setHapticEnabled），供「我的」页设置或 reduced-motion 偏好联动关闭。
 */

/** 振动强度档位（uni.vibrateShort type；不支持档位的平台自动回落默认短振） */
export type HapticLevel = 'light' | 'medium' | 'heavy'

let enabled = true

/** 全局启停触觉反馈（默认开启；设置项/无障碍偏好可关闭） */
export function setHapticEnabled(value: boolean): void {
  enabled = value
}

/** 当前触觉反馈是否启用 */
export function isHapticEnabled(): boolean {
  return enabled
}

/**
 * 触发一次轻触觉反馈。
 * @param level 强度档位，默认 'light'（确认类操作用轻档，避免打扰）
 * 安全：uni 或 vibrateShort 不存在（H5 无硬件 / 测试环境 / 老基础库）时静默 no-op，不抛错。
 */
export function hapticTap(level: HapticLevel = 'light'): void {
  if (!enabled) return
  try {
    const u = (globalThis as any).uni
    if (u && typeof u.vibrateShort === 'function') {
      u.vibrateShort({ type: level, fail: () => {}, complete: () => {} })
    }
  } catch {
    /* 平台不支持触觉：静默降级，不影响主流程 */
  }
}
