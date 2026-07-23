/**
 * hr 人力资源 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 入职申请 /hr/entry          GET /v1/hr/entry-apply/page
 *  B 离职申请 /hr/resign-apply    GET /v1/hr/resign-apply
 *  C 车辆管理 /hr/vehicle         GET /v1/hr/vehicle/page
 *  D 办公用品 /hr/office-supply    GET /v1/hr/office-supply/page
 *
 * 说明：entry/vehicle/office-supply 的 status 为内联三元翻译，用 expect 自定义；
 * resign 的 isHandover 为 1/0→是/否；status 用 statusMap（RESIGN_STATUS 基线）。
 */
import { test } from '@playwright/test'
import {
  runListConsistency,
  writeModuleReport,
  type ColumnSpec,
  type PageConsistencyResult,
} from './consistency-helper'
import { RESIGN_STATUS } from './enum-baseline'

const ENTRY_COLUMNS: ColumnSpec[] = [
  { label: '姓名', index: 0, field: 'realName', type: 'text' },
  { label: '登录账号', index: 1, field: 'username', type: 'text' },
  { label: '手机号', index: 2, field: 'phone', type: 'text' },
  { label: '入职日期', index: 3, field: 'entryDate', type: 'date' },
  { label: '状态', index: 4, field: 'status', expect: (r) => (r.status === 'APPROVED' ? '已通过' : '草稿') },
]

const RESIGN_COLUMNS: ColumnSpec[] = [
  { label: '姓名', index: 0, field: 'userName', type: 'text' },
  { label: '离职日期', index: 1, field: 'resignDate', type: 'date' },
  { label: '交接人', index: 2, field: 'handoverPerson', type: 'text' },
  { label: '是否交接', index: 3, field: 'isHandover', expect: (r) => (r.isHandover === 1 ? '是' : '否') },
  { label: '状态', index: 4, field: 'status', type: 'enum', enumMap: RESIGN_STATUS },
]

const VEHICLE_COLUMNS: ColumnSpec[] = [
  { label: '车牌号', index: 0, field: 'plateNo', type: 'text' },
  { label: '车辆类型', index: 1, field: 'vehicleType', type: 'text' },
  { label: '品牌型号', index: 2, field: 'brand', type: 'text' },
  { label: '驾驶人', index: 3, field: 'driver', type: 'text' },
  { label: '使用部门', index: 4, field: 'department', type: 'text' },
  { label: '保险到期', index: 5, field: 'insuranceExpiry', type: 'date' },
  { label: '年检到期', index: 6, field: 'inspectionExpiry', type: 'date' },
  { label: '状态', index: 7, field: 'status', expect: (r) => (r.status === 'NORMAL' ? '正常' : '维修') },
]

const OFFICE_SUPPLY_COLUMNS: ColumnSpec[] = [
  { label: '申请单号', index: 0, field: 'applyNo', type: 'text' },
  { label: '物品名称', index: 1, field: 'itemName', type: 'text' },
  { label: '规格', index: 2, field: 'specification', type: 'text' },
  { label: '数量', index: 3, field: 'quantity', type: 'numeric' },
  { label: '申请人', index: 4, field: 'applicant', type: 'text' },
  { label: '申请日期', index: 5, field: 'applyDate', type: 'date' },
  { label: '状态', index: 6, field: 'status', expect: (r) => (r.status === 'APPROVED' ? '已领用' : r.status === 'PENDING' ? '审批中' : '草稿') },
]

const results: PageConsistencyResult[] = []

test.describe.serial('hr 一致性', () => {
  test('A 入职申请 /hr/entry 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/hr/entry', title: '入职申请列表', api: 'GET /v1/hr/entry-apply/page', urlPattern: /\/v1\/hr\/entry-apply\/page/, columns: ENTRY_COLUMNS }, results)
  })

  test('B 离职申请 /hr/resign-apply 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/hr/resign-apply', title: '离职申请列表', api: 'GET /v1/hr/resign-apply', urlPattern: /\/v1\/hr\/resign-apply(\?|$)/, columns: RESIGN_COLUMNS }, results)
  })

  test('C 车辆管理 /hr/vehicle 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/hr/vehicle', title: '车辆管理列表', api: 'GET /v1/hr/vehicle/page', urlPattern: /\/v1\/hr\/vehicle\/page/, columns: VEHICLE_COLUMNS }, results)
  })

  test('D 办公用品 /hr/office-supply 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/hr/office-supply', title: '办公用品列表', api: 'GET /v1/hr/office-supply/page', urlPattern: /\/v1\/hr\/office-supply\/page/, columns: OFFICE_SUPPLY_COLUMNS }, results)
  })

  test.afterAll(async () => {
    writeModuleReport('hr', results)
  })
})
