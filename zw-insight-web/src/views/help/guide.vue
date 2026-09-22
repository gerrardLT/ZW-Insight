<template>
  <div class="reader" data-testid="guide-header">
    <!-- ===== 左栏：章节导航（模块分组 + 检索过滤） ===== -->
    <aside class="reader-nav" :class="{ 'nav-open': drawerOpen }">
      <div class="nav-head">
        <span class="nav-title">系统使用文档</span>
        <span class="nav-count">{{ USER_GUIDE.length }} 章</span>
      </div>
      <el-input
        v-model="keyword"
        class="guide-search"
        placeholder="检索章节 / 内容…"
        clearable
        data-testid="guide-search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <nav class="nav-scroll" data-testid="guide-toc">
        <template v-for="(group, gi) in filteredGroups" :key="group.module + '-' + gi">
          <div class="nav-group-label">{{ group.module }}</div>
          <button
            v-for="item in group.items"
            :key="item.ch.id"
            class="nav-item"
            :class="{ active: item.ch.id === activeId }"
            :data-testid="'guide-toc-' + item.ch.id"
            @click="go(item.ch.id)"
          >
            <span class="nav-item-text">{{ navLabel(item.ch) }}</span>
            <span v-if="keyword && item.hits" class="nav-item-hits">{{ item.hits }}</span>
          </button>
        </template>
        <p v-if="!filteredGroups.length" class="nav-empty">没有匹配的章节，换个关键词试试</p>
      </nav>
    </aside>
    <div v-if="drawerOpen" class="nav-scrim" @click="drawerOpen = false" />

    <!-- ===== 中栏：单章正文（内部滚动） ===== -->
    <main ref="bodyRef" class="reader-body" @scroll.passive="onScroll">
      <button class="toc-drawer-btn" aria-label="打开章节目录" @click="drawerOpen = true">
        <el-icon><ListIcon /></el-icon>
        <span>{{ current ? current.title.split('：')[0] : '章节目录' }}</span>
      </button>

      <transition name="zw-doc" mode="out-in" appear @after-enter="renderMermaid">
        <article v-if="current" :key="activeId" class="doc-article" :data-testid="'guide-chapter-' + activeId">
          <header class="doc-head">
            <h1 class="doc-title">{{ current.title }}</h1>
            <p class="doc-meta">
              <span>{{ current.module }}</span>
              <template v-if="current.route"><span class="meta-dot" /><code>{{ current.route }}</code></template>
              <span class="meta-dot" /><span>{{ position }} / {{ USER_GUIDE.length }}</span>
            </p>
            <p class="doc-summary">{{ current.summary }}</p>
          </header>

          <!-- eslint-disable-next-line vue/no-v-html —— 内容为本仓库静态 md，经 markdown-it(html:false) 转义 -->
          <div class="zw-md" v-html="doc.html"></div>

          <nav class="doc-pager">
            <button v-if="prevCh" class="pager-btn" @click="go(prevCh.id)">
              <span class="pager-dir">← 上一章</span>
              <span class="pager-name">{{ prevCh.title.split('：')[0] }}</span>
            </button>
            <span v-else class="pager-btn is-ghost" />
            <button v-if="nextCh" class="pager-btn pager-next" @click="go(nextCh.id)">
              <span class="pager-dir">下一章 →</span>
              <span class="pager-name">{{ nextCh.title.split('：')[0] }}</span>
            </button>
          </nav>
        </article>
      </transition>
    </main>

    <!-- ===== 右栏：本章小节目录（scroll-spy） ===== -->
    <aside v-if="doc.sections.length > 1" class="reader-toc">
      <div class="toc-label">本章目录</div>
      <a
        v-for="s in doc.sections"
        :key="s.id"
        class="toc-link"
        :class="{ 'toc-active': s.id === activeSection }"
        @click.prevent="scrollToSection(s.id)"
        >{{ s.title }}</a
      >
    </aside>
  </div>
</template>

<script setup lang="ts">
/**
 * 系统使用文档阅读器（/help/guide）。
 *
 * 三栏形态：左＝章节导航（模块分组 + 全文检索过滤），中＝单章正文，右＝本章小节目录（scroll-spy）。
 * 一次只呈现一章，根治长页滚动；正文来自 src/docs/help/*.md，经 markdown-it 渲染，
 * mermaid 块懒加载出图（随明暗主题重渲染）。
 *
 * 深链：/help/guide#<chapterId> 直接打开对应章（命令面板逐章命令沿用此格式）；
 * 切章经 router.replace 同步 hash，单一事实源且不产生历史记录。
 */
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, List as ListIcon } from '@/components/icons/registry'
import { useAppStore } from '@/stores/app'
import { USER_GUIDE, getChapter, type GuideChapterMeta } from '@/docs/help/registry'
import { renderChapter, type DocSection } from '@/docs/help/markdown'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const keyword = ref('')
const activeId = ref<string>(USER_GUIDE[0]?.id ?? '')
const activeSection = ref('')
const drawerOpen = ref(false)
const bodyRef = ref<HTMLElement | null>(null)

const current = computed<GuideChapterMeta | undefined>(() => getChapter(activeId.value))
const position = computed(() => USER_GUIDE.findIndex((c) => c.id === activeId.value) + 1)
const prevCh = computed(() => USER_GUIDE[position.value - 2])
const nextCh = computed(() => USER_GUIDE[position.value])

/** 当前章渲染结果（HTML + 小节目录） */
const doc = computed<{ html: string; sections: DocSection[] }>(() =>
  current.value ? renderChapter(current.value.body) : { html: '', sections: [] }
)

/** 左栏导航：按 module 分组保持业务顺序；检索时过滤并统计命中次数 */
const filteredGroups = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  const hitsOf = (ch: GuideChapterMeta): number => {
    if (!q) return 0
    const hay = [ch.title, ch.module, ch.summary, ch.keywords, ch.route || '', ch.body]
      .join('\n')
      .toLowerCase()
    let n = 0
    let i = hay.indexOf(q)
    while (i !== -1 && n < 999) {
      n++
      i = hay.indexOf(q, i + q.length)
    }
    return n
  }
  const groups: { module: string; items: { ch: GuideChapterMeta; hits: number }[] }[] = []
  for (const ch of USER_GUIDE) {
    const hits = hitsOf(ch)
    if (q && hits === 0) continue
    const last = groups[groups.length - 1]
    if (last && last.module === ch.module) last.items.push({ ch, hits })
    else groups.push({ module: ch.module, items: [{ ch, hits }] })
  }
  return groups
})

/** 导航条目标签：冒号前主干；与分组名重复时改用冒号后内容，避免“看板/看板”式冗余 */
function navLabel(ch: GuideChapterMeta): string {
  const [head, rest] = ch.title.split('：')
  if (rest && head === ch.module) {
    return rest.length > 14 ? rest.slice(0, 14) + '…' : rest
  }
  return head
}

/** 切章：更新状态 + 经 router 同步 hash（单一事实源，replace 不新增历史）+ 回顶 */
function go(id: string) {
  if (!getChapter(id)) return
  activeId.value = id
  activeSection.value = ''
  drawerOpen.value = false
  void router.replace({ path: '/help/guide', hash: '#' + id })
  nextTick(() => bodyRef.value?.scrollTo({ top: 0 }))
}

function scrollToSection(secId: string) {
  const el = bodyRef.value?.querySelector('#' + CSS.escape(secId))
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    activeSection.value = secId
  }
}

/** scroll-spy：正文滚动时定位当前小节（rAF 节流） */
let spyRaf = 0
function onScroll() {
  if (spyRaf) return
  spyRaf = requestAnimationFrame(() => {
    spyRaf = 0
    const body = bodyRef.value
    if (!body || !doc.value.sections.length) return
    let cur = ''
    for (const s of doc.value.sections) {
      const el = body.querySelector('#' + CSS.escape(s.id)) as HTMLElement | null
      if (el && el.getBoundingClientRect().top <= 120) cur = s.id
      else break
    }
    // 滚到底时强制点亮末节：末节后的内容短于阈值时上方判定永远失配
    if (body.scrollTop + body.clientHeight >= body.scrollHeight - 2 && doc.value.sections.length) {
      cur = doc.value.sections[doc.value.sections.length - 1].id
    }
    activeSection.value = cur
  })
}
onBeforeUnmount(() => {
  if (spyRaf) cancelAnimationFrame(spyRaf)
})

/* ================= mermaid 出图 ================= */
let mermaidMod: any = null
let mmdSeq = 0

async function ensureMermaid() {
  if (!mermaidMod) {
    const m = await import('mermaid')
    mermaidMod = (m as any).default || m
  }
  return mermaidMod
}

/** 扫描当前 DOM 中未出图的 mermaid 块渲染为 SVG（幂等：data-done 标记） */
async function renderMermaid() {
  const root = bodyRef.value
  if (!root) return
  const blocks = Array.prototype.slice.call(
    root.querySelectorAll('.zw-md .mermaid-block[data-src]:not([data-done])')
  ) as HTMLElement[]
  if (!blocks.length) return
  let mermaid: any
  try {
    mermaid = await ensureMermaid()
  } catch (e) {
    // mermaid 加载失败：保留 <pre> 兜底不阻断阅读，但错误必须可见（不静默吞）
    console.warn('[user-guide] mermaid 加载失败，流程图以纯文本兜底显示：', e)
    return
  }
  mermaid.initialize({
    startOnLoad: false,
    theme: appStore.isDark ? 'dark' : 'neutral',
    securityLevel: 'strict',
    // 失败时不留临时错误节点在 body（否则随 mmdSeq 递增永久累积）
    suppressErrorRendering: true,
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

/** 主题切换：清标记令当前章以新主题重出图 */
async function rerenderMermaidOnTheme() {
  const root = bodyRef.value
  if (!root) return
  root.querySelectorAll('.zw-md .mermaid-block[data-done]').forEach((el) => {
    el.removeAttribute('data-done')
    el.classList.remove('mermaid-ok', 'mermaid-error')
  })
  await nextTick()
  await renderMermaid()
}

/* ================= 深链与生命周期 ================= */

/** 从当前路由 hash 打开对应章（同值短路：防 replace 回环与无谓回顶） */
function openFromHash() {
  const hash = route.hash.replace('#', '')
  if (hash && hash !== activeId.value && getChapter(hash)) go(hash)
}

// 检索时若当前章不在命中集，自动切到首个命中章（输入关键词即意在找内容）
watch(keyword, () => {
  const q = keyword.value.trim().toLowerCase()
  if (!q || !filteredGroups.value.length) return
  const ids = filteredGroups.value.flatMap((g) => g.items.map((it) => it.ch.id))
  if (!ids.includes(activeId.value)) go(ids[0])
})

watch(() => route.hash, openFromHash)
// 切章后 mermaid 出图由 transition 的 after-enter 钩子触发：
// mode="out-in" 下新章 DOM 要等离场动画结束才插入，nextTick 时机扫描不到块
watch(() => appStore.isDark, rerenderMermaidOnTheme)

onMounted(() => {
  openFromHash()
})
</script>

<style scoped>
/* ================= 三栏阅读器骨架 ================= */
.reader {
  height: 100%;
  display: flex;
  min-width: 0;
}

/* ---- 左栏：章节导航 ---- */
.reader-nav {
  width: var(--zw-sidebar-width);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--zw-border-light);
  background: var(--zw-bg-card);
  padding: var(--zw-space-md) var(--zw-space-sm) var(--zw-space-sm);
  min-height: 0;
}

.nav-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 var(--zw-space-sm) var(--zw-space-sm);
}

.nav-title {
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-md);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}

.nav-count {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
}

.guide-search {
  margin-bottom: var(--zw-space-sm);
}

.nav-scroll {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding-right: var(--zw-space-2xs);
}

.nav-group-label {
  font-size: var(--zw-font-size-xs);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-quaternary);
  letter-spacing: 0.5px;
  padding: var(--zw-space-md) var(--zw-space-sm) var(--zw-space-xs);
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-xs);
  width: 100%;
  padding: var(--zw-space-xs) var(--zw-space-sm);
  border: none;
  background: none;
  border-radius: var(--zw-radius-sm);
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-secondary);
  text-align: left;
  cursor: pointer;
  line-height: var(--zw-line-height-normal);
  transition: background-color 0.12s ease, color 0.12s ease;
}

.nav-item:hover {
  background: var(--zw-bg-hover);
  color: var(--zw-text-primary);
}

.nav-item.active {
  background: var(--zw-bg-active);
  color: var(--zw-brand);
  font-weight: var(--zw-font-weight-semibold);
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: calc(var(--zw-space-2xs) * -1);
  top: 25%;
  bottom: 25%;
  width: 2px;
  border-radius: var(--zw-radius-full);
  background: var(--zw-brand);
}

.nav-item-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-item-hits {
  flex-shrink: 0;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.nav-empty {
  padding: var(--zw-space-md) var(--zw-space-sm);
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
}

/* ---- 中栏：单章正文（滚动容器） ---- */
.reader-body {
  position: relative;
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: var(--zw-bg-card);
  scroll-behavior: smooth;
}

.doc-article {
  max-width: 54rem;
  margin: 0 auto;
  padding: var(--zw-space-2xl) var(--zw-space-xl) var(--zw-space-xl);
}

.doc-head {
  margin-bottom: var(--zw-space-xl);
}

.doc-title {
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-3xl);
  font-weight: var(--zw-font-weight-bold);
  line-height: var(--zw-line-height-tight);
  letter-spacing: -0.01em;
  color: var(--zw-text-primary);
  margin: 0;
}

.doc-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zw-space-xs);
  margin: var(--zw-space-sm) 0 0;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
}

.doc-meta code {
  font-family: var(--zw-font-mono);
  font-size: var(--zw-font-size-xs);
}

.meta-dot {
  width: 3px;
  height: 3px;
  border-radius: var(--zw-radius-full);
  background: var(--zw-text-quaternary);
}

.doc-summary {
  margin: var(--zw-space-md) 0 0;
  font-size: var(--zw-font-size-md);
  line-height: var(--zw-line-height-relaxed);
  color: var(--zw-text-secondary);
}

/* ---- 章末翻页 ---- */
.doc-pager {
  display: flex;
  justify-content: space-between;
  gap: var(--zw-space-md);
  margin-top: var(--zw-space-3xl);
  padding-top: var(--zw-space-md);
  border-top: 1px solid var(--zw-border-light);
}

.pager-btn {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-2xs);
  flex: 1;
  max-width: 48%;
  padding: var(--zw-space-sm-md) var(--zw-space-md);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-md);
  background: none;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.12s ease, background-color 0.12s ease;
}

.pager-btn:hover {
  border-color: var(--zw-brand);
  background: var(--zw-bg-hover);
}

.pager-btn.is-ghost {
  border: none;
  visibility: hidden;
}

.pager-next {
  text-align: right;
  align-items: flex-end;
}

.pager-dir {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
}

.pager-name {
  font-size: var(--zw-font-size-base);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- 右栏：本章小节目录 ---- */
.reader-toc {
  width: 208px;
  flex-shrink: 0;
  overflow-y: auto;
  border-left: 1px solid var(--zw-border-light);
  background: var(--zw-bg-card);
  padding: var(--zw-space-2xl) var(--zw-space-md) var(--zw-space-md);
}

.toc-label {
  font-size: var(--zw-font-size-xs);
  font-weight: var(--zw-font-weight-semibold);
  letter-spacing: 0.5px;
  color: var(--zw-text-quaternary);
  margin-bottom: var(--zw-space-sm);
}

.toc-link {
  display: block;
  padding: var(--zw-space-2xs) 0 var(--zw-space-2xs) var(--zw-space-sm);
  font-size: var(--zw-font-size-sm);
  line-height: var(--zw-line-height-normal);
  color: var(--zw-text-tertiary);
  text-decoration: none;
  border-left: 1px solid var(--zw-border-light);
  transition: color 0.12s ease, border-color 0.12s ease;
}

.toc-link:hover {
  color: var(--zw-text-primary);
}

.toc-link.toc-active {
  color: var(--zw-brand);
  border-left-color: var(--zw-brand);
  font-weight: var(--zw-font-weight-medium);
}

/* ---- 窄屏：左栏收进抽屉，右栏隐藏 ---- */
.toc-drawer-btn {
  display: none;
  position: sticky;
  top: 0;
  z-index: 5;
  width: 100%;
  align-items: center;
  gap: var(--zw-space-xs);
  padding: var(--zw-space-sm) var(--zw-space-md);
  border: none;
  border-bottom: 1px solid var(--zw-border-light);
  background: var(--zw-bg-card);
  color: var(--zw-text-primary);
  font-size: var(--zw-font-size-sm);
  font-weight: var(--zw-font-weight-semibold);
  cursor: pointer;
}

.nav-scrim {
  display: none;
}

@media (max-width: 1279px) {
  .reader-toc {
    display: none;
  }
}

@media (max-width: 1023px) {
  .reader-nav {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 1001;
    width: min(var(--zw-sidebar-width), 82vw);
    transform: translateX(-100%);
    transition: transform 0.22s cubic-bezier(0.16, 1, 0.3, 1);
    box-shadow: var(--zw-shadow-overlay);
  }

  .reader-nav.nav-open {
    transform: translateX(0);
  }

  .nav-scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 1000;
    background: var(--zw-bg-mask);
  }

  .toc-drawer-btn {
    display: flex;
  }

  .doc-article {
    padding: var(--zw-space-lg) var(--zw-space-md);
  }
}

/* ---- 切章动效：单一受控的入场瞬间（expo ease-out） ---- */
.zw-doc-enter-active {
  transition: opacity 0.16s cubic-bezier(0.16, 1, 0.3, 1), transform 0.16s cubic-bezier(0.16, 1, 0.3, 1);
}

.zw-doc-leave-active {
  transition: opacity 0.08s ease-in;
}

.zw-doc-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.zw-doc-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .zw-doc-enter-active,
  .zw-doc-leave-active {
    transition: none;
  }
}
</style>

<style>
/* ===== Markdown 正文排版（非 scoped，作用于 v-html 内容）===== */
.zw-md {
  font-size: var(--zw-font-size-base);
  line-height: var(--zw-line-height-relaxed);
  color: var(--zw-text-secondary);
}

.zw-md > *:first-child {
  margin-top: 0;
}

.zw-md h2 {
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-lg);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
  scroll-margin-top: var(--zw-space-lg);
  margin: var(--zw-space-2xl) 0 var(--zw-space-md);
  padding-bottom: var(--zw-space-xs);
  border-bottom: 1px solid var(--zw-border-light);
}

.zw-md h3 {
  font-size: var(--zw-font-size-md);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
  margin: var(--zw-space-xl) 0 var(--zw-space-sm);
}

.zw-md p {
  margin: var(--zw-space-md) 0;
}

.zw-md ol,
.zw-md ul {
  margin: var(--zw-space-md) 0;
  padding-left: var(--zw-space-lg);
}

.zw-md ol {
  list-style: decimal;
}

.zw-md ul {
  list-style: square;
}

.zw-md li {
  margin: var(--zw-space-xs) 0;
}

.zw-md li::marker {
  color: var(--zw-text-quaternary);
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
  margin: var(--zw-space-md) 0;
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
  text-underline-offset: 2px;
}

.zw-md a:hover {
  text-decoration: underline;
}

/* 表格 */
.zw-md table {
  width: 100%;
  margin: var(--zw-space-lg) 0;
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

.zw-md td code {
  font-variant-numeric: tabular-nums;
}

/* 引用块（提示/口径说明）：中性细线 + 品牌色标记，避免彩色侧边条样式 */
.zw-md blockquote {
  margin: var(--zw-space-lg) 0;
  padding: var(--zw-space-sm) var(--zw-space-md);
  border-left: 1px solid var(--zw-border);
  background: var(--zw-bg-page);
  color: var(--zw-text-secondary);
  border-radius: 0 var(--zw-radius-xs) var(--zw-radius-xs) 0;
}

.zw-md blockquote p {
  margin: 0;
}

.zw-md blockquote strong:first-child,
.zw-md blockquote code:first-of-type {
  color: var(--zw-brand);
}

/* 截图 */
.zw-md img {
  max-width: 100%;
  height: auto;
  display: block;
  margin: var(--zw-space-lg) 0;
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-sm);
}

/* mermaid 图容器 */
.zw-md .mermaid-block {
  margin: var(--zw-space-lg) 0;
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
  border: 1px dashed var(--zw-danger);
  padding: var(--zw-space-sm);
  border-radius: var(--zw-radius-xs);
}

/* 阅读器内的选中态与细滚动条（浏览器表面也属于设计） */
.reader-body ::selection,
.reader-nav ::selection {
  background: var(--zw-brand);
  color: var(--zw-bg-card);
}

.reader-nav .nav-scroll::-webkit-scrollbar,
.reader-toc::-webkit-scrollbar,
.reader-body::-webkit-scrollbar {
  width: 8px;
}

.reader-nav .nav-scroll::-webkit-scrollbar-thumb,
.reader-toc::-webkit-scrollbar-thumb,
.reader-body::-webkit-scrollbar-thumb {
  background: var(--zw-border);
  border-radius: var(--zw-radius-full);
}

.reader-nav .nav-scroll::-webkit-scrollbar-thumb:hover,
.reader-toc::-webkit-scrollbar-thumb:hover,
.reader-body::-webkit-scrollbar-thumb:hover {
  background: var(--zw-text-quaternary);
}

.reader-nav .nav-scroll::-webkit-scrollbar-track,
.reader-toc::-webkit-scrollbar-track,
.reader-body::-webkit-scrollbar-track {
  background: transparent;
}
</style>
