/**
 * 系统使用文档 Markdown 基座测试。
 *
 * 覆盖：registry 解析（章节齐全/id 唯一/字段非空/顺序稳定）、图片引用无死链、
 * markdown-it 渲染（mermaid 代码块识别、相对图片重写、html:false 转义防 XSS）。
 * 不挂载 guide.vue（mermaid 依赖真实浏览器渲染），只测纯数据与纯渲染函数。
 */
import { describe, it, expect } from 'vitest'
import { USER_GUIDE, resolveImage, getChapter } from '@/docs/help/registry'
import { renderMarkdown } from '@/docs/help/markdown'

describe('docs/help/registry', () => {
  it('解析出全部 19 个章节，id 唯一', () => {
    expect(USER_GUIDE.length).toBe(19)
    const ids = USER_GUIDE.map((c) => c.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('章节顺序为业务主线序（quickstart → workflow-approval → main-flow 打头）', () => {
    expect(USER_GUIDE.slice(0, 3).map((c) => c.id)).toEqual(['quickstart', 'workflow-approval', 'main-flow'])
    expect(USER_GUIDE.map((c) => c.id)).toContain('finance')
    expect(USER_GUIDE.map((c) => c.id)).toContain('system')
    expect(USER_GUIDE.map((c) => c.id)).toContain('basedata')
  })

  it('每章 frontmatter 字段齐备（title/module/keywords/summary/body 非空）', () => {
    for (const ch of USER_GUIDE) {
      expect(ch.title, ch.id).toBeTruthy()
      expect(ch.module, ch.id).toBeTruthy()
      expect(ch.keywords, ch.id).toBeTruthy()
      expect(ch.summary, ch.id).toBeTruthy()
      expect(ch.body.trim().length, ch.id + ' 正文非空').toBeGreaterThan(0)
    }
  })

  it('getChapter 命中与未命中', () => {
    expect(getChapter('finance')?.module).toBe('财务管理')
    expect(getChapter('__nope__')).toBeUndefined()
  })

  it('正文引用的所有本地图片均能解析为构建资源（无死链）', () => {
    // 兼容三种写法：](./images/x.png)、](images/x.png)、](./images/x.png "title")
    const imgRe = /\]\((?:<)?((?:\.\/)?images\/[^)\s>]+)/g
    let checked = 0
    for (const ch of USER_GUIDE) {
      let m: RegExpExecArray | null
      while ((m = imgRe.exec(ch.body)) !== null) {
        const raw = m[1]
        const rel = raw.startsWith('./') ? raw : './' + raw
        // resolveImage 未命中会原样返回；命中则返回构建后 URL（必然不同于相对路径）
        expect(resolveImage(rel), `${ch.id} 引用图片不存在：${rel}`).not.toBe(rel)
        checked++
      }
    }
    // 全量图文：应有 60+ 张配图引用
    expect(checked).toBeGreaterThanOrEqual(60)
  })
})

describe('docs/help/markdown 渲染器', () => {
  it('mermaid 代码块渲染为可挂载占位（class + data-src）', () => {
    const html = renderMarkdown('```mermaid\ngraph TD\n  A-->B\n```')
    expect(html).toContain('class="mermaid-block"')
    expect(html).toContain('data-src=')
    expect(html).toContain('mermaid-fallback')
  })

  it('相对图片经 resolveImage 重写为构建资源 URL', () => {
    const html = renderMarkdown('![示例](./images/finance/payment-apply.png)')
    expect(html).toContain('<img')
    // 不应保留原始相对路径（已被映射为资源 URL）
    expect(html).not.toContain('./images/finance/payment-apply.png')
  })

  it('html:false：正文内嵌原始 HTML 被转义（防 XSS）', () => {
    const html = renderMarkdown('<script>alert(1)</script>')
    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;script&gt;')
  })

  it('表格 / 标题 / 列表等 CommonMark 元素正常渲染', () => {
    const html = renderMarkdown('## 标题\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n- 甲\n- 乙')
    expect(html).toContain('<h2>')
    expect(html).toContain('<table>')
    expect(html).toContain('<ul>')
  })
})
