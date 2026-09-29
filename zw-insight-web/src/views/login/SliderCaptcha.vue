<template>
  <div ref="rootRef" class="captcha-shell">
    <button
      type="button"
      class="captcha-trigger"
      :class="{ verified: state === 'success' }"
      :aria-expanded="panelOpen"
      aria-haspopup="dialog"
      @click="openPanel"
    >
      <span class="trigger-icon" aria-hidden="true">
        <svg v-if="state === 'success'" viewBox="0 0 24 24"><path d="M5 12.5l4 4L19 6.5" /></svg>
        <svg v-else viewBox="0 0 24 24"><path d="M12 3l7 3v5c0 4.6-2.8 8-7 10-4.2-2-7-5.4-7-10V6l7-3z" /><path d="M9 12l2 2 4-4" /></svg>
      </span>
      <span>{{ state === 'success' ? '安全验证已完成' : '点击完成安全验证' }}</span>
      <span v-if="state !== 'success'" class="trigger-action">验证</span>
    </button>

    <Transition name="captcha-panel">
      <div v-if="panelOpen" class="captcha-panel" role="dialog" aria-label="图片拼图验证">
        <header>
          <div>
            <strong>完成拼图验证</strong>
            <span>拖动滑块，将拼图移动到缺口位置</span>
          </div>
          <button type="button" class="icon-button" aria-label="关闭验证" @click="closePanel">×</button>
        </header>

        <div class="image-stage" :style="stageStyle">
          <div v-if="loading" class="stage-state">正在生成安全图片…</div>
          <div v-else-if="loadError" class="stage-state error-state">
            <span>{{ loadError }}</span>
            <button type="button" @click="load">重新加载</button>
          </div>
          <template v-else-if="challenge">
            <img class="background-image" :src="imageSrc(challenge.backgroundImage)" alt="拼图验证背景" draggable="false" />
            <img
              class="puzzle-piece"
              :src="imageSrc(challenge.pieceImage)"
              alt=""
              draggable="false"
              :style="pieceStyle"
            />
          </template>
        </div>

        <div ref="trackRef" class="slider-track" :class="{ dragging: state === 'drag', fail: state === 'fail' }">
          <div class="slider-fill" :style="{ width: `${pieceX + HANDLE / 2}px` }" />
          <span class="slider-copy">{{ state === 'verify' ? '正在验证…' : '按住滑块，拖动完成拼图' }}</span>
          <button
            class="slider-handle"
            type="button"
            :disabled="loading || !!loadError || state === 'verify'"
            :style="{ transform: `translateX(${pieceX}px)` }"
            aria-label="拖动拼图"
            @pointerdown="onDown"
            @keydown.left.prevent="nudge(-4)"
            @keydown.right.prevent="nudge(4)"
            @keydown.enter.prevent="verify"
          >
            <svg viewBox="0 0 24 24"><path d="M7 8l4 4-4 4M13 8l4 4-4 4" /></svg>
          </button>
        </div>

        <footer>
          <span v-if="state === 'fail'" class="failure-copy">位置不正确，请重试</span>
          <span v-else>验证将在 2 分钟后失效</span>
          <button type="button" class="refresh-button" :disabled="loading" @click="load">换一张</button>
        </footer>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { getSliderCaptcha, verifySliderCaptcha, type SliderCaptchaChallenge } from '@/api/captcha'

const emit = defineEmits<{ success: [token: string]; fail: [] }>()
const HANDLE = 46
const rootRef = ref<HTMLElement | null>(null)
const trackRef = ref<HTMLElement | null>(null)
const panelOpen = ref(false)
const loading = ref(false)
const loadError = ref('')
const challenge = ref<SliderCaptchaChallenge | null>(null)
const pieceX = ref(0)
const state = ref<'idle' | 'drag' | 'verify' | 'success' | 'fail'>('idle')
let startClientX = 0
let startPieceX = 0

const displayMaxX = computed(() => Math.max(0, (trackRef.value?.clientWidth || challenge.value?.imageWidth || 0) - HANDLE))
const stageStyle = computed(() => challenge.value ? { aspectRatio: `${challenge.value.imageWidth} / ${challenge.value.imageHeight}` } : undefined)
const pieceStyle = computed(() => ({
  width: `${challenge.value?.pieceWidth || HANDLE}px`,
  top: `${challenge.value?.pieceY || 0}px`,
  transform: `translateX(${pieceX.value}px)`,
}))

function imageSrc(value: string) {
  return value.startsWith('data:') ? value : `data:image/png;base64,${value}`
}

async function openPanel() {
  if (state.value === 'success') return
  panelOpen.value = true
  if (!challenge.value) await load()
}

function closePanel() {
  if (state.value !== 'verify') panelOpen.value = false
}

async function load() {
  loading.value = true
  loadError.value = ''
  challenge.value = null
  pieceX.value = 0
  state.value = 'idle'
  try {
    const res = await getSliderCaptcha()
    challenge.value = res.data
  } catch {
    loadError.value = '图片加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function onDown(event: PointerEvent) {
  if (!challenge.value || state.value === 'verify') return
  state.value = 'drag'
  startClientX = event.clientX
  startPieceX = pieceX.value
  const move = (e: PointerEvent) => { pieceX.value = Math.max(0, Math.min(displayMaxX.value, startPieceX + e.clientX - startClientX)) }
  const up = async () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    await verify()
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up, { once: true })
}

function nudge(delta: number) {
  if (!challenge.value) return
  pieceX.value = Math.max(0, Math.min(displayMaxX.value, pieceX.value + delta))
}

async function verify() {
  if (!challenge.value || state.value === 'verify') return
  state.value = 'verify'
  try {
    const sourceMaxX = challenge.value.imageWidth - challenge.value.pieceWidth
    const sourceX = displayMaxX.value > 0 ? pieceX.value / displayMaxX.value * sourceMaxX : 0
    const res = await verifySliderCaptcha(challenge.value.challengeId, sourceX)
    state.value = 'success'
    emit('success', res.data.sliderToken)
    await nextTick()
    setTimeout(() => { panelOpen.value = false }, 320)
  } catch {
    state.value = 'fail'
    emit('fail')
    setTimeout(load, 650)
  }
}

function onDocumentPointerDown(event: PointerEvent) {
  if (panelOpen.value && !rootRef.value?.contains(event.target as Node)) closePanel()
}
document.addEventListener('pointerdown', onDocumentPointerDown)
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointerDown))

defineExpose({ reset: () => { challenge.value = null; panelOpen.value = false; state.value = 'idle'; pieceX.value = 0 } })
</script>

<style scoped>
.captcha-shell { position: relative; width: 100%; }
.captcha-trigger { width: 100%; min-height: 48px; display: flex; align-items: center; gap: 10px; padding: 0 12px; border: 1px solid var(--zw-border); border-radius: var(--zw-radius-md); background: var(--zw-bg-card); color: var(--zw-text-secondary); font: inherit; cursor: pointer; transition: border-color var(--zw-duration-fast), background var(--zw-duration-fast); }
.captcha-trigger:hover { border-color: var(--zw-brand); background: var(--zw-bg-hover); }
.captcha-trigger.verified { border-color: color-mix(in srgb, var(--zw-success) 55%, var(--zw-border)); color: var(--zw-success); }
.trigger-icon { width: 24px; height: 24px; color: var(--zw-brand); }
.trigger-icon svg, .slider-handle svg { width: 100%; height: 100%; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.trigger-action { margin-inline-start: auto; color: var(--zw-brand); font-weight: var(--zw-font-weight-semibold); }
.captcha-panel { position: absolute; inset-inline: 0; top: calc(100% + 8px); z-index: var(--zw-z-popover); padding: 14px; border: 1px solid var(--zw-border); border-radius: var(--zw-radius-lg); background: var(--zw-bg-elevated); box-shadow: var(--zw-shadow-overlay); }
.captcha-panel header, .captcha-panel footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.captcha-panel header { margin-bottom: 12px; }
.captcha-panel header div { display: flex; flex-direction: column; gap: 2px; }
.captcha-panel header strong { color: var(--zw-text-primary); font-size: var(--zw-font-size-md); }
.captcha-panel header span, .captcha-panel footer { color: var(--zw-text-tertiary); font-size: var(--zw-font-size-xs); }
.icon-button, .refresh-button { border: 0; background: transparent; color: var(--zw-text-secondary); cursor: pointer; }
.icon-button { width: 32px; height: 32px; font-size: 22px; }
.refresh-button { color: var(--zw-brand); font-weight: var(--zw-font-weight-semibold); }
.image-stage { position: relative; width: 100%; min-height: 156px; overflow: hidden; border-radius: var(--zw-radius-md); background: var(--zw-bg-surface-3); }
.background-image { width: 100%; height: 100%; position: absolute; inset: 0; object-fit: fill; user-select: none; }
.puzzle-piece { position: absolute; inset-inline-start: 0; filter: drop-shadow(0 3px 5px rgba(0,0,0,.35)); transition: transform 30ms linear; user-select: none; }
.stage-state { position: absolute; inset: 0; display: grid; place-content: center; gap: 8px; color: var(--zw-text-tertiary); font-size: var(--zw-font-size-sm); text-align: center; }
.error-state button { border: 1px solid var(--zw-border); background: var(--zw-bg-card); color: var(--zw-brand); padding: 6px 10px; cursor: pointer; }
.slider-track { position: relative; height: 46px; margin: 12px 0 10px; overflow: hidden; border: 1px solid var(--zw-border); border-radius: var(--zw-radius-md); background: var(--zw-bg-hover); }
.slider-fill { position: absolute; inset-block: 0; inset-inline-start: 0; background: color-mix(in srgb, var(--zw-brand) 16%, transparent); }
.slider-copy { position: absolute; inset: 0; display: grid; place-items: center; color: var(--zw-text-tertiary); font-size: var(--zw-font-size-sm); pointer-events: none; }
.slider-handle { position: absolute; inset-block-start: -1px; inset-inline-start: -1px; width: 46px; height: 46px; display: grid; place-items: center; padding: 12px; border: 1px solid var(--zw-brand); border-radius: var(--zw-radius-md); background: var(--zw-bg-card); color: var(--zw-brand); cursor: grab; touch-action: none; }
.slider-handle:disabled { cursor: wait; opacity: .7; }
.dragging .slider-handle { cursor: grabbing; }
.fail { border-color: var(--zw-danger); animation: captcha-shake .3s; }
.failure-copy { color: var(--zw-danger); }
.captcha-trigger:focus-visible, .icon-button:focus-visible, .refresh-button:focus-visible, .slider-handle:focus-visible { outline: 2px solid var(--zw-brand); outline-offset: 2px; }
.captcha-panel-enter-active { transition: opacity var(--zw-duration-fast) var(--zw-ease-out), transform var(--zw-duration-fast) var(--zw-ease-out); }
.captcha-panel-leave-active { transition: opacity var(--zw-duration-exit-fast) var(--zw-ease-in), transform var(--zw-duration-exit-fast) var(--zw-ease-in); }
.captcha-panel-enter-from, .captcha-panel-leave-to { opacity: 0; transform: translateY(-6px); }
@keyframes captcha-shake { 25% { transform: translateX(-4px); } 55% { transform: translateX(3px); } 80% { transform: translateX(-2px); } }
@media (max-width: 640px) { .captcha-panel { position: fixed; inset: auto 12px max(12px, env(safe-area-inset-bottom)); } }
@media (prefers-reduced-motion: reduce) { .captcha-panel-enter-active, .captcha-panel-leave-active, .puzzle-piece { transition: none; } }
</style>
