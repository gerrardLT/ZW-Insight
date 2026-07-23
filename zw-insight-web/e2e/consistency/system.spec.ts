/**
 * system 系统管理模块 —— 前端展示 vs 后端数据 一致性
 *  分页列表页（records/total）：
 *   - 用户管理    /system/user           GET /v1/system/user           (pageNum/pageSize)
 *   - 岗位管理    /system/post           GET /v1/system/post           (pageNum/pageSize)
 *   - 操作日志    /system/log            GET /v1/system/log/oper       (pageNum/pageSize)
 *   - 数据备份    /system/backup         GET /v1/system/backup/list    (pageNum/pageSize)
 *   - 打印模板    /system/print-template GET /v1/print-template/list  (pageNum/pageSize)
 *  非分页「直出数组」列表页（data 为数组，无 records/total）：
 *   - 编号规则    /system/serial-number  GET /v1/file/serial
 *   - 通用模板    /system/template       GET /v1/file/template
 *   - 版本管理    /system/version        GET /v1/system/version/list
 *
 * ⚠ 备注（print-template）：模板名称为「前端客户端过滤」（后端列表未提供名称模糊查询），
 *   若输入名称关键字，分页 total 将与实际显示行数不符。本用例不输入关键字，故不受影响。
 */
import { test } from '@playwright/test'
import {
  runListConsistency,
  runFlatListConsistency,
  writeModuleReport,
  type PageConsistencyResult,
  type ColumnSpec,
} from './consistency-helper'

const results: PageConsistencyResult[] = []

// —— 打印模板：业务类型标签映射（与页面 businessTypeOptions 一致） ——
const PRINT_BIZ: Record<string, string> = {
  CONTRACT: '合同', BUDGET: '预算', MATERIAL: '材料', FINANCE: '财务', LABOR: '劳务', MACHINE: '机械',
}
// —— 通用模板：所属模块 / 模板类型映射（与页面 moduleOptions / typeTagMap 一致） ——
const TPL_MODULE: Record<string, string> = {
  machine_ledger: '机械台账', labor_roster: '劳务花名册', sys_user: '人员信息', supplier: '供应商',
  material: '材料字典', material_stock: '库存', labor_payroll: '工资单', finance_invoice: '开票申请', project: '项目',
}
const TPL_TYPE: Record<string, string> = { IMPORT: '导入', EXPORT: '导出', PRINT: '打印' }

test.describe.serial('system 一致性', () => {
  test('用户管理 /system/user', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '账号', index: 1, field: 'username' },
      { label: '姓名', index: 2, field: 'realName' },
      { label: '手机号', index: 3, field: 'phone' },
      { label: '所属机构', index: 4, expect: (r) => r.orgName || '-' },
      { label: '状态', index: 5, expect: (r) => (r.status === 1 ? '启用' : '停用') },
      { label: '创建时间', index: 6, field: 'createdAt', type: 'datetime' },
    ]
    await runListConsistency(page, {
      route: '/system/user', title: '用户管理', api: 'GET /v1/system/user',
      urlPattern: /\/v1\/system\/user(\?|$)/, columns,
    }, results)
  })

  test('岗位管理 /system/post', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '岗位名称', index: 0, field: 'postName' },
      { label: '岗位编码', index: 1, field: 'postCode' },
      { label: '状态', index: 2, expect: (r) => (r.status === 1 ? '启用' : '停用') },
      { label: '排序', index: 3, field: 'sort', type: 'numeric' },
      { label: '创建时间', index: 4, field: 'createdAt', type: 'datetime' },
    ]
    await runListConsistency(page, {
      route: '/system/post', title: '岗位管理', api: 'GET /v1/system/post',
      urlPattern: /\/v1\/system\/post(\?|$)/, columns,
    }, results)
  })

  test('操作日志 /system/log', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '操作模块', index: 0, field: 'module' },
      { label: '操作类型', index: 1, field: 'operType' },
      { label: '操作人', index: 2, field: 'operator' },
      { label: '操作时间', index: 3, field: 'operTime', type: 'datetime' },
      { label: 'IP', index: 4, field: 'ip' },
      { label: '操作描述', index: 5, field: 'description' },
      { label: '状态', index: 6, expect: (r) => (r.status === 1 ? '成功' : '失败') },
    ]
    await runListConsistency(page, {
      route: '/system/log', title: '操作日志', api: 'GET /v1/system/log/oper',
      urlPattern: /\/v1\/system\/log\/oper/, columns,
    }, results)
  })

  test('数据备份 /system/backup', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '备份文件名', index: 0, expect: (r) => r.fileName || '-' },
      { label: '类型', index: 3, expect: (r) => (r.backupType === 'SCHEDULED' ? '定时' : '手动') },
      { label: '状态', index: 4, expect: (r) => (r.status === 'SUCCESS' ? '成功' : '失败') },
      { label: '备份时间', index: 5, field: 'createdAt', type: 'datetime' },
    ]
    await runListConsistency(page, {
      route: '/system/backup', title: '数据备份', api: 'GET /v1/system/backup/list',
      urlPattern: /\/v1\/system\/backup\/list/, columns,
    }, results)
  })

  test('打印模板 /system/print-template', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '模板名称', index: 0, field: 'templateName' },
      { label: '业务类型', index: 1, expect: (r) => (!r.businessType ? '-' : PRINT_BIZ[r.businessType] || r.businessType) },
      { label: '渲染引擎', index: 2, expect: (r) => r.engineType || 'SIMPLE' },
      { label: '所属模块', index: 3, field: 'moduleCode' },
      { label: '创建时间', index: 4, field: 'createdAt', type: 'datetime' },
    ]
    await runListConsistency(page, {
      route: '/system/print-template', title: '打印模板', api: 'GET /v1/print-template/list',
      urlPattern: /\/v1\/print-template\/list/, columns,
    }, results)
  })

  test('编号规则 /system/serial-number (直出数组)', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '业务类型', index: 0, field: 'businessType' },
      { label: '规则前缀', index: 1, field: 'rulePrefix' },
      { label: '日期格式', index: 2, field: 'dateFormat' },
      { label: '序号长度', index: 3, field: 'seqLength', type: 'numeric' },
      { label: '重置周期', index: 4, expect: (r) => (r.resetPeriod === 'MONTH' ? '按月' : '按年') },
      { label: '描述', index: 5, field: 'description' },
    ]
    await runFlatListConsistency(page, {
      route: '/system/serial-number', title: '编号规则', api: 'GET /v1/file/serial',
      urlPattern: /\/v1\/file\/serial(\?|$)/, columns,
    }, results)
  })

  test('通用模板 /system/template (直出数组)', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '模板名称', index: 0, field: 'templateName' },
      { label: '所属模块', index: 1, expect: (r) => TPL_MODULE[r.moduleCode] || r.moduleCode },
      { label: '模板类型', index: 2, expect: (r) => TPL_TYPE[r.templateType] || r.templateType },
      { label: '默认', index: 3, expect: (r) => (r.isDefault === 1 ? '是' : '-') },
      { label: '创建时间', index: 4, field: 'createdAt', type: 'datetime' },
    ]
    await runFlatListConsistency(page, {
      route: '/system/template', title: '通用模板', api: 'GET /v1/file/template',
      urlPattern: /\/v1\/file\/template(\?|$)/, columns,
    }, results)
  })

  test('版本管理 /system/version (直出数组)', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '版本号', index: 0, expect: (r) => `v${r.versionNo}` },
      { label: '发布日期', index: 1, field: 'releaseDate', type: 'date' },
    ]
    await runFlatListConsistency(page, {
      route: '/system/version', title: '版本管理', api: 'GET /v1/system/version/list',
      urlPattern: /\/v1\/system\/version\/list/, columns,
    }, results)
  })

  test.afterAll(async () => {
    writeModuleReport('system', results)
  })
})
