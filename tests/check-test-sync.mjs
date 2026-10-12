#!/usr/bin/env node
/**
 * 测试同步守卫（Test-Sync Guard）—— 仅告警，不阻断
 *
 * 目的（2026-10-12 用户需求）：代码改动/需求变更时，配套的测试改动要「同步」，
 * 且本次是否同步要留痕可查。本脚本不评覆盖率、不跑用例，只做一件事：
 *   把本次提交范围内的「生产代码改动」与「测试代码改动」做映射核对，
 *   逐文件给出 SYNCED / AREA / UNSYNCED / EXEMPT 判定，并落一份报告。
 *
 * 用法：
 *   node tests/check-test-sync.mjs                 # 默认核对 origin/main..HEAD
 *   node tests/check-test-sync.mjs --range A..B    # 指定范围
 *   node tests/check-test-sync.mjs --last          # 只看最后一个提交
 *   node tests/check-test-sync.mjs --hook          # pre-push 钩子模式（精简输出）
 *   node tests/check-test-sync.mjs --no-report     # 只打印，不落报告
 *
 * 判定口径（按「契约面」区分所需测试层级，这是本工具的核心价值）：
 *
 *   requiredLayer 由生产文件的性质决定：
 *     api  —— Controller / DTO / VO / 前端 api/*.ts 等「接口契约面」，
 *             必须由 API/E2E 层测试（keys/test-api-*.sh、e2e/api-tests、e2e/consistency、
 *             e2e/tests/real）覆盖；**同模块的 Service 单测不算同步**
 *     ui   —— 前端 views/components/router，必须由组件测试或 UI E2E 覆盖
 *     unit —— Service/Mapper/Entity/utils/stores 等，模块内单测即可
 *
 *   SYNCED   存在「所需层级」的测试改动，且与生产文件有直接名字对应
 *   AREA     存在「所需层级」的测试改动，但无直接名字对应（同域即可）
 *   UNSYNCED 没有任何「所需层级」的测试改动  ← 需要人工确认
 *   EXEMPT   命中豁免规则（文档/配置/迁移/样式/资源等，按约定无需测试）
 *
 *   ⚠️ 之所以区分层级：实测提交 e5bdaa07 给 ProjectCreateRequest 加了 @NotBlank，
 *      只补了同模块 Service 单测，却没动 25 个 api-tests/e2e spec，导致整个 L5 层
 *      烂了两个月无人发现。若只看「同域有无测试改动」，这类契约漂移会被放过。
 *
 * 退出码：恒为 0（仅告警）。若将来要改为阻断，把 EXIT_ON_UNSYNCED 置 true。
 */
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = resolve(__dirname, '..')
const EXIT_ON_UNSYNCED = false // 用户决策：仅告警记录，不阻断

// ─────────────────────────────────────────────────────────────
// 规则表
// ─────────────────────────────────────────────────────────────

/** 测试代码路径（改动这些即视为「测试侧有动作」） */
const TEST_PATTERNS = [
  /^zw-insight-server\/[^/]+\/src\/test\//,
  /^zw-insight-web\/src\/__tests__\//,
  /^zw-insight-web\/e2e\//,
  /^zw-insight-app\/(src\/.*\.test\.ts|tests\/)/,
  /^zw-supplier-portal\/(src\/.*\.test\.ts|tests\/)/,
  /^keys\/test-api-.*\.sh$/,
  /^keys\/lifecycle-sim-.*\.sh$/,
  /^keys\/verify-l4-clean\.sh$/,
  /^tests\//,
]

/** 豁免路径：按约定无需配套测试 */
const EXEMPT_PATTERNS = [
  /\.md$/,
  /^docs\//,
  /^audit-reports\//,
  /^\.kiro\//,
  /^\.github\//,
  /^deploy\//,
  /^sql\//,
  /^logo-candidates\//,
  /^ui-evolution-demo\//,
  /^\.gitignore$/,
  /^zw-insight-server\/[^/]+\/src\/main\/resources\/db\/migration\//,
  /\.(json|ya?ml|lock|toml|ini|properties|xml|txt|csv|xlsx)$/,
  /\/pom\.xml$/,
  /^zw-insight-web\/src\/docs\//, // 内置帮助内容（文本资产）
  /\.(css|scss|less)$/,
  /\.(png|jpe?g|svg|webp|ico|gif|ttf|woff2?)$/,
]

/** 生产代码路径 → 归属域 + 所需测试层级 */
function classifyProd(file) {
  let m
  if ((m = file.match(/^zw-insight-server\/(zw-[a-z-]+)\/src\/main\/java\/(.+)\.java$/))) {
    const area = m[1]
    const stem = m[2].split('/').pop()
    let layer = 'unit'
    let why = '模块内单元测试即可'
    if (/\/controller\//.test(file)) { layer = 'api'; why = 'Controller 变更影响接口契约' }
    else if (/\/domain\/dto\//.test(file)) { layer = 'api'; why = 'DTO 变更影响请求契约（含校验规则）' }
    else if (/\/vo\//.test(file)) { layer = 'api'; why = 'VO 变更影响响应契约' }
    else if (/\/enums\//.test(file) || /\/domain\//.test(file)) { why = '领域模型/枚举，模块内单测' }
    return { kind: 'backend', area, stem, path: file, layer, why }
  }
  if ((m = file.match(/^zw-insight-web\/src\/(.+)\.(vue|ts)$/))) {
    const stem = m[1].split('/').pop()
    let layer = 'unit'
    let why = '前端单元测试即可'
    if (file.startsWith('zw-insight-web/src/api/')) { layer = 'api'; why = '前端 API 定义变更影响三端契约' }
    else if (file.startsWith('zw-insight-web/src/router/')) { layer = 'ui'; why = '路由变更影响导航与守卫' }
    else if (file.startsWith('zw-insight-web/src/views/')) { layer = 'ui'; why = '页面变更需组件测试或 UI E2E' }
    else if (file.startsWith('zw-insight-web/src/components/')) { layer = 'ui'; why = '组件变更需组件测试' }
    else if (file.startsWith('zw-insight-web/src/layouts/')) { layer = 'ui'; why = '布局变更需 UI 测试' }
    return { kind: 'web', area: 'zw-insight-web', stem, path: file, layer, why }
  }
  if ((m = file.match(/^zw-insight-app\/src\/(.+)\.(vue|ts)$/))) {
    return { kind: 'app', area: 'zw-insight-app', stem: m[1].split('/').pop(), path: file, layer: 'unit', why: '移动端单元测试' }
  }
  if ((m = file.match(/^zw-supplier-portal\/src\/(.+)\.(vue|ts)$/))) {
    return { kind: 'portal', area: 'zw-supplier-portal', stem: m[1].split('/').pop(), path: file, layer: 'unit', why: '门户单元测试' }
  }
  return null
}

/** 各「所需测试层级」对应的可接受测试路径 */
const LAYER_PATTERNS = {
  api: [
    /^keys\/test-api-.*\.sh$/,
    /^keys\/lifecycle-sim-.*\.sh$/,
    /^zw-insight-web\/e2e\/api-tests\//,
    /^zw-insight-web\/e2e\/consistency\//,
    /^zw-insight-web\/e2e\/tests\/real\//,
    /^tests\//,
  ],
  ui: [
    /^zw-insight-web\/src\/__tests__\//,
    /^zw-insight-web\/e2e\/tests\/real\//,
    /^zw-insight-web\/e2e\/visual/,
    /^zw-insight-web\/e2e\/consistency\//,
  ],
  unit: [
    /^zw-insight-server\/[^/]+\/src\/test\//,
    /^zw-insight-web\/src\/__tests__\//,
    /^zw-insight-app\/(src\/.*\.test\.ts|tests\/)/,
    /^zw-supplier-portal\/(src\/.*\.test\.ts|tests\/)/,
  ],
}

const satisfiesLayer = (testFile, layer) => (LAYER_PATTERNS[layer] || []).some((re) => re.test(testFile))

/** 测试文件 → 归属域 */
function classifyTest(file) {
  const m = file.match(/^zw-insight-server\/(zw-[a-z-]+)\/src\/test\//)
  if (m) return { kind: 'backend', area: m[1] }
  if (file.startsWith('zw-insight-web/')) return { kind: 'web', area: 'zw-insight-web' }
  if (file.startsWith('zw-insight-app/')) return { kind: 'app', area: 'zw-insight-app' }
  if (file.startsWith('zw-supplier-portal/')) return { kind: 'portal', area: 'zw-supplier-portal' }
  if (file.startsWith('keys/') || file.startsWith('tests/')) return { kind: 'e2e', area: 'e2e' }
  return null
}

const isTest = (f) => TEST_PATTERNS.some((re) => re.test(f))
const isExempt = (f) => EXEMPT_PATTERNS.some((re) => re.test(f))

/**
 * 直接对应判定：
 *  后端 XxxService.java → 变更集里存在名字含 XxxService 的测试文件
 *  前端 views/project/index.vue → 变更集里存在名字含 project 的测试文件
 */
function hasDirectMatch(prod, testFiles) {
  const stem = prod.stem
  const keys = new Set()
  if (prod.kind === 'backend') {
    keys.add(stem) // XxxService / XxxController / XxxMapper / XxxRequest ...
  } else {
    keys.add(stem) // index / form / detail ...
    // 前端还允许用「所属目录名」做直接对应（views/project/index.vue → project-*.test.ts）
    const parts = prod.path.split('/')
    if (parts.length >= 2) keys.add(parts[parts.length - 2])
  }
  for (const t of testFiles) {
    const base = t.split('/').pop().replace(/\.(test|spec)\.(ts|tsx)$/, '').replace(/Test\.java$/, '')
    for (const k of keys) {
      if (k && base.toLowerCase().includes(k.toLowerCase())) return t
    }
  }
  return null
}

// ─────────────────────────────────────────────────────────────
// git 交互
// ─────────────────────────────────────────────────────────────

function git(args) {
  return execFileSync('git', args, { cwd: REPO_ROOT, encoding: 'utf-8', maxBuffer: 1 << 26 }).trim()
}

function pickRange(argv) {
  if (argv.includes('--last')) return { range: 'HEAD~1..HEAD', label: '最近 1 个提交' }
  const i = argv.indexOf('--range')
  if (i >= 0 && argv[i + 1]) return { range: argv[i + 1], label: argv[i + 1] }
  // 默认：本地领先远端的分支差异；依次回退
  for (const cand of ['origin/main..HEAD', 'origin/master..HEAD']) {
    try {
      git(['rev-parse', '--verify', '--quiet', cand.split('..')[0]])
      const ahead = git(['rev-list', '--count', cand])
      if (Number(ahead) > 0) return { range: cand, label: `本地领先 origin/main 的 ${ahead} 个提交` }
      return { range: cand, label: '与 origin/main 一致（无待推送提交）' }
    } catch { /* 试下一个 */ }
  }
  return { range: 'HEAD~1..HEAD', label: '回退：最近 1 个提交' }
}

function changedFiles(range) {
  try {
    const out = git(['diff', '--name-only', '--diff-filter=ACMR', range])
    return out ? out.split('\n').filter(Boolean) : []
  } catch {
    return []
  }
}

// ─────────────────────────────────────────────────────────────
// 主流程
// ─────────────────────────────────────────────────────────────

function main() {
  const argv = process.argv.slice(2)
  const hookMode = argv.includes('--hook')
  const noReport = argv.includes('--no-report')
  const { range, label } = pickRange(argv)

  const files = changedFiles(range)
  const tests = files.filter(isTest)
  const prods = files.filter((f) => !isTest(f) && !isExempt(f)).map(classifyProd).filter(Boolean)
  const exemptCount = files.filter((f) => !isTest(f) && isExempt(f)).length

  const rows = prods.map((p) => {
    // 先按「所需测试层级」过滤：契约面文件只认 API/E2E 层的测试改动
    const layerTests = tests.filter((t) => satisfiesLayer(t, p.layer))
    if (layerTests.length === 0) {
      return { ...p, verdict: 'UNSYNCED', evidence: '', note: `期望 ${p.layer} 层测试（${p.why}），本次无` }
    }
    // 取证优先同域（同 Maven 模块 / 同一端），避免拿别的模块的测试充当证据
    const sameArea = layerTests.filter((t) => {
      const c = classifyTest(t)
      return c && c.area === p.area
    })
    const pool = sameArea.length ? sameArea : layerTests
    const direct = hasDirectMatch(p, pool)
    if (direct) return { ...p, verdict: 'SYNCED', evidence: direct, note: '' }
    // AREA 不张冠李戴：只报「同层有几次测试改动」，不硬凑一个无关文件当证据
    return {
      ...p,
      verdict: 'AREA',
      evidence: '',
      note: `同层（${p.layer}）有 ${pool.length} 处测试改动，但无同名对应文件`,
    }
  })

  const unsynced = rows.filter((r) => r.verdict === 'UNSYNCED')
  const synced = rows.filter((r) => r.verdict === 'SYNCED')
  const area = rows.filter((r) => r.verdict === 'AREA')

  // ── 机器可读汇总（供脚本/CI 消费，纯 ASCII） ──
  if (argv.includes('--summary')) {
    console.log(
      `prod=${prods.length} test=${tests.length} exempt=${exemptCount} ` +
      `synced=${synced.length} area=${area.length} unsynced=${unsynced.length}`
    )
    if (unsynced.length) for (const r of unsynced) console.log(`unsynced_file=${r.path}`)
    process.exit(EXIT_ON_UNSYNCED && unsynced.length > 0 ? 1 : 0)
  }

  // ── 控制台输出 ──
  const line = '─'.repeat(64)
  console.log(`\n[测试同步守卫] 核对范围：${range}（${label}）`)
  console.log(line)
  console.log(`生产代码改动 ${prods.length} 个 · 测试代码改动 ${tests.length} 个 · 豁免 ${exemptCount} 个`)
  if (prods.length === 0) {
    console.log('本次无生产代码改动（或全部命中豁免），无需同步测试。')
  } else {
    console.log(`  SYNCED ${synced.length} · AREA ${area.length} · UNSYNCED ${unsynced.length}`)
    if (unsynced.length) {
      console.log('\n⚠️  以下生产文件本次无配套测试改动，请确认是否需要补测：')
      for (const r of unsynced) console.log(`    - ${r.path}\n        ↳ ${r.note}`)
    }
    if (!hookMode && area.length) {
      console.log('\n（AREA = 所需层级内有测试改动，但无同名对应文件）')
      for (const r of area) console.log(`    ~ ${r.path}\n        ↳ ${r.note}`)
    }
  }
  console.log(line)

  // ── 落报告 ──
  if (!noReport) {
    const ts = new Date()
    const stamp = ts.toISOString().replace(/[:.]/g, '-').slice(0, 19)
    const dir = resolve(REPO_ROOT, 'audit-reports')
    if (!existsSync(dir)) mkdirSync(dir, { recursive: true })
    const out = resolve(dir, `test-sync-${stamp}.md`)

    const head = git(['rev-parse', '--short', 'HEAD'])
    const branch = (() => { try { return git(['rev-parse', '--abbrev-ref', 'HEAD']) } catch { return '?' } })()

    const md = [
      `# 测试同步核对报告`,
      ``,
      `> 生成时间：${ts.toISOString()}　·　分支 \`${branch}\`　·　HEAD \`${head}\``,
      `> 核对范围：\`${range}\`（${label}）`,
      `> 口径：本报告仅记录「生产代码改动是否配套了测试改动」，**不评价覆盖率、不阻断任何操作**。`,
      ``,
      `## 汇总`,
      ``,
      `| 指标 | 数量 |`,
      `|---|---:|`,
      `| 生产代码改动 | ${prods.length} |`,
      `| 测试代码改动 | ${tests.length} |`,
      `| 豁免（文档/配置/迁移/样式/资源） | ${exemptCount} |`,
      `| SYNCED（有直接对应测试改动） | ${synced.length} |`,
      `| AREA（同域内有测试改动） | ${area.length} |`,
      `| **UNSYNCED（无任何配套测试改动）** | **${unsynced.length}** |`,
      ``,
    ]

    if (rows.length) {
      md.push(`## 逐文件判定`, ``, `| 判定 | 生产文件 | 所需层级 | 对应测试证据 | 说明 |`, `|---|---|---|---|---|`)
      for (const r of rows) {
        const icon = { SYNCED: '✅', AREA: '🟡', UNSYNCED: '❌' }[r.verdict]
        const layerCell = r.verdict === 'UNSYNCED' ? `**${r.layer}**` : r.layer
        md.push(
          `| ${icon} ${r.verdict} | \`${r.path}\` | ${layerCell} | ` +
          `${r.evidence ? '`' + r.evidence + '`' : '—'} | ${r.note || '—'} |`
        )
      }
      md.push(``)
    }

    md.push(`## 本次测试侧改动清单`, ``)
    if (tests.length) for (const t of tests) md.push(`- \`${t}\``)
    else md.push(`（无）`)
    md.push(``)

    if (unsynced.length) {
      md.push(
        `## 待确认项`, ``,
        `以下生产文件本次没有配套的「所需层级」测试改动。若属纯重构、日志措辞、等价改写等无需补测的情况，可忽略；`,
        `否则请补测试，或在下一次提交中说明理由。`, ``,
        `| 生产文件 | 期望层级与原因 |`, `|---|---|`,
        ...unsynced.map((r) => `| \`${r.path}\` | **${r.layer}** — ${r.why} |`), ``,
      )
    }

    md.push(`---`, ``, `> 本文件由 \`tests/check-test-sync.mjs\` 自动生成，默认不入库（见 .gitignore）。`)

    writeFileSync(out, md.join('\n'), 'utf-8')
    console.log(`报告已生成：audit-reports/test-sync-${stamp}.md`)
  }

  process.exit(EXIT_ON_UNSYNCED && unsynced.length > 0 ? 1 : 0)
}

main()
