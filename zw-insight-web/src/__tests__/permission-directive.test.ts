/**
 * v-permission 指令单元测试（2026-08-14 前端深度补测）
 *
 * 覆盖：有权限保留元素、无权限移除 DOM、超管绕过、
 * 数组权限任一命中、未传权限标识抛错。
 * 采用 withDirectives 程序化挂载（vitest runtime-only 构建不支持 template 字符串）。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, withDirectives } from 'vue'
import { permissionDirective } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

let pinia: ReturnType<typeof createPinia>

/** 挂载一个受 v-permission 保护的单按钮宿主组件 */
function mountWithPermission(value: string | string[] | undefined) {
  const host = defineComponent({
    render() {
      return h('div', [
        withDirectives(h('button', '操作'), [[permissionDirective, value]])
      ])
    }
  })
  return mount(host, {
    global: { plugins: [pinia] }
  })
}

describe('v-permission 指令', () => {
  beforeEach(() => {
    localStorage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
  })

  it('用户持有所需权限：元素保留', () => {
    useUserStore().setPermissions(['system:user:add'])
    const wrapper = mountWithPermission('system:user:add')
    expect(wrapper.find('button').exists()).toBe(true)
  })

  it('用户无权限：元素从 DOM 移除', () => {
    useUserStore().setPermissions(['别的:权限'])
    const wrapper = mountWithPermission('system:user:add')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('超级管理员（*:*:*）：任意权限标识均保留', () => {
    useUserStore().setPermissions(['*:*:*'])
    const wrapper = mountWithPermission('system:user:delete')
    expect(wrapper.find('button').exists()).toBe(true)
  })

  it('数组权限：任一命中即保留', () => {
    useUserStore().setPermissions(['system:user:edit'])
    const wrapper = mountWithPermission(['system:user:add', 'system:user:edit'])
    expect(wrapper.find('button').exists()).toBe(true)
  })

  it('数组权限：全部缺失则移除', () => {
    useUserStore().setPermissions(['system:user:view'])
    const wrapper = mountWithPermission(['system:user:add', 'system:user:edit'])
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('未传权限标识：抛出明确错误（防误用）', () => {
    useUserStore().setPermissions([])
    expect(() => mountWithPermission(undefined)).toThrow(/v-permission/)
  })
})
