/**
 * 平铺列表转树形结构共享工具
 *
 * 适用场景：后端返回按 parentId 组织的平铺列表（如 GET /v1/system/menu），
 * 前端需构建 children 嵌套结构供 el-table 树模式 / el-tree / el-tree-select 使用。
 *
 * 行为约定（与 contract/boq-upload.vue 原 buildTree 对齐，见 @matrix A9-09）：
 * 1. 若首项已含 children 字段（后端已返回树形），直接透传原数组
 * 2. 节点浅拷贝，不污染原始响应数据
 * 3. parentId 为 0/null/undefined 或父节点不存在（孤儿）时回落根节点，不丢数据
 */
export function listToTree(list: any[]): any[] {
  // 如果后端已返回树形结构（带 children），直接使用
  if (list.length > 0 && list[0].children !== undefined) {
    return list
  }
  // 否则根据 parentId 前端构建树
  const map = new Map<any, any>()
  const roots: any[] = []

  list.forEach(item => {
    map.set(item.id, { ...item, children: [] })
  })

  list.forEach(item => {
    const node = map.get(item.id)!
    if (!item.parentId || item.parentId === 0) {
      roots.push(node)
    } else {
      const parent = map.get(item.parentId)
      if (parent) {
        parent.children.push(node)
      } else {
        roots.push(node)
      }
    }
  })

  return roots
}
