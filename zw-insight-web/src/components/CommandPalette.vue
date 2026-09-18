<template>
  <Teleport to="body">
    <transition name="zw-palette">
      <div v-if="visible" class="palette-overlay" role="presentation">
        <div class="palette-backdrop" @mousedown="close" />
        <div
          class="palette-panel"
          role="dialog"
          aria-modal="true"
          aria-label="命令面板"
        >
          <div class="palette-input-row">
            <el-icon class="palette-search-icon"><Search /></el-icon>
            <input
              ref="inputRef"
              v-model="query"
              class="palette-input"
              type="text"
              placeholder="搜索页面或命令…"
              role="combobox"
              aria-expanded="true"
              aria-controls="palette-listbox"
              aria-autocomplete="list"
              autocomplete="off"
              spellcheck="false"
              @keydown.down.prevent="move(1)"
              @keydown.up.prevent="move(-1)"
              @keydown.enter.prevent="runSelected"
              @keydown.esc.prevent="close"
            />
            <span class="palette-ai-note" title="AI 助手即将上线，届时可在此直接追问">AI 追问 · 即将上线</span>
          </div>

          <ul
            id="palette-listbox"
            class="palette-list"
            role="listbox"
            :aria-activedescendant="activeId"
          >
            <template v-for="(item, i) in results" :key="item.id">
              <li v-if="i === 0 || results[i - 1].group !== item.group" class="palette-group-label">
                {{ item.group }}
              </li>
              <li
                :id="'zw-palette-opt-' + item.id"
                class="palette-item"
                :class="{ active: i === selectedIndex }"
                role="option"
                :aria-selected="i === selectedIndex"
                @mouseenter="selectedIndex = i"
                @click="run(item)"
              >
                <el-icon v-if="item.icon" class="palette-item-icon"><component :is="item.icon" /></el-icon>
                <span class="palette-item-title">{{ item.title }}</span>
                <span v-if="item.hint" class="palette-item-hint">{{ item.hint }}</span>
              </li>
            </template>
            <li v-if="results.length === 0" class="palette-empty">无匹配结果</li>
          </ul>

          <div class="palette-footer">
            <span><kbd>↑</kbd><kbd>↓</kbd> 导航</span>
            <span><kbd>Enter</kbd> 执行</span>
            <span><kbd>Esc</kbd> 关闭</span>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Search } from '@/components/icons/registry'
import {
  useCommandPalette,
  fuzzyScore,
  type PaletteCommand
} from '@/composables/useCommandPalette'

const props = defineProps<{ commands: PaletteCommand[] }>()

const { visible, close } = useCommandPalette()
const inputRef = ref<HTMLInputElement>()
const query = ref('')
const selectedIndex = ref(0)

/** 渲染上限：命令量小，封顶保证 DOM 轻量（替代重量级虚拟滚动） */
const MAX_RESULTS = 60

interface ScoredCommand extends PaletteCommand {
  _score: number
}

const results = computed<ScoredCommand[]>(() => {
  const q = query.value
  const scored = props.commands.map((c) => ({
    ...c,
    _score: Math.max(fuzzyScore(c.title, q), fuzzyScore(c.keywords || '', q))
  }))
  const list = q.trim() ? scored.filter((c) => c._score > 0) : scored
  if (q.trim()) list.sort((a, b) => b._score - a._score)
  return list.slice(0, MAX_RESULTS)
})

const activeId = computed(() => {
  const cur = results.value[selectedIndex.value]
  return cur ? 'zw-palette-opt-' + cur.id : undefined
})

function move(delta: number) {
  const n = results.value.length
  if (!n) return
  selectedIndex.value = (selectedIndex.value + delta + n) % n
}

function run(cmd: PaletteCommand) {
  cmd.run()
  close()
}

function runSelected() {
  const cmd = results.value[selectedIndex.value]
  if (cmd) run(cmd)
}

// 打开时重置查询与选中，聚焦输入框
watch(visible, async (v) => {
  if (!v) return
  query.value = ''
  selectedIndex.value = 0
  await nextTick()
  inputRef.value?.focus()
})

// 查询变化后选中态归零；结果收缩时夹紧选中索引
watch(query, () => { selectedIndex.value = 0 })
watch(results, (list) => {
  if (selectedIndex.value >= list.length) selectedIndex.value = 0
})
</script>

<style scoped>
.palette-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--zw-z-modal);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 12vh;
}

.palette-backdrop {
  position: absolute;
  inset: 0;
  background: var(--zw-bg-mask);
}

.palette-panel {
  position: relative;
  width: min(640px, 92vw);
  display: flex;
  flex-direction: column;
  background: var(--zw-bg-elevated);
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-lg);
  box-shadow: var(--zw-shadow-overlay);
  overflow: hidden;
}

.palette-input-row {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm-md);
  padding: var(--zw-space-md) var(--zw-space-lg);
  border-bottom: 1px solid var(--zw-border-light);
}

.palette-search-icon {
  font-size: var(--zw-font-size-xl);
  color: var(--zw-text-tertiary);
  flex-shrink: 0;
}

.palette-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--zw-font-size-lg);
  color: var(--zw-text-primary);
  font-family: inherit;
}

.palette-input::placeholder {
  color: var(--zw-text-quaternary);
}

/* AI 追问入口占位：Phase 1 不接线，纯提示（不伪装成可点按钮） */
.palette-ai-note {
  flex-shrink: 0;
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-quaternary);
  letter-spacing: 0.02em;
}

.palette-list {
  list-style: none;
  margin: 0;
  padding: var(--zw-space-sm) 0;
  max-height: 52vh;
  overflow-y: auto;
}

.palette-group-label {
  padding: var(--zw-space-sm) var(--zw-space-lg);
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-sm);
  font-weight: var(--zw-font-weight-semibold);
  letter-spacing: 1px;
  text-transform: uppercase;
  color: var(--zw-text-quaternary);
}

.palette-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm-md);
  padding: var(--zw-space-sm) var(--zw-space-lg);
  min-height: 40px;
  cursor: pointer;
  color: var(--zw-text-primary);
}

.palette-item.active {
  background: var(--zw-bg-active);
}

.palette-item.active::before {
  content: '';
  position: absolute;
  margin-left: calc(var(--zw-space-lg) * -1 + 2px);
  width: 2px;
  height: 20px;
  background: var(--zw-brand);
}

.palette-item-icon {
  font-size: var(--zw-font-size-lg);
  color: var(--zw-text-secondary);
  flex-shrink: 0;
}

.palette-item.active .palette-item-icon {
  color: var(--zw-brand);
}

.palette-item-title {
  flex: 1;
  font-size: var(--zw-font-size-md);
}

.palette-item-hint {
  font-family: var(--zw-font-mono);
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-quaternary);
}

.palette-empty {
  padding: var(--zw-space-xl);
  text-align: center;
  color: var(--zw-text-tertiary);
  font-size: var(--zw-font-size-md);
}

.palette-footer {
  display: flex;
  gap: var(--zw-space-lg);
  padding: var(--zw-space-sm) var(--zw-space-lg);
  border-top: 1px solid var(--zw-border-light);
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-quaternary);
}

.palette-footer kbd {
  font-family: var(--zw-font-mono);
  font-size: var(--zw-font-size-xs);
  padding: 1px 6px;
  margin-right: 4px;
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-sm);
  background: var(--zw-bg-hover);
  color: var(--zw-text-secondary);
}

/* 进出场动效（DESIGN 语义组合：入场 ease-out，退场 = 入场×70% + ease-in 加速离开） */
.zw-palette-enter-active {
  transition: opacity var(--zw-duration-fast) var(--zw-ease-out);
}
.zw-palette-leave-active {
  transition: opacity var(--zw-duration-exit-fast) var(--zw-ease-in);
}
.zw-palette-enter-active .palette-panel {
  transition: transform var(--zw-duration-base) var(--zw-ease-out);
}
.zw-palette-leave-active .palette-panel {
  transition: transform var(--zw-duration-exit-dropdown) var(--zw-ease-in);
}
.zw-palette-enter-from,
.zw-palette-leave-to {
  opacity: 0;
}
.zw-palette-enter-from .palette-panel,
.zw-palette-leave-to .palette-panel {
  transform: translateY(-8px);
}

@media (prefers-reduced-motion: reduce) {
  .zw-palette-enter-active,
  .zw-palette-leave-active,
  .zw-palette-enter-active .palette-panel,
  .zw-palette-leave-active .palette-panel {
    transition: none;
  }
  .zw-palette-enter-from .palette-panel,
  .zw-palette-leave-to .palette-panel {
    transform: none;
  }
}
</style>
