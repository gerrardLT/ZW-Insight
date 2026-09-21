<template>
  <div class="guide-container">
    <!-- 顶部：标题 + 章节内检索 + 目录（锚点） -->
    <el-card shadow="never" class="guide-card" data-testid="guide-header">
      <div class="card-header">
        <span class="card-title">系统使用文档</span>
        <span class="card-sub">按业务模块组织的图文操作指南；可用命令面板（Ctrl+K）输入关键词直达任意章节</span>
      </div>
      <el-input
        v-model="keyword"
        class="guide-search"
        placeholder="在本页检索章节标题 / 步骤 / 规则 / 常见问题…"
        clearable
        data-testid="guide-search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="guide-toc" data-testid="guide-toc">
        <el-tag
          v-for="ch in filtered"
          :key="ch.id"
          class="toc-tag"
          effect="plain"
          :data-testid="'guide-toc-' + ch.id"
          @click="jumpTo(ch.id)"
        >
          {{ ch.module }} · {{ shortTitle(ch.title) }}
        </el-tag>
        <span v-if="!filtered.length" class="toc-empty">无匹配章节</span>
      </div>
    </el-card>

    <!-- 逐章渲染 Markdown 正文 -->
    <el-card
      v-for="{ ch, html } in rendered"
      :id="'guide-' + ch.id"
      :key="ch.id"
      shadow="never"
      class="guide-card guide-chapter"
      :data-testid="'guide-chapter-' + ch.id"
    >
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ ch.title }}</span>
          <span class="card-sub">{{ ch.module }}<template v-if="ch.route"> · {{ ch.route }}</template></span>
        </div>
      </template>
      <p class="chapter-summary">{{ ch.summary }}</p>
      <!-- eslint-disable-next-line vue/no-v-html —— 内容为本仓库静态 md，经 markdown-it(html:false) 转义 -->
      <div class="zw-md" v-html="html"></div>
    </el-card>

    <el-card v-if="!filtered.length" shadow="never" class="guide-card">
      <el-empty description="没有匹配的章节，换个关键词试试" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 系统使用文档页（/help/guide）。
 * 正文来自 src/docs/help/*.md（Markdown，锚定真实路由与后端校验规则），
 * 由 src/docs/help/markdown.ts（markdown-it）渲染，mermaid 代码块出图为 SVG。
 * 支持：① 本页关键词检索（标题/模块/正文全文匹配）；② 锚点定位
 * （命令面板跳转 /help/guide#<chapterId> 时滚动到对应章节）；③ 图随明暗主题重渲染。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from '@/components/icons/registry'
import { useAppStore } from '@/stores/app'
import { USER_GUIDE, type GuideChapterMeta } from '@/docs/help/registry'
import { renderMarkdown } from '@/docs/help/markdown'

const route = useRoute()
const appStore = useAppStore()
const keyword = ref('')

/** 目录标签用短标题（冒号前主干，过长截断） */
function shortTitle(title: string): string {
  const head = title.split('：')[0] || title
  return head.length > 8 ? head.slice(0, 8) + '…' : head
}

/** 全文检索：标题 / 模块 / 摘要 / 关键词 / 正文 任一命中即保留 */
const filtered = computed<GuideChapterMeta[]>(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return USER_GUIDE
  return USER_GUIDE.filter((ch) => {
    const hay = [ch.title, ch.module, ch.summary, ch.keywords, ch.route || '', ch.body]
      .join('\n')
      .toLowerCase()
    return hay.includes(q)
  })
})

/** 渲染结果按章节缓存（避免检索框每敲一键全量重渲染 19 章） */
const rendered = computed(() => filtered.value.map((ch) => ({ ch, html: renderMarkdown(ch.body) })))

function jumpTo(id: string) {
  // 同页内锚点：直接滚动；跨页由命令面板 router.push 带 hash 进入
  const el = document.getElementById('guide-' + id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** 进入页面或 hash 变化时定位到对应章节 */
async function scrollToHash() {
  const hash = route.hash.replace('#', '')
  if (!hash) return
  await nextTick()
  const el = document.getElementById('guide-' + hash)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

// ===== mermaid 出图 =====
let mermaidMod: any = null
let mmdSeq = 0

async function ensureMermaid() {
  if (!mermaidMod) {
    const m = await import('mermaid')
    mermaidMod = (m as any).default || m
  }
  return mermaidMod
}

/** 扫描当前 DOM 中的 mermaid 占位块，逐个渲染为 SVG（幂等：已出图的跳过） */
async function renderMermaid() {
  const blocks = Array.prototype.slice.call(
    document.querySelectorAll('.zw-md .mermaid-block[data-src]:not([data-done])')
  ) as HTMLElement[]
  if (!blocks.length) return
  let mermaid: any
  try {
    mermaid = await ensureMermaid()
  } catch {
    // mermaid 加载失败（如离线）：保留 <pre> 兜底，不阻断文档阅读
    return
  }
  mermaid.initialize({
    startOnLoad: false,
    theme: appStore.isDark ? 'dark' : 'neutral',
    securityLevel: 'strict',
  })
  for (const el of blocks) {
    const src = decodeURIComponent(el.getAttribute('data-src') || '')
    try {
      const { svg } = await mermaid.render('zwmd-mmd-' + mmdSeq++, src)
      el.innerHTML = svg
      el.classList.add('mermaid-ok')
      el.setAttribute('data-done', '1')
    } catch {
      el.classList.add('mermaid-error')
      el.setAttribute('data-done', '1')
    }
  }
}

/** 主题切换时清空已出图标记，令其以新主题重新渲染 */
async function rerenderMermaidOnTheme() {
  const done = Array.prototype.slice.call(
    document.querySelectorAll('.zw-md .mermaid-block[data-done]')
  ) as HTMLElement[]
  done.forEach((el) => {
    el.removeAttribute('data-done')
    el.classList.remove('mermaid-ok', 'mermaid-error')
  })
  await nextTick()
  await renderMermaid()
}

onMounted(async () => {
  await nextTick()
  await renderMermaid()
  scrollToHash()
})
// 检索/章节变化 → 新 DOM 里可能有未出图的块
watch(filtered, async () => {
  await nextTick()
  await renderMermaid()
})
// 明暗切换 → 全量以新主题重出图
watch(() => appStore.isDark, rerenderMermaidOnTheme)
watch(() => route.hash, scrollToHash)
</script>

<style scoped>
.guide-container {
  max-width: var(--zw-content-max-width);
  margin: 0 auto;
  padding: var(--zw-space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}

.guide-card {
  border-radius: var(--zw-radius-sm);
}

.card-header {
  display: flex;
  align-items: baseline;
  gap: var(--zw-space-sm);
  flex-wrap: wrap;
}

.card-title {
  font-size: var(--zw-font-size-md);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}

.card-sub {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
}

.guide-search {
  margin-top: var(--zw-space-md);
  max-width: 480px;
}

.guide-toc {
  margin-top: var(--zw-space-md);
  display: flex;
  flex-wrap: wrap;
  gap: var(--zw-space-xs);
}

.toc-tag {
  cursor: pointer;
}

.toc-empty {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
}

.chapter-summary {
  margin: 0 0 var(--zw-space-md);
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-secondary);
}
</style>

<style>
/* ===== Markdown 正文排版（非 scoped，作用于 v-html 内容）===== */
.zw-md {
  font-size: var(--zw-font-size-sm);
  line-height: var(--zw-line-height-relaxed);
  color: var(--zw-text-secondary);
}

.zw-md > *:first-child {
  margin-top: 0;
}

.zw-md h2 {
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-base);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
  margin: var(--zw-space-lg) 0 var(--zw-space-sm);
  padding-bottom: var(--zw-space-2xs);
  border-bottom: 1px solid var(--zw-border-light);
}

.zw-md h3 {
  font-size: var(--zw-font-size-sm);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
  margin: var(--zw-space-md) 0 var(--zw-space-xs);
}

.zw-md p {
  margin: var(--zw-space-sm) 0;
}

.zw-md ol,
.zw-md ul {
  margin: var(--zw-space-sm) 0;
  padding-left: var(--zw-space-lg);
}

.zw-md ol {
  list-style: decimal;
}

.zw-md ul {
  list-style: square;
}

.zw-md li {
  margin: var(--zw-space-2xs) 0;
}

.zw-md strong {
  color: var(--zw-text-primary);
  font-weight: var(--zw-font-weight-semibold);
}

.zw-md code {
  font-family: var(--zw-font-mono);
  font-size: 0.9em;
  padding: var(--zw-space-2xs) var(--zw-space-xs);
  background: var(--zw-bg-page);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-xs);
  color: var(--zw-text-primary);
}

.zw-md pre {
  margin: var(--zw-space-sm) 0;
  padding: var(--zw-space-md);
  background: var(--zw-bg-page);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  overflow-x: auto;
}

.zw-md pre code {
  padding: 0;
  background: none;
  border: none;
}

.zw-md a {
  color: var(--zw-brand);
  text-decoration: none;
}

.zw-md a:hover {
  text-decoration: underline;
}

/* 表格 */
.zw-md table {
  width: 100%;
  margin: var(--zw-space-md) 0;
  border-collapse: collapse;
  font-size: var(--zw-font-size-sm);
}

.zw-md th,
.zw-md td {
  border: 1px solid var(--zw-border-light);
  padding: var(--zw-space-xs) var(--zw-space-sm);
  text-align: left;
  vertical-align: top;
}

.zw-md th {
  background: var(--zw-bg-page);
  color: var(--zw-text-primary);
  font-weight: var(--zw-font-weight-semibold);
}

/* 引用块（提示/口径说明） */
.zw-md blockquote {
  margin: var(--zw-space-md) 0;
  padding: var(--zw-space-sm) var(--zw-space-md);
  border-left: 3px solid var(--zw-brand);
  background: var(--zw-bg-page);
  color: var(--zw-text-secondary);
  border-radius: 0 var(--zw-radius-xs) var(--zw-radius-xs) 0;
}

.zw-md blockquote p {
  margin: 0;
}

/* 截图 */
.zw-md img {
  max-width: 100%;
  height: auto;
  display: block;
  margin: var(--zw-space-md) 0;
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-sm);
  box-shadow: var(--zw-shadow-sm);
}

.zw-md img + em,
.zw-md figcaption {
  display: block;
  text-align: center;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
  margin-top: calc(var(--zw-space-xs) * -1);
  margin-bottom: var(--zw-space-md);
}

/* mermaid 图容器 */
.zw-md .mermaid-block {
  margin: var(--zw-space-md) 0;
  padding: var(--zw-space-md);
  background: var(--zw-bg-page);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  overflow-x: auto;
  text-align: center;
}

.zw-md .mermaid-block svg {
  max-width: 100%;
  height: auto;
}

.zw-md .mermaid-block .mermaid-fallback {
  margin: 0;
  text-align: left;
}

.zw-md .mermaid-ok .mermaid-fallback {
  /* 仅在出图成功后隐藏原始定义兜底；加载失败/未完成时兜底 <pre> 始终可读 */
  display: none;
}

.zw-md .mermaid-error .mermaid-fallback {
  border: 1px dashed var(--zw-danger, #d9534f);
  padding: var(--zw-space-sm);
  border-radius: var(--zw-radius-xs);
}
</style>
