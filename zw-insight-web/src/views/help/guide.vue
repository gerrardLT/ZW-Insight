<template>
  <div class="guide-container">
    <!-- 顶部：标题 + 章节内检索 -->
    <el-card shadow="never" class="guide-card" data-testid="guide-header">
      <div class="card-header">
        <span class="card-title">系统使用文档</span>
        <span class="card-sub">按业务模块组织的操作指南；可用命令面板（Ctrl+K）输入关键词直达任意章节</span>
      </div>
      <el-input
        v-model="keyword"
        class="guide-search"
        placeholder="在本页检索章节标题 / 步骤 / 规则…"
        clearable
        data-testid="guide-search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <!-- 目录（锚点） -->
      <div class="guide-toc" data-testid="guide-toc">
        <el-tag
          v-for="ch in filtered"
          :key="ch.id"
          class="toc-tag"
          effect="plain"
          :data-testid="'guide-toc-' + ch.id"
          @click="jumpTo(ch.id)"
        >
          {{ ch.title }}
        </el-tag>
        <span v-if="!filtered.length" class="toc-empty">无匹配章节</span>
      </div>
    </el-card>

    <!-- 逐章渲染 -->
    <el-card
      v-for="ch in filtered"
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

      <div class="chapter-block">
        <div class="block-label">操作步骤</div>
        <ol class="step-list">
          <li v-for="(s, i) in ch.steps" :key="i" class="step-item">{{ s }}</li>
        </ol>
      </div>

      <div class="chapter-block">
        <div class="block-label">关键校验与规则</div>
        <ul class="rule-list">
          <li v-for="(r, i) in ch.rules" :key="i" class="rule-item">{{ r }}</li>
        </ul>
      </div>

      <div v-if="ch.faq?.length" class="chapter-block">
        <div class="block-label">常见问题</div>
        <div v-for="(f, i) in ch.faq" :key="i" class="faq-item" :data-testid="'guide-faq-' + ch.id + '-' + i">
          <div class="faq-q">Q：{{ f.q }}</div>
          <div class="faq-a">A：{{ f.a }}</div>
        </div>
      </div>
    </el-card>

    <el-card v-if="!filtered.length" shadow="never" class="guide-card">
      <el-empty description="没有匹配的章节，换个关键词试试" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 系统使用文档页（/help/guide）。
 * 内容来自 src/constants/user-guide.ts（静态产品文档，锚定真实路由与后端校验规则）。
 * 支持：① 本页关键词检索（标题/步骤/规则全文匹配）；② 锚点定位
 * （命令面板跳转 /help/guide#<chapterId> 时滚动到对应章节）。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from '@/components/icons/registry'
import { USER_GUIDE, type GuideChapter } from '@/constants/user-guide'

const route = useRoute()
const keyword = ref('')

/** 全文检索：标题 / 模块 / 步骤 / 规则 / FAQ 任一命中即保留 */
const filtered = computed<GuideChapter[]>(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return USER_GUIDE
  return USER_GUIDE.filter((ch) => {
    const hay = [
      ch.title,
      ch.module,
      ch.summary,
      ch.keywords,
      ...ch.steps,
      ...ch.rules,
      ...(ch.faq || []).flatMap((f) => [f.q, f.a])
    ]
      .join('\n')
      .toLowerCase()
    return hay.includes(q)
  })
})

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

onMounted(scrollToHash)
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

.chapter-block {
  margin-top: var(--zw-space-md);
}

.block-label {
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-sm);
  font-weight: var(--zw-font-weight-semibold);
  letter-spacing: 1px;
  color: var(--zw-text-quaternary);
  margin-bottom: var(--zw-space-xs);
}

.step-list {
  margin: 0;
  padding-left: var(--zw-space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
}

.step-item {
  font-size: var(--zw-font-size-sm);
  line-height: var(--zw-line-height-relaxed);
  color: var(--zw-text-secondary);
}

.rule-list {
  margin: 0;
  padding-left: var(--zw-space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
  list-style: square;
}

.rule-item {
  font-size: var(--zw-font-size-sm);
  line-height: var(--zw-line-height-relaxed);
  color: var(--zw-text-secondary);
}

.faq-item {
  padding: var(--zw-space-sm) 0;
  border-bottom: 1px solid var(--zw-border-light);
}

.faq-item:last-child {
  border-bottom: none;
}

.faq-q {
  font-size: var(--zw-font-size-sm);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}

.faq-a {
  margin-top: var(--zw-space-2xs);
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-secondary);
}
</style>
