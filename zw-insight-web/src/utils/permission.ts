import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * v-permission 自定义指令
 *
 * 用法：
 *   v-permission="'system:user:add'"          —— 单个权限标识
 *   v-permission="['system:user:add', 'system:user:edit']"  —— 多个权限标识（满足其一即可）
 *
 * 指令逻辑：
 *   从用户 store 获取当前用户的权限标识列表，判断元素所需权限是否在列表中。
 *   若用户不具备所需权限，则将该 DOM 元素从父节点中移除。
 *
 * Validates: Requirements 4.2
 */
export const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const { value } = binding

    if (!value) {
      throw new Error('v-permission 指令需要传入权限标识，例如 v-permission="\'system:user:add\'"')
    }

    const userStore = useUserStore()
    const userPermissions = userStore.permissions

    // 超级管理员拥有全部权限
    if (userPermissions.includes('*:*:*')) {
      return
    }

    // 需要校验的权限列表
    const requiredPermissions: string[] = Array.isArray(value) ? value : [value]

    // 只要满足其中一个权限即可通过
    const hasPermission = requiredPermissions.some((perm) =>
      userPermissions.includes(perm)
    )

    if (!hasPermission) {
      // 用户无权限，移除 DOM 元素
      el.parentNode?.removeChild(el)
    }
  },
}
