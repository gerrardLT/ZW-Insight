/**
 * 系统使用文档注册中心（Markdown 数据源）。
 *
 * 取代旧的 src/constants/user-guide.ts：正文以 Markdown 文件维护（src/docs/help/*.md），
 * 本模块负责在构建期把 md 与配图打入 bundle，并向消费方提供结构化元数据。
 *
 * 每章 md 顶部含 frontmatter（--- 包裹的 key: value）：
 *   id / title / module / route(可选) / keywords / summary
 * 正文每条均锚定真实实现，修改对应实现时须同步更新：
 *   - 模块入口路由 ← src/router/index.ts
 *   - 业务主线流程 ← keys/lifecycle-sim-v2.sh 的 19 阶段闭环
 *   - 校验规则 ← 后端各 Service（预算 BLOCK、可付上限、超结算/超合同拦截、
 *     删除引用拦截、二次确认 449、质保金逾期催办等）
 *
 * 消费方：
 *   - src/views/help/guide.vue 渲染全文（markdown-it + mermaid）并支持锚点定位
 *   - DefaultLayout 命令面板为每章生成一条「使用文档」命令，keywords 供检索
 */

export interface GuideChapterMeta {
  /** 锚点 id，命令面板跳转 /help/guide#<id> */
  id: string
  title: string
  /** 所属一级菜单 */
  module: string
  /** 主入口路由（隐藏页不填） */
  route?: string
  /** 命令面板检索关键词（别名/拼音/路径片段） */
  keywords: string
  /** 一句话定位 */
  summary: string
  /** 去掉 frontmatter 的 Markdown 正文 */
  body: string
}

/** 章节展示顺序（业务逻辑顺序，非文件名序）；新增章节须在此登记 */
const ORDER: string[] = [
  'quickstart',
  'workflow-approval',
  'main-flow',
  'dashboard',
  'project',
  'tender',
  'contract',
  'budget',
  'purchase',
  'labor',
  'material',
  'machine',
  'subcontract',
  'finance',
  'site',
  'hr',
  'archive',
  'system',
  'basedata',
]

/** 构建期内联所有章节 md 原文（键为相对本文件的 ./<id>.md） */
const RAW_MODULES = import.meta.glob('./*.md', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

/** 构建期内联所有配图，键为相对路径 ./images/<...>，值为 vite 解析后的资源 URL */
const IMAGE_MODULES = import.meta.glob('./images/**/*.{png,jpg,jpeg,svg,gif}', {
  import: 'default',
  eager: true,
}) as Record<string, string>

/** 解析极简 frontmatter（仅支持单层 `key: value` 行，值不含换行） */
function splitFrontmatter(raw: string): { meta: Record<string, string>; body: string } {
  const norm = raw.replace(/\r\n/g, '\n')
  if (!norm.startsWith('---\n')) return { meta: {}, body: norm }
  const end = norm.indexOf('\n---', 4)
  if (end === -1) return { meta: {}, body: norm }
  const fmBlock = norm.slice(4, end)
  const body = norm.slice(end + '\n---'.length).replace(/^\n+/, '')
  const meta: Record<string, string> = {}
  for (const line of fmBlock.split('\n')) {
    const idx = line.indexOf(':')
    if (idx === -1) continue
    const key = line.slice(0, idx).trim()
    const val = line.slice(idx + 1).trim()
    if (key) meta[key] = val
  }
  return { meta, body }
}

/** 从 glob 键（./<file>.md）取文件名作为兜底 id */
function basenameId(key: string): string {
  const file = key.split('/').pop() || key
  return file.replace(/\.md$/, '')
}

/** 按 id 归档的全部章节 */
const BY_ID: Record<string, GuideChapterMeta> = {}
for (const [key, raw] of Object.entries(RAW_MODULES)) {
  const { meta, body } = splitFrontmatter(raw)
  const id = meta.id || basenameId(key)
  BY_ID[id] = {
    id,
    title: meta.title || id,
    module: meta.module || '通用',
    route: meta.route || undefined,
    keywords: meta.keywords || '',
    summary: meta.summary || '',
    body,
  }
}

/** 有序章节列表：按 ORDER 取，未在 ORDER 登记的追加在末尾（保证不丢章） */
export const USER_GUIDE: GuideChapterMeta[] = [
  ...ORDER.map((id) => BY_ID[id]).filter(Boolean),
  ...Object.values(BY_ID).filter((c) => !ORDER.includes(c.id)),
]

/** 取指定章节正文（Markdown） */
export function getChapter(id: string): GuideChapterMeta | undefined {
  return BY_ID[id]
}

/**
 * 解析 Markdown 中的图片相对路径为构建后资源 URL。
 * md 与 registry.ts 同目录，故 md 内的 `./images/x.png` 与 IMAGE_MODULES 键一致。
 * 未命中（如路径写错）时开发现场告警便于立即定位，线上原样返回交由浏览器处理。
 */
export function resolveImage(relPath: string): string {
  const url = IMAGE_MODULES[relPath]
  if (!url && import.meta.env.DEV) {
    console.warn(`[user-guide] 图片死链：${relPath} 不在 src/docs/help/images 中，请核对文件名`)
  }
  return url || relPath
}
