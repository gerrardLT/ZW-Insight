/**
 * 通用 API 响应与分页类型定义
 */

/** 后端统一响应结构 */
export interface R<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

/** 分页查询参数基类
 * 运行时约定：组件传 pageNum/pageSize，request.ts 拦截器统一映射为 page/size。
 * 两组字段均声明为可选，以兼容组件与直接调用两种写法。 */
export interface PageQuery {
  page?: number
  size?: number
  pageNum?: number
  pageSize?: number
}

/** ID 类型 */
export type ID = number
