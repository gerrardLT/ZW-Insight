/**
 * 暗色主题契约测试（2026-08-28 移动端暗色批次）
 *
 * 钉住暗色模式落地契约：移动端无暗色基建是迁移遗留项 1，本批以纯 CSS
 * 跟随系统（@media prefers-color-scheme: dark）覆盖 token 落地。
 * 以下断言防止后续改动悄悄移除暗色块或破坏双端选择器。
 */
import { describe, it, expect } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// 以测试文件位置锚定根目录（不依赖 process.cwd()，防根目录误跑）
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const TOKENS_CSS = path.resolve(ROOT, 'src/styles/tokens.css')

const css = fs.readFileSync(TOKENS_CSS, 'utf-8')

// 提取暗色 media 块（简化：匹配 @media ... { 到行首 } 为止）
const darkBlockMatch = css.match(/@media \(prefers-color-scheme: dark\) \{([\s\S]*?)^\}/m)
const darkBlock = darkBlockMatch?.[1] ?? ''

describe('暗色主题契约（跟随系统，纯 CSS）', () => {
  it('tokens.css 含 prefers-color-scheme: dark 覆盖块', () => {
    expect(darkBlockMatch, 'tokens.css 缺少暗色 media 块').not.toBeNull()
  })

  it('暗色块选择器 :root, page 双写（H5 与 mp-weixin 双端生效）', () => {
    expect(darkBlock).toMatch(/:root,\s*page/)
  })

  it('暗色石墨阶梯关键值与 PC dark.css 同源', () => {
    expect(darkBlock).toContain('--zw-bg-page: #101214')
    expect(darkBlock).toContain('--zw-bg-card: #16181c')
    expect(darkBlock).toContain('--zw-bg-elevated: #1c1f24')
    expect(darkBlock).toContain('--zw-text-primary: #f2f3f1')
    expect(darkBlock).toContain('--zw-border: #2b2e34')
  })

  it('亮/暗两态齐名 bg-mask（弹层遮罩统一走 token）', () => {
    expect(css).toContain('--zw-bg-mask: rgba(0, 0, 0, 0.5)')
    expect(darkBlock).toContain('--zw-bg-mask: rgba(0, 0, 0, 0.55)')
  })

  it('暗色 text-inverse 翻深（红/绿实心底白字承重规则）', () => {
    expect(darkBlock).toContain('--zw-text-inverse: #14161a')
  })

  it('品牌橙与 on-primary 暗色不变（深底浅字由 token 阶梯承担）', () => {
    expect(darkBlock).not.toContain('--zw-brand:')
    expect(darkBlock).not.toContain('--zw-on-primary:')
  })

  it('页面样式中无残留硬编码遮罩黑（须走 --zw-bg-mask）', () => {
    const pagesDir = path.resolve(ROOT, 'src/pages')
    const walk = (dir: string): string[] =>
      fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
        const full = path.join(dir, e.name)
        if (e.isDirectory()) return walk(full)
        return e.name.endsWith('.vue') ? [full] : []
      })
    const offenders = walk(pagesDir).filter((f) =>
      /rgba\(0,\s*0,\s*0,\s*0\.5\)/.test(fs.readFileSync(f, 'utf-8'))
    )
    expect(offenders).toEqual([])
  })
})
