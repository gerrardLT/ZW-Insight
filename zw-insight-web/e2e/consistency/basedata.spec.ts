/**
 * basedata 基础数据 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 自持公司 /basedata/company            GET /v1/basedata/company/page
 *  B 材料字典 /basedata/material            GET /v1/basedata/material/page
 *  C 甲方单位 /basedata/owner               GET /v1/basedata/owner/page
 *  D 供应商   /basedata/supplier             GET /v1/basedata/supplier/page
 *  E 供应商黑名单 /basedata/supplier-blacklist  GET /v1/basedata/supplier-blacklist
 *  F 供应商评价 /basedata/supplier-evaluation   GET /v1/basedata/supplier-evaluation
 *  G 检查方案 /basedata/inspection-scheme    GET /v1/basedata/inspection-scheme/page
 *
 * 说明：material.referencePrice / evaluation.score / office 库存等无 formatter，直出数值，用 numeric；
 * supplier.supplierType 直出原值（MATERIAL/MACHINE/LABOR，无翻译）；inspection-scheme 类型/状态为内联三元。
 */
import { test } from '@playwright/test'
import {
  runListConsistency,
  finalizeModuleConsistency,
  type ColumnSpec,
  type PageConsistencyResult,
} from './consistency-helper'

const COMPANY_COLUMNS: ColumnSpec[] = [
  { label: '公司名称', index: 0, field: 'companyName', type: 'text' },
  { label: '简称', index: 1, field: 'shortName', type: 'text' },
  { label: '法人', index: 2, field: 'legalPerson', type: 'text' },
  // 统一社会信用代码：页面列 prop=taxNumber（views/basedata/company.vue:22），实体 BdCompany.taxNumber
  { label: '统一社会信用代码', index: 3, field: 'taxNumber', type: 'text' },
  { label: '联系电话', index: 4, field: 'contactPhone', type: 'text' },
  { label: '注册地址', index: 5, field: 'address', type: 'text' },
]

const MATERIAL_COLUMNS: ColumnSpec[] = [
  { label: '材料编码', index: 0, field: 'materialCode', type: 'text' },
  { label: '材料名称', index: 1, field: 'materialName', type: 'text' },
  { label: '分类', index: 2, field: 'categoryName', type: 'text' },
  { label: '规格型号', index: 3, field: 'specification', type: 'text' },
  { label: '计量单位', index: 4, field: 'unit', type: 'text' },
  { label: '参考单价(元)', index: 5, field: 'referencePrice', type: 'numeric' },
]

const OWNER_COLUMNS: ColumnSpec[] = [
  { label: '甲方名称', index: 0, field: 'ownerName', type: 'text' },
  { label: '联系人', index: 1, field: 'contactName', type: 'text' },
  { label: '联系电话', index: 2, field: 'contactPhone', type: 'text' },
  // 统一社会信用代码：页面列 prop=taxpayerNo（views/basedata/owner.vue:22），实体 BdOwner.taxpayerNo
  { label: '统一社会信用代码', index: 3, field: 'taxpayerNo', type: 'text' },
  { label: '地址', index: 4, field: 'address', type: 'text' },
]

const SUPPLIER_COLUMNS: ColumnSpec[] = [
  { label: '供应商名称', index: 0, field: 'supplierName', type: 'text' },
  { label: '类型', index: 1, field: 'supplierType', type: 'text' },
  { label: '联系人', index: 2, field: 'contactName', type: 'text' },
  { label: '联系电话', index: 3, field: 'contactPhone', type: 'text' },
  { label: '地址', index: 4, field: 'address', type: 'text' },
  // 统一社会信用代码：页面列 prop=taxNumber（views/basedata/supplier.vue:31），实体 BdSupplier.taxNumber
  { label: '统一社会信用代码', index: 5, field: 'taxNumber', type: 'text' },
]

const BLACKLIST_COLUMNS: ColumnSpec[] = [
  { label: '供应商名称', index: 0, field: 'supplierName', type: 'text' },
  { label: '加入原因', index: 1, field: 'reason', type: 'text' },
  { label: '加入时间', index: 2, field: 'createTime', type: 'datetime' },
]

// 列序对齐 views/basedata/supplier-evaluation.vue:23-31：供应商/综合评分/质量/交期/价格/服务/配合/评价日期/备注
const EVALUATION_COLUMNS: ColumnSpec[] = [
  { label: '供应商名称', index: 0, field: 'supplierName', type: 'text' },
  { label: '综合评分', index: 1, field: 'totalScore', type: 'numeric' },
  { label: '质量', index: 2, field: 'qualityScore', type: 'numeric' },
  { label: '交期', index: 3, field: 'timelinessScore', type: 'numeric' },
  { label: '价格', index: 4, field: 'priceScore', type: 'numeric' },
  { label: '服务', index: 5, field: 'serviceScore', type: 'numeric' },
  { label: '配合', index: 6, field: 'cooperationScore', type: 'numeric' },
  { label: '评价日期', index: 7, field: 'evaluationDate', type: 'date' },
  { label: '备注', index: 8, field: 'remark', type: 'text' },
]

const SCHEME_COLUMNS: ColumnSpec[] = [
  { label: '方案名称', index: 0, field: 'schemeName', type: 'text' },
  { label: '类型', index: 1, field: 'schemeType', expect: (r) => (r.schemeType === 'QUALITY' ? '质量检查' : '安全检查') },
  { label: '状态', index: 2, field: 'status', expect: (r) => (r.status === 'ENABLED' ? '启用' : '停用') },
  { label: '创建时间', index: 3, field: 'createTime', type: 'datetime' },
]

const results: PageConsistencyResult[] = []

test.describe.serial('basedata 一致性', () => {
  test('A 自持公司 /basedata/company 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/basedata/company', title: '自持公司列表', api: 'GET /v1/basedata/company/page', urlPattern: /\/v1\/basedata\/company\/page/, columns: COMPANY_COLUMNS }, results)
  })

  test('B 材料字典 /basedata/material 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/basedata/material', title: '材料字典列表', api: 'GET /v1/basedata/material/page', urlPattern: /\/v1\/basedata\/material\/page/, columns: MATERIAL_COLUMNS }, results)
  })

  test('C 甲方单位 /basedata/owner 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/basedata/owner', title: '甲方单位列表', api: 'GET /v1/basedata/owner/page', urlPattern: /\/v1\/basedata\/owner\/page/, columns: OWNER_COLUMNS }, results)
  })

  test('D 供应商 /basedata/supplier 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/basedata/supplier', title: '供应商列表', api: 'GET /v1/basedata/supplier/page', urlPattern: /\/v1\/basedata\/supplier\/page/, columns: SUPPLIER_COLUMNS }, results)
  })

  test('E 供应商黑名单 /basedata/supplier-blacklist 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/basedata/supplier-blacklist', title: '供应商黑名单列表', api: 'GET /v1/basedata/supplier-blacklist', urlPattern: /\/v1\/basedata\/supplier-blacklist(\?|$)/, columns: BLACKLIST_COLUMNS }, results)
  })

  test('F 供应商评价 /basedata/supplier-evaluation 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/basedata/supplier-evaluation', title: '供应商评价列表', api: 'GET /v1/basedata/supplier-evaluation', urlPattern: /\/v1\/basedata\/supplier-evaluation(\?|$)/, columns: EVALUATION_COLUMNS }, results)
  })

  test('G 检查方案 /basedata/inspection-scheme 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/basedata/inspection-scheme', title: '检查方案列表', api: 'GET /v1/basedata/inspection-scheme/page', urlPattern: /\/v1\/basedata\/inspection-scheme\/page/, columns: SCHEME_COLUMNS }, results)
  })

  test.afterAll(async () => {
    finalizeModuleConsistency('basedata', results)
  })
})
