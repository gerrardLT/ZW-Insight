/**
 * tender 招投标 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 证件管理 /tender/certificate  GET /v1/tender/certificate/person (默认 type=person)
 *  B 投标报名 /tender/register      GET /v1/tender/register/page
 *
 * 说明：certificate 已按后端真实契约重写（台账缺口#1 解除）：personName/certificateType/
 * certificateNo/issueDate/expireDate，状态三态由前端基于 expireDate 派生
 * （>30天=有效/≤30天=即将过期/<今天=已过期，与 certificate.vue deriveStatus 同源）；
 * register.depositAmount 用 toLocaleString() 无参（仅千分位），用 numeric。
 */
import { test } from '@playwright/test'
import {
  runListConsistency,
  finalizeModuleConsistency,
  type ColumnSpec,
  type PageConsistencyResult,
} from './consistency-helper'
import { TENDER_REGISTER_STATUS } from './enum-baseline'

const CERT_COLUMNS: ColumnSpec[] = [
  { label: '持证人', index: 0, field: 'personName', type: 'text' },
  { label: '证件类型', index: 1, field: 'certificateType', type: 'text' },
  { label: '证件编号', index: 2, field: 'certificateNo', type: 'text' },
  { label: '发证日期', index: 3, field: 'issueDate', type: 'date' },
  { label: '到期日期', index: 4, field: 'expireDate', type: 'date' },
  { label: '状态', index: 5, field: 'status', expect: (r) => {
    // 与 certificate.vue deriveStatus 同源：无到期日视为长期有效
    if (!r.expireDate) return '有效'
    const expire = new Date(r.expireDate)
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    if (expire < today) return '已过期'
    return (expire.getTime() - today.getTime()) / 86400000 <= 30 ? '即将过期' : '有效'
  } },
]

const REGISTER_COLUMNS: ColumnSpec[] = [
  { label: '业主单位', index: 0, field: 'ownerCompany', type: 'text' },
  { label: '招标方式', index: 1, field: 'bidMethod', type: 'text' },
  { label: '报名日期', index: 2, field: 'registerDate', type: 'date' },
  { label: '开标日期', index: 3, field: 'openDate', type: 'date' },
  { label: '保证金(元)', index: 4, field: 'depositAmount', type: 'numeric' },
  { label: '状态', index: 5, field: 'status', type: 'enum', enumMap: TENDER_REGISTER_STATUS },
]

const results: PageConsistencyResult[] = []

test.describe.serial('tender 一致性', () => {
  test('A 证件管理 /tender/certificate 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/tender/certificate', title: '证件管理列表', api: 'GET /v1/tender/certificate/person', urlPattern: /\/v1\/tender\/certificate\/person/, columns: CERT_COLUMNS }, results)
  })

  test('B 投标报名 /tender/register 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/tender/register', title: '投标报名列表', api: 'GET /v1/tender/register/page', urlPattern: /\/v1\/tender\/register\/page/, columns: REGISTER_COLUMNS }, results)
  })

  test.afterAll(async () => {
    finalizeModuleConsistency('tender', results)
  })
})
