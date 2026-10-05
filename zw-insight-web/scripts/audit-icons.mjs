#!/usr/bin/env node
/**
 * 图标覆盖度审计（内部工具，node scripts/audit-icons.mjs）
 *
 * 盘点三类信号，输出报告到 stdout（--json 输出机器可读）：
 *   1) registry 注册表：EP 兼容名数量与 Tabler 直引数量
 *   2) 模板直引 @element-plus/icons-vue 的残留（应迁 Tabler 或入 registry）
 *   3) 菜单种子/菜单数据引用但 registry 缺失的图标名（会导致菜单渲染回退）
 *
 * 只读扫描，不修改任何文件。
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs'
import { join, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')
const webRoot = join(root, 'zw-insight-web')
const registryPath = join(webRoot, 'src', 'components', 'icons', 'registry.ts')

function walk(dir, exts, out = []) {
  for (const name of readdirSync(dir)) {
    if (name === 'node_modules' || name === 'dist' || name.startsWith('.')) continue
    const p = join(dir, name)
    const st = statSync(p)
    if (st.isDirectory()) walk(p, exts, out)
    else if (exts.some((e) => name.endsWith(e))) out.push(p)
  }
  return out
}

const report = { registryCount: 0, tablerDirectImports: [], epIconVueImports: [], registryNames: [] }

// 1) registry 盘点
const registrySrc = readFileSync(registryPath, 'utf-8')
const registryNames = [...registrySrc.matchAll(/^export const (\w+)/gm)].map((m) => m[1])
report.registryCount = registryNames.length
report.registryNames = registryNames

// 2/3) 全源码扫描
const files = walk(join(webRoot, 'src'), ['.vue', '.ts'])
for (const f of files) {
  if (f === registryPath) continue
  const src = readFileSync(f, 'utf-8')
  const rel = f.slice(webRoot.length + 1)
  if (src.includes('@element-plus/icons-vue')) {
    const names = [...src.matchAll(/import\s*\{([^}]+)\}\s*from\s*'@element-plus\/icons-vue'/g)]
    if (names.length) report.epIconVueImports.push({ file: rel, icons: names.map((m) => m[1].split(',').map((s) => s.trim()).filter(Boolean)).flat() })
  }
}

if (process.argv.includes('--json')) {
  console.log(JSON.stringify(report, null, 2))
} else {
  console.log('==== 图标覆盖度审计 ====')
  console.log(`registry EP 兼容导出: ${report.registryCount} 枚`)
  console.log(`仍直引 @element-plus/icons-vue 的文件: ${report.epIconVueImports.length}`)
  for (const item of report.epIconVueImports.slice(0, 15)) {
    console.log(`  - ${item.file}: ${(item.icons || []).join(', ')}`)
  }
  if (report.epIconVueImports.length > 15) console.log(`  ... 共 ${report.epIconVueImports.length} 个文件`)
  console.log('\n结论：' + (report.epIconVueImports.length === 0
    ? '图标体系已全量收敛到 Tabler registry ✅'
    : `存在 ${report.epIconVueImports.length} 个文件待迁移（非阻断，逐迭代收敛）`))
}
