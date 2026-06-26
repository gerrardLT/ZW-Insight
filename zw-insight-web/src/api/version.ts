import request from '@/utils/request'

/**
 * 系统版本记录实体
 * 对应后端 SysVersion（zw-system 模块）
 */
export interface SysVersion {
  id?: number
  /** 版本号（语义化: x.y.z） */
  versionNo: string
  /** 发布日期（yyyy-MM-dd） */
  releaseDate: string
  /** 更新日志（Markdown 格式） */
  changelog?: string
  /** 操作人ID */
  operatorId?: number
  /** 创建时间 */
  createdAt?: string
}

/** 创建版本请求体（对应后端 VersionCreateRequest） */
export interface VersionCreateRequest {
  /** 版本号（语义化: x.y.z） */
  versionNo: string
  /** 发布日期（yyyy-MM-dd） */
  releaseDate: string
  /** 更新日志（Markdown 格式） */
  changelog?: string
}

/**
 * 创建版本记录
 * POST /api/v1/system/version
 * 响应：R<SysVersion> → res.data
 */
export function createVersion(data: VersionCreateRequest) {
  return request.post('/v1/system/version', data)
}

/**
 * 查询版本列表（后端按发布日期降序返回）
 * GET /api/v1/system/version/list
 * 响应：R<List<SysVersion>> → res.data
 */
export function getVersionList() {
  return request.get('/v1/system/version/list')
}

/**
 * 查询当前最新版本
 * GET /api/v1/system/version/current
 * 响应：R<SysVersion> → res.data（无版本时为 null）
 */
export function getCurrentVersion() {
  return request.get('/v1/system/version/current')
}
