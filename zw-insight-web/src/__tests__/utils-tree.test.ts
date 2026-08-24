/**
 * utils/tree.ts listToTree 单元测试
 *
 * 背景：GET /v1/system/menu 等接口返回平铺列表（后端 SysMenuService.list 注释约定
 * 「用于前端构建树」），listToTree 为菜单管理/角色管理/清单树共用的建树工具。
 */
import { describe, it, expect } from 'vitest'
import { listToTree } from '@/utils/tree'

describe('utils/tree listToTree', () => {
  it('空数组返回空树', () => {
    expect(listToTree([])).toEqual([])
  })

  it('单层平铺：parentId=0 的节点全部为根', () => {
    const tree = listToTree([
      { id: 1, parentId: 0, name: 'A' },
      { id: 2, parentId: 0, name: 'B' },
    ])
    expect(tree).toHaveLength(2)
    expect(tree[0].children).toEqual([])
    expect(tree[1].children).toEqual([])
  })

  it('多层嵌套：子节点按 parentId 挂到父节点 children', () => {
    const tree = listToTree([
      { id: 1, parentId: 0, menuName: '系统管理' },
      { id: 2, parentId: 1, menuName: '用户管理' },
      { id: 3, parentId: 1, menuName: '角色管理' },
      { id: 4, parentId: 2, menuName: '用户新增' },
    ])
    expect(tree).toHaveLength(1)
    expect(tree[0].menuName).toBe('系统管理')
    expect(tree[0].children.map((c: any) => c.id)).toEqual([2, 3])
    expect(tree[0].children[0].children[0].menuName).toBe('用户新增')
  })

  it('孤儿节点（父不存在）回落根节点，不丢数据', () => {
    const tree = listToTree([
      { id: 1, parentId: 0, name: '根' },
      { id: 5, parentId: 999, name: '孤儿' },
    ])
    expect(tree).toHaveLength(2)
    expect(tree[1].name).toBe('孤儿')
  })

  it('首项已含 children（后端已返回树形）直接透传原数组', () => {
    const input = [{ id: 1, children: [{ id: 2 }] }]
    expect(listToTree(input)).toBe(input)
  })

  it('建树浅拷贝，不污染原始响应数组', () => {
    const raw = [
      { id: 1, parentId: 0, name: '根' },
      { id: 2, parentId: 1, name: '子' },
    ]
    const tree = listToTree(raw)
    expect(tree[0].children).toHaveLength(1)
    // 原始对象不应被附加 children 字段
    expect(raw[0]).not.toHaveProperty('children')
    expect(raw[1]).not.toHaveProperty('children')
    // 修改树节点不影响原始数据
    tree[0].children[0].name = '被改'
    expect(raw[1].name).toBe('子')
  })
})
