<template>
  <el-drawer
    :model-value="guideStore.quickVisible"
    direction="rtl"
    size="52%"
    class="guide-quick-drawer"
    append-to-body
    data-testid="guide-quick-drawer"
    @update:model-value="onVisibleChange"
    @opened="renderMermaid"
  >
    <template #header>
      <div class="gqd-header">
        <div class="gqd-title">
          <el-icon class="gqd-title-icon"><IconBook /></el-icon>
          <span>核心链路一册通</span>
          <span class="gqd-sub">10 分钟速览</span>
        </div>
        <el-button link type="primary" data-testid="guide-quick-full" @click="goFullGuide">
          完整帮助文档
          <el-icon class="gqd-arrow"><IconArrowRight /></el-icon>
        </el-button>
      </div>
      <!-- 本章小节导航（sticky，点击平滑滚动到对应 h2 锚点） -->
      <div v-if="sections.length" class="gqd-toc" data-testid="guide-quick-toc">
        <button
          v-for="s in sections"
          :key="s.id"
          type="button"
          class="gqd-toc-item"
          @click="scrollTo(s.id)"
        >{{ s.title }}</button>
      </div>
    </template>
    <div ref="bodyRef" class="zw-md gqd-body" data-testid="guide-quick-body" v-html="docHtml"></div>
  </el-drawer>
</template>

<script setup lang="ts">
/**
 * 核心链路速览抽屉（顶栏「?」/ 首页引导条唤起）。
 *
 * 内容 = 帮助中心「core-chains」章（src/docs/help/core-chains.md），与 /help/guide
 * 完整文档同源——不存在第二份正文。渲染复用 docs/help/markdown.ts（mermaid 块
 * 输出占位 div），出图逻辑与 guide.vue 一致：懒加载 mermaid、按明暗主题重渲染、
 * 失败保留纯文本兜底不静默。抽屉样式为 guide.vue .zw-md 的紧凑子集（同一套
 * --zw-* 主题变量，明暗自适应）。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { IconBook, IconArrowRight } from '@tabler/icons-vue'
import { useAppStore } from '@/stores/app'
import { useGuideStore } from '@/stores/guide'
import { getChapter } from '@/docs/help/registry'
import { renderChapter } from '@/docs/help/markdown'

const router = useRouter()
const appStore = useAppStore()
const guideStore = useGuideStore()

const CORE_CHAINS_ID = 'core-chains'
const chapter = getChapter(CORE_CHAINS_ID)
const { html: docHtml, sections } = chapter
  ? renderChapter(chapter.body)
  : { html: '<p>未找到 core-chains 章节</p>', sections: [] as { id: string; title: string }[] }

const bodyRef = ref<HTMLElement | null>(null)

function onVisibleChange(v: boolean | string | number) {
  if (v) guideStore.openQuick()
  else guideStore.closeQuick()
}

function goFullGuide() {
  guideStore.closeQuick()
  router.push(`/help/guide#${CORE_CHAINS_ID}`)
}

function scrollTo(id: string) {
  const root = bodyRef.value
  const target = root?.querySelector(`#${id}`) as HTMLElement | null
  if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/* ================= mermaid 出图（与 guide.vue 同一套约束） ================= */
let mermaidMod: any = null
let mmdSeq = 0
let renderScheduled = false

async function ensureMermaid() {
  if (!mermaidMod) {
    const m = await import('mermaid')
    mermaidMod = (m as any).default || m
  }
  return mermaidMod
}

/** 幂等出图：只处理未标记的块，防主题重渲染/重复 open 时重复渲染 */
async function renderMermaid() {
  const root = bodyRef.value
  if (!root || renderScheduled) return
  const blocks = Array.prototype.slice.call(
    root.querySelectorAll('.zw-md .mermaid-block[data-src]:not([data-done])')
  ) as HTMLElement[]
  if (!blocks.length) return
  renderScheduled = true
  let mermaid: any
  try {
    mermaid = await ensureMermaid()
  } catch (e) {
    renderScheduled = false
    console.warn('[guide-quick] mermaid 加载失败，流程图以纯文本兜底显示：', e)
    return
  }
  mermaid.initialize({
    startOnLoad: false,
    theme: appStore.isDark ? 'dark' : 'neutral',
    securityLevel: 'strict',
    suppressErrorRendering: true,
  })
  for (const el of blocks) {
    const src = decodeURIComponent(el.getAttribute('data-src') || '')
    try {
      const { svg } = await mermaid.render('gqd-mmd-' + mmdSeq++, src)
      el.innerHTML = svg
      el.classList.add('mermaid-ok')
    } catch {
      el.classList.add('mermaid-error')
    }
    el.setAttribute('data-done', '1')
  }
  renderScheduled = false
}

/** 主题切换重出图：清标记后下一拍重渲染（抽屉关闭时 DOM 仍在，恢复可见即正确主题） */
async function rerenderOnTheme() {
  const root = bodyRef.value
  if (!root) return
  root.querySelectorAll('.zw-md .mermaid-block[data-done]').forEach((el) => {
    el.removeAttribute('data-done')
    el.classList.remove('mermaid-ok', 'mermaid-error')
  })
  await nextTick()
  if (guideStore.quickVisible) await renderMermaid()
}

const stopThemeWatch = watch(() => appStore.isDark, rerenderOnTheme)
/** 首次打开抽屉时 DOM 才出现，opened 后再出图；兜底再 watch 一次可见性 */
const stopVisibleWatch = watch(
  () => guideStore.quickVisible,
  (v) => {
    if (v) nextTick(() => renderMermaid())
  }
)

onMounted(() => {
  if (guideStore.quickVisible) nextTick(() => renderMermaid())
})
onBeforeUnmount(() => {
  stopThemeWatch()
  stopVisibleWatch()
})
</script>

<style scoped>
.gqd-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-md);
  width: 100%;
}

.gqd-title {
  display: flex;
  align-items: baseline;
  gap: var(--zw-space-xs);
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-lg);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}

.gqd-title-icon {
  font-size: var(--zw-font-size-md);
  color: var(--zw-brand);
  align-self: center;
}

.gqd-sub {
  font-size: var(--zw-font-size-xs);
  font-weight: var(--zw-font-weight-regular);
  color: var(--zw-text-quaternary);
}

.gqd-arrow {
  margin-left: 2px;
}

.gqd-toc {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zw-space-2xs);
  margin-top: var(--zw-space-sm);
  width: 100%;
}

.gqd-toc-item {
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-full, 999px);
  background: var(--zw-bg-page);
  color: var(--zw-text-secondary);
  font-size: var(--zw-font-size-xs);
  padding: 2px 10px;
  cursor: pointer;
}

.gqd-toc-item:hover {
  color: var(--zw-brand);
  border-color: var(--zw-brand);
}

.gqd-body {
  padding: 0 var(--zw-space-md) var(--zw-space-xl);
}

/* ===== markdown 正文样式（guide.vue .zw-md 的紧凑子集，同一套 --zw-* 主题变量） ===== */
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

.zw-md p,
.zw-md ol,
.zw-md ul {
  margin: var(--zw-space-md) 0;
}

.zw-md ol,
.zw-md ul {
  padding-left: var(--zw-space-lg);
}

.zw-md ol {
  list-style: decimal;
}

.zw-md ul {
  list-style: square;
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

.zw-md blockquote {
  margin: var(--zw-space-md) 0;
  padding: var(--zw-space-sm) var(--zw-space-md);
  border-left: 3px solid var(--zw-brand);
  background: var(--zw-bg-page);
  border-radius: var(--zw-radius-xs);
  color: var(--zw-text-secondary);
}

/* 宽表横向滚动（链路卡片表在抽屉内放不下整列） */
.zw-md table {
  display: block;
  width: max-content;
  min-width: 100%;
  max-width: 100%;
  overflow-x: auto;
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
  white-space: nowrap;
}

/* mermaid 出图容器（占位 markup 由 docs/help/markdown.ts fence 规则生成） */
.zw-md :deep(.mermaid-block) {
  margin: var(--zw-space-lg) 0;
  padding: var(--zw-space-md);
  border: 1px dashed var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  text-align: center;
  overflow-x: auto;
}

.zw-md :deep(.mermaid-block svg) {
  max-width: 100%;
  height: auto;
}

.zw-md :deep(.mermaid-error) {
  color: var(--zw-text-quaternary);
  font-family: var(--zw-font-mono);
  font-size: var(--zw-font-size-xs);
  text-align: left;
}

@media (max-width: 767px) {
  .guide-quick-drawer :deep(.el-drawer) {
    width: 100% !important;
  }
}
</style>
