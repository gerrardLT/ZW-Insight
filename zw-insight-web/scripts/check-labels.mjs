#!/usr/bin/env node
/**
 * check-labels.mjs —— 业务术语一致性审计（warn-only，不阻断）
 *
 * 基准：docs/术语表.md + src/constants/biz-labels.ts（spec: .kiro/specs/finance-clarity-terminology-ui）
 * 用法：cd zw-insight-web && npm run check:labels
 * 输出：
 *   VIOLATION —— 命中禁用词（术语表 §八），应在本批改造中消除；
 *   DEBT      —— 已知术语债（阶段三清偿），只计数不判违例；
 *   HINT      —— 页面内手写 statusMap 且未引用 biz-labels（建议迁移到常量层）。
 * 退出码恒 0（与 tests/check-test-sync.mjs 同策略：仅告警记录，不阻断）。
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(fileURLToPath(import.meta.url), '..', '..')
const VIEWS = join(ROOT, 'src', 'views')
const toRel = (p) => relative(ROOT, p).split(sep).join('/')

/** 禁用词规则：scope 限定扫描范围（相对路径正则），except 排除合法语境 */
const RULES = [
  { word: '已报备', scope: /^src\/views\// },
  { word: '招标中', scope: /^src\/views\// },
  { word: '已中标', scope: /^src\/views\// },
  { word: '已落标', scope: /^src\/views\// },
  { word: '施工中', scope: /^src\/views\// },
  { word: '结算单ID', scope: /^src\/views\// },
  { word: '其他总支付', scope: /^src\/views\// },
  // 已暂停/已关闭：项目/合同状态语境禁用（→停工/已结项）；WBS 节点、CBS 账户等非项目状态语境除外
  { word: '已暂停', scope: /^src\/views\/(project|contract)\//, except: /\/wbs\// },
  { word: '已关闭', scope: /^src\/views\/(project|contract)\//, except: /\/wbs\// },
  // 费用类别/账户名称：成本语境禁用（→成本类别/成本科目名称）；银行账户语境合法
  { word: '费用类别', scope: /^src\/views\/(budget|cockpit)\// },
  { word: '账户名称', scope: /^src\/views\/budget\// },
]

/** 已知术语债（阶段三清偿，只计数）：结算单号统一为“结算单编号”等 */
const DEBT_WORDS = ['总收入', '总支出', '结算单号', '未支付', '已支付']

function collectVueFiles(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (name === 'node_modules' || name.startsWith('_')) continue
    const full = join(dir, name)
    if (statSync(full).isDirectory()) collectVueFiles(full, out)
    else if (name.endsWith('.vue')) out.push(full)
  }
  return out
}

const files = collectVueFiles(VIEWS)
const violations = []
const debts = []
const hints = []

for (const file of files) {
  const rel = toRel(file)
  const src = readFileSync(file, 'utf8')
  const lines = src.split('\n')

  lines.forEach((line, i) => {
    for (const rule of RULES) {
      if (!rule.scope.test(rel)) continue
      if (rule.except && rule.except.test(rel)) continue
      if (line.includes(rule.word)) {
        violations.push(`${rel}:${i + 1}  禁用词「${rule.word}」`)
      }
    }
    for (const word of DEBT_WORDS) {
      if (line.includes(word)) debts.push(`${rel}:${i + 1}  术语债「${word}」`)
    }
  })

  // 手写状态映射且未引用常量层 → 迁移提示
  if (/const\s+\w*[sS]tatusMap\s*[:=]/.test(src) && !src.includes('biz-labels')) {
    hints.push(`${rel}  手写 statusMap，建议迁移到 @/constants/biz-labels`)
  }
}

console.log(`scan ${files.length} vue files under src/views`)
for (const v of violations) console.log(`VIOLATION  ${v}`)
for (const d of debts) console.log(`DEBT       ${d}`)
for (const h of hints) console.log(`HINT       ${h}`)
console.log(`summary: violations=${violations.length} debt=${debts.length} hint=${hints.length} (warn-only)`)

process.exit(0)
