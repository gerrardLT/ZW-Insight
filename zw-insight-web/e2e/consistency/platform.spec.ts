/**
 * platform 平台租户模块 —— 前端展示 vs 后端数据 一致性
 *  分页列表页（records/total）：
 *   - 租户管理     /platform/tenant       GET /v1/platform/tenant       (pageNum/pageSize)
 *   - 租户类型     /platform/tenant-type  GET /v1/platform/tenant-type  (page/size)
 *   - 存储配置     /platform/storage      GET /v1/file/storage          (page/size)
 *
 * ⚠ 分页参数不一致：tenant 用 pageNum/pageSize，tenant-type/storage 用 page/size（后端拦截器映射）。
 */
import { test } from '@playwright/test'
import {
  runListConsistency,
  writeModuleReport,
  type PageConsistencyResult,
  type ColumnSpec,
} from './consistency-helper'

const results: PageConsistencyResult[] = []

// —— 租户：用户类型 / 状态映射（与页面 userTypeNameMap / statusNameMap 一致） ——
const TENANT_USER_TYPE: Record<string, string> = { TRIAL: '试用', STANDARD: '标准', ENTERPRISE: '企业' }
const TENANT_STATUS: Record<number, string> = { 1: '正常', 2: '已停用', 3: '已过期' }
// —— 存储：类型映射（与页面 storageTypeMap 一致） ——
const STORAGE_TYPE: Record<string, string> = {
  LOCAL: '本地存储', MINIO: 'MinIO', ALIYUN: '阿里云 OSS', TENCENT: '腾讯云 COS', QINIU: '七牛云',
}

test.describe.serial('platform 一致性', () => {
  test('租户管理 /platform/tenant', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '租户名称', index: 0, field: 'tenantName' },
      { label: '联系人', index: 1, field: 'contactName' },
      { label: '联系电话', index: 2, field: 'contactPhone' },
      { label: '用户类型', index: 3, expect: (r) => TENANT_USER_TYPE[r.userType] || r.userType },
      { label: '有效期', index: 4, expect: (r) => `${r.startDate || '-'} ~ ${r.endDate || '-'}` },
      { label: '使用量', index: 5, expect: (r) => `${r.currentUsers ?? 0} / ${r.maxUsers ?? 0}` },
      { label: '状态', index: 6, expect: (r) => TENANT_STATUS[r.status] || '-' },
    ]
    await runListConsistency(page, {
      route: '/platform/tenant', title: '租户管理', api: 'GET /v1/platform/tenant',
      urlPattern: /\/v1\/platform\/tenant(\?|$)/, columns,
    }, results)
  })

  test('租户类型 /platform/tenant-type', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '类型名称', index: 0, field: 'typeName' },
      { label: '有效期(天)', index: 1, field: 'durationDays', type: 'numeric' },
      { label: '排序', index: 2, field: 'sortOrder', type: 'numeric' },
      { label: '状态', index: 3, expect: (r) => (r.status === 1 ? '启用' : '停用') },
    ]
    await runListConsistency(page, {
      route: '/platform/tenant-type', title: '租户类型', api: 'GET /v1/platform/tenant-type',
      urlPattern: /\/v1\/platform\/tenant-type(\?|$)/, columns,
    }, results)
  })

  test('存储配置 /platform/storage', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '存储类型', index: 0, expect: (r) => STORAGE_TYPE[r.storageType] || r.storageType },
      { label: '端点地址', index: 1, field: 'endpoint' },
      { label: '存储桶', index: 2, field: 'bucket' },
      { label: '基础路径', index: 3, field: 'basePath' },
      { label: '状态', index: 4, expect: (r) => (r.status === 1 ? '启用' : '停用') },
    ]
    await runListConsistency(page, {
      route: '/platform/storage', title: '存储配置', api: 'GET /v1/file/storage',
      urlPattern: /\/v1\/file\/storage(\?|$)/, columns,
    }, results)
  })

  test.afterAll(async () => {
    writeModuleReport('platform', results)
  })
})
