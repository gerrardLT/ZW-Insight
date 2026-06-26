import request from '@/utils/request'

// 可选快捷功能定义（msg_available_shortcut）
export interface AvailableShortcut {
  id: number
  name: string
  icon: string
  routePath: string
  sortOrder: number
  status: string
}

// 用户已选快捷入口配置（msg_user_shortcut）
export interface UserShortcutConfig {
  id?: number
  userId?: number
  shortcutId: number
  menuName: string
  menuPath: string
  menuIcon: string
  sortOrder: number
}

// 批量保存请求体
export interface ShortcutBatchSaveRequest {
  shortcutIds: number[]
}

// 批量保存响应体
export interface ShortcutBatchSaveResponse {
  savedIds: number[]
  invalidIds: number[]
}

// 获取全部可选功能列表
export function getAvailableShortcuts() {
  return request({ url: '/v1/message/shortcut/available' })
}

// 获取用户已选配置（无配置时返回默认项）
export function getUserShortcuts() {
  return request({ url: '/v1/message/shortcut' })
}

// 批量保存用户配置（整体替换）
export function batchSaveShortcuts(shortcutIds: number[]) {
  return request({ url: '/v1/message/shortcut/batch', method: 'POST', data: { shortcutIds } })
}
