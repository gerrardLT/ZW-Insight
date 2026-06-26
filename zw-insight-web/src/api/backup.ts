import request from '@/utils/request'

/**
 * 数据库备份记录实体
 * 对应后端 SysBackupRecord（zw-system 模块）
 */
export interface SysBackupRecord {
  id: number
  /** 备份文件名 */
  fileName?: string
  /** 文件大小(bytes) */
  fileSize?: number
  /** 备份耗时(毫秒) */
  durationMs?: number
  /** MinIO 存储路径 */
  storagePath?: string
  /** 备份类型: MANUAL/SCHEDULED */
  backupType?: string
  /** 状态: SUCCESS/FAILED */
  status?: string
  /** 错误信息 */
  errorMessage?: string
  /** 操作人ID */
  operatorId?: number
  /** 创建时间 */
  createdAt?: string
}

/** 备份记录分页查询参数 */
export interface BackupQuery {
  pageNum?: number
  pageSize?: number
}

/**
 * 手动触发数据库备份（同步执行的长耗时操作）
 * POST /api/v1/system/backup/execute
 * 响应：R<SysBackupRecord>
 */
export function executeBackup() {
  // 备份为长耗时同步操作（mysqldump → GZIP → MinIO），放宽超时时间
  return request.post('/v1/system/backup/execute', null, { timeout: 600000 })
}

/**
 * 备份记录分页列表（按创建时间倒序）
 * GET /api/v1/system/backup/list
 * 响应：R<PageResult<SysBackupRecord>> → res.data.{records,total}
 */
export function getBackupPage(params: BackupQuery) {
  return request.get('/v1/system/backup/list', { params })
}

/**
 * 下载备份文件（二进制流）
 * GET /api/v1/system/backup/download/{id}
 */
export function downloadBackup(id: number) {
  return request.get(`/v1/system/backup/download/${id}`, { responseType: 'blob' })
}

/**
 * 删除备份记录及其 MinIO 存储文件
 * DELETE /api/v1/system/backup/{id}
 */
export function deleteBackup(id: number) {
  return request.delete(`/v1/system/backup/${id}`)
}

/**
 * 从指定备份恢复数据库（高风险操作，可能触发二次确认 449）
 * POST /api/v1/system/backup/restore/{id}
 */
export function restoreBackup(id: number) {
  return request.post(`/v1/system/backup/restore/${id}`, null, { timeout: 600000 })
}
