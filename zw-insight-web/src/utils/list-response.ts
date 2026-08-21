/**
 * 列表响应归一化：后端列表接口存在两种契约形态——
 * 1. 分页对象 PageResult{records, total}（如 GET /system/role、/system/dict、/system/post）
 * 2. 纯数组（如 GET /system/menu、/system/org）
 * 前端消费时统一经此函数归一为数组，避免把分页对象当数组渲染导致静默空列表。
 */
export function extractRecords(res: any): any[] {
  const data = res?.data
  if (Array.isArray(data)) return data
  if (data && Array.isArray(data.records)) return data.records
  return []
}
