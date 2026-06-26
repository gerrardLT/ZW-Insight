/**
 * 批量操作用户状态 — 纯函数逻辑
 * 排除当前登录用户，防止管理员将自己停用
 */

/**
 * 从用户 ID 列表中排除当前登录用户
 * @param selectedIds 选中的用户 ID 数组
 * @param currentUserId 当前登录用户 ID
 * @returns 过滤后的 ID 数组（不包含当前用户）
 */
export function filterBatchIds(selectedIds: number[], currentUserId: number): number[] {
  return selectedIds.filter(id => id !== currentUserId)
}
