/**
 * user 用户中心模块 —— 前端展示 vs 后端数据 一致性
 *  非分页「直出数组」列表页（data 为数组）：
 *   - 登录设备     /user/devices  GET /v1/user/devices/list
 *
 * ⚠ 「设备名称」列含条件「当前设备」tag，不做逐字比对。
 * ⚠ 「登录地点」列由后端 "省份|城市" 经 formatLocation 转为 "省份 城市"。
 */
import { test } from '@playwright/test'
import {
  runFlatListConsistency,
  writeModuleReport,
  type PageConsistencyResult,
  type ColumnSpec,
} from './consistency-helper'

const results: PageConsistencyResult[] = []

/** 与页面 formatLocation 一致：省份|城市 → 省份 城市 */
function formatLocation(location?: string): string {
  if (!location) return '-'
  return location.split('|').filter(Boolean).join(' ') || '-'
}

test.describe.serial('user 一致性', () => {
  test('登录设备 /user/devices (直出数组)', async ({ page }) => {
    const columns: ColumnSpec[] = [
      // index 0 为设备名称列（含条件「当前设备」tag），不做逐字比对
      { label: '操作系统', index: 1, expect: (r) => r.os || '-' },
      { label: 'IP 地址', index: 2, expect: (r) => r.ipAddress || '-' },
      { label: '登录地点', index: 3, expect: (r) => formatLocation(r.location) },
      { label: '登录时间', index: 4, expect: (r) => r.loginTime || '-' },
      { label: '状态', index: 5, expect: (r) => (r.status === 1 ? '活跃' : '已注销') },
    ]
    await runFlatListConsistency(page, {
      route: '/user/devices', title: '登录设备', api: 'GET /v1/user/devices/list',
      urlPattern: /\/v1\/user\/devices\/list/, columns,
    }, results)
  })

  test.afterAll(async () => {
    writeModuleReport('user', results)
  })
})
