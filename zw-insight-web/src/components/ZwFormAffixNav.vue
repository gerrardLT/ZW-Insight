<template>
  <nav class="zw-form-affix-nav card-corner-marked" aria-label="表单区域快速定位导航">
    <div class="nav-header">
      <span class="nav-eyebrow">BLUEPRINT // OUTLINE</span>
      <span class="nav-title">表单蓝图目录</span>
    </div>
    <div class="nav-track">
      <div
        v-for="(sec, idx) in sections"
        :key="sec.id"
        class="nav-item"
        :class="{
          active: activeId === sec.id,
          'has-error': sec.error,
          'is-completed': sec.completed
        }"
        @click="scrollTo(sec.id)"
      >
        <div class="nav-indicator">
          <span class="nav-index">{{ formatIndex(idx + 1) }}</span>
          <span class="nav-dot" :class="getDotClass(sec)"></span>
        </div>
        <div class="nav-text-wrap">
          <span class="nav-item-title">
            {{ sec.title }}
            <span v-if="sec.required" class="required-mark" title="包含必填项">*</span>
          </span>
          <span v-if="sec.subtitle" class="nav-item-sub">{{ sec.subtitle }}</span>
        </div>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'

export interface NavSection {
  id: string
  title: string
  subtitle?: string
  required?: boolean
  completed?: boolean
  error?: boolean
}

const props = withDefaults(defineProps<{
  sections: NavSection[]
  /** 滚动容器选择器，默认为 window */
  scrollContainer?: string
  /** 顶部偏移像素 */
  offset?: number
}>(), {
  sections: () => [],
  offset: 80
})

const activeId = ref<string>('')

function formatIndex(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

function getDotClass(sec: NavSection) {
  if (sec.error) return 'dot-error'
  if (sec.completed) return 'dot-completed'
  if (sec.required) return 'dot-required'
  return 'dot-normal'
}

function scrollTo(id: string) {
  activeId.value = id
  const target = document.getElementById(id)
  if (!target) return
  const rect = target.getBoundingClientRect()
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const targetY = rect.top + scrollTop - props.offset
  window.scrollTo({
    top: targetY,
    behavior: 'smooth'
  })
}

let scrollHandler: (() => void) | null = null

function updateActiveSection() {
  if (!props.sections.length) return
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  let currentActive = props.sections[0].id

  for (const sec of props.sections) {
    const el = document.getElementById(sec.id)
    if (el) {
      const top = el.getBoundingClientRect().top + scrollTop
      if (scrollTop >= top - props.offset - 20) {
        currentActive = sec.id
      }
    }
  }
  activeId.value = currentActive
}

onMounted(() => {
  if (props.sections.length > 0) {
    activeId.value = props.sections[0].id
  }
  scrollHandler = () => {
    updateActiveSection()
  }
  window.addEventListener('scroll', scrollHandler, { passive: true })
})

onBeforeUnmount(() => {
  if (scrollHandler) {
    window.removeEventListener('scroll', scrollHandler)
  }
})
</script>

<style scoped>
.zw-form-affix-nav {
  position: sticky;
  top: 80px;
  width: 180px;
  background: var(--zw-bg-card);
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-xs);
  padding: 12px 14px;
  box-sizing: border-box;
  user-select: none;
}

.nav-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--zw-border-light);
}

.nav-eyebrow {
  font-family: var(--zw-font-display);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.8px;
  color: var(--zw-brand);
}

.nav-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--zw-text-primary);
}

.nav-track {
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: relative;
}

/* 垂直导轨线 */
.nav-track::before {
  content: '';
  position: absolute;
  left: 20px;
  top: 8px;
  bottom: 8px;
  width: 1px;
  background: var(--zw-border-light);
  z-index: 0;
}

.nav-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--zw-radius-xs);
  transition: all var(--zw-transition-fast);
  position: relative;
  z-index: 1;
}

.nav-item:hover {
  background: var(--zw-bg-hover);
}

.nav-item.active {
  background: var(--zw-brand-lighter);
  border-left: 2px solid var(--zw-brand);
}

.nav-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-index {
  font-family: var(--zw-font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--zw-text-tertiary);
  width: 16px;
}

.nav-item.active .nav-index {
  color: var(--zw-brand);
}

.nav-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--zw-border-strong);
  flex-shrink: 0;
}

.dot-completed {
  background: var(--zw-success);
}

.dot-error {
  background: var(--zw-danger);
}

.dot-required {
  background: var(--zw-brand);
}

.nav-text-wrap {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.nav-item-title {
  font-size: 12px;
  color: var(--zw-text-secondary);
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

.nav-item.active .nav-item-title {
  color: var(--zw-text-primary);
  font-weight: 600;
}

.required-mark {
  color: var(--zw-danger);
  font-weight: bold;
  margin-left: 2px;
}

.nav-item-sub {
  font-size: 10px;
  color: var(--zw-text-quaternary);
  font-family: var(--zw-font-mono);
}
</style>
