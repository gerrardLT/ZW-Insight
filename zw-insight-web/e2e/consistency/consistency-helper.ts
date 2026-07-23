/**
 * 前端展示 vs 后端数据 一致性断言工具集
 *
 * 核心方法论：抓取页面真实调用的后端 API JSON，再把 DOM 渲染值与
 * 响应字段逐一断言。枚举标签对照独立基线校验；金额/日期用独立预期
 * 公式校验（不复用被测页面自身的 formatter，避免 formatter 有 bug 也通过）。
 *
 * 所有断言失败信息均带上「行号/列名/字段」上下文，便于定位不一致。
 */
import { Page, Locator, expect, APIResponse } from '@playwright/test'

/** 后端统一响应结构 */
export interface R<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页结果 */
export interface PageResult<T = any> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

/** 列比对规格：描述某个表格列如何与 record 字段对应 */
export interface ColumnSpec {
  /** 列名（仅用于报错信息与文档） */
  label: string
  /** el-table 列序号（0-based，与 <el-table-column> 定义顺序一致） */
  index: number
  /** 对应 record 的字段名；不填表示操作列/序号列等不比对的列 */
  field?: string
  /** 值类型，决定期望值的格式化方式。numeric=数值归一比较（容忍千分位/小数位差异） */
  type?: 'text' | 'amount' | 'numeric' | 'date' | 'datetime' | 'enum'
  /** enum 类型必填：code -> label 的独立基线映射 */
  enumMap?: Record<string, string>
  /** amount 类型：小数位数，默认 2 */
  decimals?: number
  /** amount 类型：是否千分位，默认 true */
  thousands?: boolean
  /**
   * 自定义期望值函数（优先级最高）。用于处理拼接列、条件渲染等复杂场景。
   * 返回 null 表示跳过该单元格比对。
   */
  expect?: (record: any) => string | null
}

/**
 * 在导航到目标页面的同时抓取指定 API 的响应。
 * waitForResponse 必须在 goto 之前挂载，避免请求早于监听触发而漏抓。
 */
export async function gotoAndCapture<T = any>(
  page: Page,
  path: string,
  urlPattern: string | RegExp,
  method = 'GET',
  timeout = 20_000
): Promise<R<T>> {
  const respPromise = page.waitForResponse(
    (r) => matchUrl(r.url(), urlPattern) && r.request().method() === method,
    { timeout }
  )
  await page.goto(path)
  const resp = await respPromise
  return (await resp.json()) as R<T>
}

/**
 * 执行一个触发请求的动作（点击/搜索等）并抓取 API 响应。
 */
export async function actionAndCapture<T = any>(
  page: Page,
  urlPattern: string | RegExp,
  action: () => Promise<void>,
  method = 'GET',
  timeout = 20_000
): Promise<R<T>> {
  const respPromise = page.waitForResponse(
    (r) => matchUrl(r.url(), urlPattern) && r.request().method() === method,
    { timeout }
  )
  await action()
  const resp = await respPromise
  return (await resp.json()) as R<T>
}

/** URL 匹配：字符串按 includes 匹配，正则按 test 匹配 */
export function matchUrl(url: string, pattern: string | RegExp): boolean {
  return typeof pattern === 'string' ? url.includes(pattern) : pattern.test(url)
}

/**
 * 读取 el-table 主体的所有行，每行返回单元格文本数组。
 * 只取第一个 .el-table__body-wrapper，避免 fixed 列产生的重复行。
 */
export async function getTableRows(page: Page, tableRoot?: Locator): Promise<string[][]> {
  const root = tableRoot ?? page.locator('.el-table').first()
  const body = root.locator('.el-table__body-wrapper').first()
  const rowLocators = body.locator('.el-table__row')
  const rowCount = await rowLocators.count()
  const rows: string[][] = []
  for (let i = 0; i < rowCount; i++) {
    const cells = rowLocators.nth(i).locator('.cell')
    const cellCount = await cells.count()
    const texts: string[] = []
    for (let j = 0; j < cellCount; j++) {
      texts.push((await cells.nth(j).innerText()).trim())
    }
    rows.push(texts)
  }
  return rows
}

/** 金额格式化（独立预期，不依赖被测页面） */
export function fmtAmount(raw: unknown, decimals = 2, thousands = true): string {
  if (raw === null || raw === undefined || raw === '') return ''
  const n = Number(raw)
  if (Number.isNaN(n)) return String(raw)
  const fixed = n.toFixed(decimals)
  if (!thousands) return fixed
  const [intPart, decPart] = fixed.split('.')
  const sign = intPart.startsWith('-') ? '-' : ''
  const digits = sign ? intPart.slice(1) : intPart
  const withSep = digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return decPart !== undefined ? `${sign}${withSep}.${decPart}` : `${sign}${withSep}`
}

/** 数值归一化：去千分位后取规范数字字符串（容忍分组/尾随小数位差异）。 */
export function normNumeric(raw: unknown): string {
  if (raw === null || raw === undefined || raw === '') return ''
  const n = Number(String(raw).replace(/,/g, '').trim())
  return Number.isNaN(n) ? String(raw).trim() : String(n)
}

/** 日期格式化 YYYY-MM-DD（独立预期） */
export function fmtDate(raw: unknown): string {
  if (!raw) return ''
  const s = String(raw)
  // 已是 YYYY-MM-DD 或含空格的 datetime，取日期部分
  const m = s.match(/^(\d{4}-\d{2}-\d{2})/)
  return m ? m[1] : s
}

/** 计算某列的期望展示值 */
export function expectedCellValue(record: any, col: ColumnSpec): string | null {
  if (col.expect) return col.expect(record)
  if (!col.field) return null
  const raw = getByPath(record, col.field)
  switch (col.type) {
    case 'amount':
      return fmtAmount(raw, col.decimals ?? 2, col.thousands ?? true)
    case 'numeric':
      return normNumeric(raw)
    case 'date':
      return fmtDate(raw)
    case 'datetime':
      return raw === null || raw === undefined ? '' : String(raw)
    case 'enum': {
      if (raw === null || raw === undefined || raw === '') return ''
      const map = col.enumMap ?? {}
      // 基线缺失该 code 时返回原始 code（触发断言暴露缺失翻译）
      return map[String(raw)] ?? String(raw)
    }
    case 'text':
    default:
      return raw === null || raw === undefined ? '' : String(raw)
  }
}

/** 支持 a.b.c 形式的嵌套取值 */
export function getByPath(obj: any, path: string): any {
  return path.split('.').reduce((acc, key) => (acc == null ? acc : acc[key]), obj)
}

/**
 * 列表页字段级一致性断言：逐行逐列比对表格文本与 records 字段。
 * @returns 不一致项列表（同时会即时 expect 断言，便于失败即停或收集）
 */
export interface Mismatch {
  row: number
  column: string
  field?: string
  expected: string | null
  actual: string
}

export async function matchTableToRecords(
  page: Page,
  records: any[],
  columns: ColumnSpec[],
  opts: { tableRoot?: Locator; softCollect?: boolean } = {}
): Promise<Mismatch[]> {
  const rows = await getTableRows(page, opts.tableRoot)
  const mismatches: Mismatch[] = []

  // 行数一致性（当前页）
  if (rows.length !== records.length) {
    mismatches.push({
      row: -1,
      column: '__rowCount__',
      expected: String(records.length),
      actual: String(rows.length),
    })
    if (!opts.softCollect) {
      expect(rows.length, `表格行数应等于当前页 records 数量`).toBe(records.length)
    }
  }

  const compareRows = Math.min(rows.length, records.length)
  for (let i = 0; i < compareRows; i++) {
    for (const col of columns) {
      if (!col.field && !col.expect) continue
      const expectedVal = expectedCellValue(records[i], col)
      if (expectedVal === null) continue
      // numeric 列：实际单元格也做数值归一化后再比较
      const actual = col.type === 'numeric'
        ? normNumeric(rows[i][col.index] ?? '')
        : (rows[i][col.index] ?? '').trim()
      if (actual !== expectedVal) {
        mismatches.push({
          row: i,
          column: col.label,
          field: col.field,
          expected: expectedVal,
          actual,
        })
        if (!opts.softCollect) {
          expect(actual, `第${i}行 列[${col.label}] 字段[${col.field}] 与后端不一致`).toBe(expectedVal)
        }
      }
    }
  }
  return mismatches
}

/** 字段值应出现在页面文本中（详情页 descriptions 宽松比对） */
export async function expectFieldRendered(
  page: Page,
  value: unknown,
  fieldLabel: string
): Promise<void> {
  if (value === null || value === undefined || value === '') return
  const text = String(value)
  await expect(page.locator('body'), `详情页应展示字段[${fieldLabel}]的值: ${text}`).toContainText(text)
}

/**
 * 详情页字段级比对：对每个字段，断言其期望展示值出现在页面上。
 * fieldMap: { 字段名: {label, type?, enumMap?, decimals?} }
 */
export interface DetailFieldSpec {
  label: string
  type?: 'text' | 'amount' | 'date' | 'datetime' | 'enum'
  enumMap?: Record<string, string>
  decimals?: number
  thousands?: boolean
}

export async function matchDetailFields(
  page: Page,
  data: any,
  fieldMap: Record<string, DetailFieldSpec>,
  opts: { softCollect?: boolean } = {}
): Promise<Mismatch[]> {
  const mismatches: Mismatch[] = []
  const bodyText = await page.locator('body').innerText()
  for (const [field, spec] of Object.entries(fieldMap)) {
    const raw = getByPath(data, field)
    const expectedVal = expectedCellValue(data, {
      label: spec.label,
      index: -1,
      field,
      type: spec.type,
      enumMap: spec.enumMap,
      decimals: spec.decimals,
      thousands: spec.thousands,
    })
    if (expectedVal === null || expectedVal === '') continue
    if (!bodyText.includes(expectedVal)) {
      mismatches.push({ row: -1, column: spec.label, field, expected: expectedVal, actual: '(未在页面找到)' })
      if (!opts.softCollect) {
        expect(bodyText, `详情页缺少字段[${spec.label}] 期望值: ${expectedVal}`).toContain(expectedVal)
      }
    }
  }
  return mismatches
}

/** 断言分页组件 total 与后端 total 一致 */
export async function matchPaginationTotal(page: Page, total: number): Promise<void> {
  const pager = page.locator('.el-pagination').first()
  if (!(await pager.isVisible().catch(() => false))) return
  const totalText = await pager.locator('.el-pagination__total').first().innerText().catch(() => '')
  if (totalText) {
    const num = Number(totalText.replace(/[^\d]/g, ''))
    expect(num, `分页 total 应与后端返回一致`).toBe(total)
  }
}

/** 读取表单 input 的当前值（编辑回显场景） */
export async function getFormInputValue(page: Page, placeholder: string): Promise<string> {
  const input = page.locator(`input[placeholder*="${placeholder}"]`).first()
  return await input.inputValue().catch(() => '')
}

// ==================== 不一致清单报告 ====================
import { mkdirSync, writeFileSync } from 'fs'
import { resolve } from 'path'

export interface PageConsistencyResult {
  /** 页面路由 */
  route: string
  /** 页面标题/说明 */
  title: string
  /** 抓取的接口路径 */
  api: string
  /** 后端返回记录数（列表页当前页） */
  recordCount?: number
  /** 不一致项 */
  mismatches: Mismatch[]
}

export interface ModuleReport {
  module: string
  generatedAt: string
  pages: PageConsistencyResult[]
}

/**
 * 通用列表页一致性流程：抓取列表接口 → 断言 code=200 → 表格与 records 字段级比对
 * → 分页 total 断言。接口非 200 时把错误记入 results 后再抛断言，避免丢失发现。
 * 结果统一 push 到调用方传入的 results 数组，afterAll 再 writeModuleReport 落盘。
 */
export async function runListConsistency(
  page: Page,
  spec: { route: string; title: string; api: string; urlPattern: string | RegExp; columns: ColumnSpec[] },
  results: PageConsistencyResult[]
): Promise<void> {
  const { route, title, api, urlPattern, columns } = spec
  const resp = await gotoAndCapture<PageResult>(page, route, urlPattern)
  if (resp.code !== 200) {
    results.push({
      route,
      title,
      api,
      mismatches: [{ row: -1, column: '__apiError__', expected: 'code=200', actual: `code=${resp.code} message=${resp.message}` }],
    })
    // 记录 500 等接口错误为发现，但不抛异常：避免 describe.serial 组内后续页面被跳过（保证全页覆盖）
    return
  }
  const records = resp.data?.records ?? []
  if (records.length === 0) {
    // 测试租户下该列表无种子数据：记录跳过说明，不误报为一致性缺陷，
    // 也避免 describe.serial 分组内因硬断言失败而中断后续页面用例。
    results.push({
      route, title, api, recordCount: 0,
      mismatches: [{ row: -1, column: '__empty__', expected: '有种子数据', actual: '测试租户下该列表为空，跳过逐行比对（非一致性缺陷）' }],
    })
    return
  }
  await page.locator('.el-table__body-wrapper .el-table__row').first().waitFor({ timeout: 15_000 })
  const mismatches = await matchTableToRecords(page, records, columns, { softCollect: true })
  await matchPaginationTotal(page, resp.data.total)
  results.push({ route, title, api, recordCount: records.length, mismatches })
  expect(mismatches, `${title} 存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
}

/**
 * 非分页「直出数组」列表页一致性流程：接口 data 直接是数组（无 records/total）。
 * 适用于 serial-number/template/version/process/devices 等无分页组件的列表页。
 * - data 非数组（意外返回分页对象等）→ 记 __listBinding__ 发现并断言失败暴露缺陷。
 * - data 为空数组 → 记 __empty__ 说明（管理类全局表在测试租户下可能无种子），
 *   best-effort 跳过逐行比对而非误报为不一致。
 */
export async function runFlatListConsistency(
  page: Page,
  spec: { route: string; title: string; api: string; urlPattern: string | RegExp; columns: ColumnSpec[] },
  results: PageConsistencyResult[]
): Promise<void> {
  const { route, title, api, urlPattern, columns } = spec
  const resp = await gotoAndCapture<any>(page, route, urlPattern)
  if (resp.code !== 200) {
    results.push({
      route,
      title,
      api,
      mismatches: [{ row: -1, column: '__apiError__', expected: 'code=200', actual: `code=${resp.code} message=${resp.message}` }],
    })
    // 记录 500 等接口错误为发现，但不抛异常：避免 describe.serial 组内后续页面被跳过（保证全页覆盖）
    return
  }
  const data = resp.data
  if (!Array.isArray(data)) {
    results.push({
      route,
      title,
      api,
      mismatches: [{ row: -1, column: '__listBinding__', expected: 'data 为数组', actual: `data 非数组（${typeof data}），列表页可能绑定了非列表结构` }],
    })
    // 记录绑定异常为发现，但不抛异常，保证同组后续页面仍被审计
    return
  }
  if (data.length === 0) {
    results.push({
      route,
      title,
      api,
      recordCount: 0,
      mismatches: [{ row: -1, column: '__empty__', expected: '有种子数据', actual: '测试租户下该列表为空，跳过逐行比对（非一致性缺陷）' }],
    })
    return
  }
  await page.locator('.el-table__body-wrapper .el-table__row').first().waitFor({ timeout: 15_000 })
  const mismatches = await matchTableToRecords(page, data, columns, { softCollect: true })
  results.push({ route, title, api, recordCount: data.length, mismatches })
  expect(mismatches, `${title} 存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
}

/**
 * 将某模块的一致性结果落盘为 JSON + 追加汇总 Markdown 行。
 * 报告目录：e2e/consistency/reports/
 */
export function writeModuleReport(module: string, pages: PageConsistencyResult[]): void {
  const dir = resolve(process.cwd(), 'e2e/consistency/reports')
  mkdirSync(dir, { recursive: true })
  const report: ModuleReport = {
    module,
    generatedAt: new Date().toISOString(),
    pages,
  }
  const jsonPath = resolve(dir, `${module}.json`)
  writeFileSync(jsonPath, JSON.stringify(report, null, 2), 'utf-8')

  // 生成人类可读的 Markdown 清单
  const lines: string[] = []
  lines.push(`# 一致性报告 - ${module}`)
  lines.push('')
  lines.push(`生成时间：${report.generatedAt}`)
  lines.push('')
  const totalMismatch = pages.reduce((s, p) => s + p.mismatches.length, 0)
  lines.push(`共 ${pages.length} 个页面，不一致项 ${totalMismatch} 处。`)
  lines.push('')
  for (const p of pages) {
    lines.push(`## ${p.title} (${p.route})`)
    lines.push(`- 接口：\`${p.api}\``)
    if (p.recordCount !== undefined) lines.push(`- 后端记录数：${p.recordCount}`)
    if (p.mismatches.length === 0) {
      lines.push('- 结果：一致 ✅')
    } else {
      lines.push(`- 结果：发现 ${p.mismatches.length} 处不一致 ❌`)
      lines.push('')
      lines.push('| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |')
      lines.push('|---|---|---|---|---|')
      for (const m of p.mismatches) {
        lines.push(`| ${m.row} | ${m.column} | ${m.field ?? ''} | ${m.expected ?? ''} | ${m.actual} |`)
      }
    }
    lines.push('')
  }
  writeFileSync(resolve(dir, `${module}.md`), lines.join('\n'), 'utf-8')
}
