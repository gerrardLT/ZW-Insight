/**
 * 壳组件引用一致性测试（Stage 3.1，2026-09-17）
 *
 * 钉住一类**静默致命**缺陷：模板里用了 `<ZwiFormPage>` / `<ZwiField>` 等壳组件，
 * 但 `<script setup>` 漏了 import。这类缺陷：
 *   - 不报错：Vue 仅 warn「Failed to resolve component」，随后把标签当原生未知元素渲染；
 *   - 不断构建：`uni build -p h5` / `-p mp-weixin` 均 exit 0；
 *   - 不被页面测试抓到：页面测试多为 vm 级断言（调方法、看 ref），不查渲染树；
 *   - 但线上真崩：ZwiFormPage 不解析 ⇒ sticky 底部提交条整块消失，主操作按钮不可点。
 *
 * 历史实证：Stage 2 批 3「财务表单页 ZwiFormPage 收敛（9 页）」全部 9 个 finance 页面
 * 模板已换壳、脚本未加 import，208 用例全绿 + build:h5 exit 0 仍然漏网，
 * 直到 Stage 3.1 在 orphan-pages.test.ts 的 stderr 里看到 warn 才暴露。
 *
 * 故此处用源码静态扫描做门禁（与 pages-registry.test.ts 同手法）：
 * 模板出现 `<ZwiX` 就必须有 `import ZwiX from`。
 */
import { describe, it, expect } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// 以测试文件位置锚定根目录（不依赖 process.cwd()，防根目录误跑）
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SRC_DIR = path.resolve(ROOT, 'src')

/** src/components/zwi 下的壳组件清单（目录即事实源，新增壳组件自动纳入门禁） */
const SHELL_COMPONENTS = fs
  .readdirSync(path.resolve(SRC_DIR, 'components/zwi'))
  .filter((f) => f.endsWith('.vue'))
  .map((f) => f.replace(/\.vue$/, ''))

function walkVueFiles(dir: string): string[] {
  const out: string[] = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) out.push(...walkVueFiles(full))
    else if (entry.name.endsWith('.vue')) out.push(full)
  }
  return out
}

/** 切出 template 段（壳组件只可能在模板里被引用；注释里的字样不算） */
function templateOf(source: string): string {
  const start = source.indexOf('<template>')
  const end = source.lastIndexOf('</template>')
  if (start < 0 || end < 0) return ''
  return source.slice(start, end)
}

describe('壳组件引用一致性（模板用了就必须 import）', () => {
  const files = walkVueFiles(SRC_DIR)

  it('src/components/zwi 下确有壳组件（防目录改名导致门禁空转）', () => {
    expect(SHELL_COMPONENTS.length).toBeGreaterThanOrEqual(8)
    expect(SHELL_COMPONENTS).toContain('ZwiFormPage')
    expect(SHELL_COMPONENTS).toContain('ZwiField')
  })

  it('每个引用壳组件的 .vue 都有对应 import（防「Failed to resolve component」静默崩壳）', () => {
    const offenders: string[] = []

    for (const file of files) {
      const source = fs.readFileSync(file, 'utf-8')
      const tpl = templateOf(source)
      if (!tpl) continue

      for (const name of SHELL_COMPONENTS) {
        // 模板中以标签形式出现：<ZwiField ...> / <ZwiField/> / </ZwiField>
        const usedInTemplate = new RegExp(`<${name}[\\s>/]`).test(tpl)
        if (!usedInTemplate) continue

        const imported = new RegExp(`import\\s+${name}\\s+from`).test(source)
        if (!imported) {
          offenders.push(`${path.relative(SRC_DIR, file).replace(/\\/g, '/')} → ${name}`)
        }
      }
    }

    expect(offenders).toEqual([])
  })

  it('反向：import 了壳组件却未在模板使用（死 import，收敛后须清掉）', () => {
    const deadImports: string[] = []

    for (const file of files) {
      const source = fs.readFileSync(file, 'utf-8')
      const tpl = templateOf(source)

      for (const name of SHELL_COMPONENTS) {
        const imported = new RegExp(`import\\s+${name}\\s+from`).test(source)
        if (!imported) continue
        if (!new RegExp(`<${name}[\\s>/]`).test(tpl)) {
          deadImports.push(`${path.relative(SRC_DIR, file).replace(/\\/g, '/')} → ${name}`)
        }
      }
    }

    expect(deadImports).toEqual([])
  })
})
