/**
 * 帮助中心 Markdown 渲染器。
 *
 * 基于 markdown-it（CommonMark）：
 *   - html:false —— 转义正文内嵌原始 HTML，杜绝 XSS（文档均为本仓库静态 md）
 *   - linkify:true —— 自动识别裸链接
 * 扩展：
 *   - image 规则：把 md 相对图片路径经 registry.resolveImage 重写为构建后资源 URL
 *   - fence 规则：```mermaid 代码块渲染为占位 div，交由 guide.vue 挂载期用 mermaid 出图
 */
import MarkdownIt from 'markdown-it'
import { resolveImage } from './registry'

const md = new MarkdownIt({ html: false, linkify: true, breaks: false })

/** 默认 image 渲染：重写非外链 src 为构建产物 URL */
md.renderer.rules.image = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const srcIdx = token.attrIndex('src')
  if (srcIdx >= 0) {
    const src = token.attrs![srcIdx][1]
    if (!/^https?:/i.test(src)) {
      token.attrs![srcIdx][1] = resolveImage(src)
    }
  }
  return self.renderToken(tokens, idx, options)
}

/** 默认 fence 渲染（代码高亮前的转义） */
const defaultFence =
  md.renderer.rules.fence ||
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

md.renderer.rules.fence = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const lang = (token.info || '').trim().split(/\s+/)[0].toLowerCase()
  if (lang === 'mermaid') {
    const code = md.utils.escapeHtml(token.content)
    const encoded = encodeURIComponent(token.content)
    // data-src 存原始定义供 mermaid 渲染；fallback <pre> 保证无 JS 时仍可读
    return `<div class="mermaid-block" data-src="${encoded}"><pre class="mermaid-fallback">${code}</pre></div>\n`
  }
  return defaultFence(tokens, idx, options, env, self)
}

/** 渲染章节正文 Markdown 为 HTML 字符串 */
export function renderMarkdown(body: string): string {
  return md.render(body)
}
