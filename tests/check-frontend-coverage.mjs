/**
 * 前端覆盖率基线比对（只升不降）
 *
 * 用法：node tests/check-frontend-coverage.mjs <project-dir>
 *   例：node tests/check-frontend-coverage.mjs zw-insight-web
 *
 * 机制复刻后端覆盖率守护（deploy.yml backend job「Coverage baseline check」）：
 * 读取 <project>/coverage/coverage-summary.json 的行覆盖率（total.lines.pct），
 * 折算千分比后与 tests/frontend-coverage-baseline.json 比对，
 * 回落超过 ±1‰ 容忍带即非零退出。基线只许升不许降，更新需提交新 JSON。
 *
 * 2026-08-14 M3 新增：前端覆盖率度量门禁。
 */
import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(__dirname, '..')

const project = process.argv[2]
if (!project) {
  console.error('用法: node tests/check-frontend-coverage.mjs <project-dir>')
  process.exit(2)
}

const baselinePath = resolve(__dirname, 'frontend-coverage-baseline.json')
if (!existsSync(baselinePath)) {
  console.error(`::error::${baselinePath} 不存在，无法比对前端覆盖率基线`)
  process.exit(1)
}
const baseline = JSON.parse(readFileSync(baselinePath, 'utf-8'))
const base = baseline[project]
if (base === undefined) {
  console.log(`⚠️ ${project} 未登记基线，跳过比对（新增前端项目须先实测并登记基线）`)
  process.exit(0)
}

const summaryPath = resolve(repoRoot, project, 'coverage', 'coverage-summary.json')
if (!existsSync(summaryPath)) {
  console.error(`::error::${summaryPath} 不存在，请先运行 npm run test:coverage`)
  process.exit(1)
}
const summary = JSON.parse(readFileSync(summaryPath, 'utf-8'))
const lines = summary?.total?.lines
if (!lines) {
  console.error(`::error::${summaryPath} 缺少 total.lines 字段`)
  process.exit(1)
}

const cur = Math.floor(lines.pct * 10) // pct 为百分比，折算千分比
if (cur < base - 1) {
  console.error(`❌ ${project} 前端覆盖率回退: 当前 ${cur}‰ < 基线 ${base}‰`)
  console.error('请补测或说明后更新基线 JSON（只许升不许降）')
  process.exit(1)
}
console.log(`✅ ${project} 前端覆盖率: 当前 ${cur}‰ >= 基线 ${base}‰（lines.pct=${lines.pct}%）`)
