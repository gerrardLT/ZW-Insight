<template>
  <!-- 现代化滑块验证码：后端下发缺口位置，前端拖动对齐，服务端校验签发一次性令牌 -->
  <div class="slider-captcha" :class="{ ok: state === 'success', fail: state === 'fail' }">
    <div ref="trackRef" class="track" @pointerdown.self="noop">
      <!-- 缺口指示 -->
      <div class="gap" :style="{ left: gapLeft }"></div>
      <!-- 已滑过填充 -->
      <div class="fill" :style="{ width: pieceX + pieceSize / 2 + 'px' }"></div>
      <!-- 拖块 -->
      <div
        class="piece"
        :style="{ transform: `translateX(${pieceX}px)` }"
        @pointerdown="onDown"
      >
        <span class="arrow">›</span>
      </div>
      <span class="hint" v-if="pieceX === 0 && state !== 'success'">拖动滑块完成验证</span>
      <span class="state" v-if="state === 'success'">验证通过</span>
    </div>
    <button type="button" class="refresh" title="换一个" @click="load">⟳</button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getSliderCaptcha, verifySliderCaptcha } from '@/api/captcha'

const emit = defineEmits<{ (e: 'success', token: string): void; (e: 'fail'): void }>()

const trackRef = ref<HTMLElement | null>(null)
const pieceSize = 44
const pieceX = ref(0)
const gapPct = ref(0.6)
const challengeId = ref('')
const state = ref<'idle' | 'drag' | 'verify' | 'success' | 'fail'>('idle')
let startX = 0
let startPiece = 0

const gapLeft = computed(() => `calc(${(gapPct.value * 100).toFixed(2)}% - ${pieceSize / 2}px)`)
const noop = () => {}

async function load() {
  state.value = 'idle'
  pieceX.value = 0
  try {
    const res: any = await getSliderCaptcha()
    challengeId.value = res.data.challengeId
    gapPct.value = Number(res.data.gapPct)
  } catch {
    // 挑战获取失败：保持可重试（refresh 按钮）
  }
}

function onDown(e: PointerEvent) {
  if (state.value === 'success' || state.value === 'verify') return
  state.value = 'drag'
  startX = e.clientX
  startPiece = pieceX.value
  const move = (ev: PointerEvent) => {
    const w = (trackRef.value?.clientWidth ?? 0) - pieceSize
    let x = startPiece + (ev.clientX - startX)
    x = Math.max(0, Math.min(w, x))
    pieceX.value = x
  }
  const up = async () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    await verify()
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

async function verify() {
  const w = trackRef.value?.clientWidth ?? 1
  const pct = (pieceX.value + pieceSize / 2) / w
  state.value = 'verify'
  try {
    const res: any = await verifySliderCaptcha(challengeId.value, pct)
    state.value = 'success'
    emit('success', res.data.sliderToken)
  } catch {
    state.value = 'fail'
    emit('fail')
    // 失败自动换一道挑战并归位
    setTimeout(() => load(), 500)
  }
}

defineExpose({ reset: load })
onMounted(load)
</script>

<style scoped>
.slider-captcha { display: flex; gap: var(--zw-space-sm); align-items: center; width: 100%; }
.track {
  position: relative; flex: 1; height: 44px; border-radius: var(--zw-radius-sm);
  background: var(--zw-bg-hover); border: 1px solid var(--zw-border);
  overflow: hidden; user-select: none; touch-action: none;
  transition: border-color var(--zw-duration-fast), box-shadow var(--zw-duration-fast);
}
.slider-captcha.ok .track { border-color: var(--zw-success); box-shadow: 0 0 0 3px color-mix(in srgb, var(--zw-success) 16%, transparent); }
.slider-captcha.fail .track { border-color: var(--zw-danger); animation: shake .3s; }
@keyframes shake { 0%,100%{transform:translateX(0)} 25%{transform:translateX(-4px)} 75%{transform:translateX(4px)} }
.gap {
  position: absolute; top: 0; bottom: 0; width: 44px;
  background: color-mix(in srgb, var(--zw-text-tertiary) 14%, transparent);
  border-left: 1px dashed var(--zw-border-hover); border-right: 1px dashed var(--zw-border-hover);
}
.fill { position: absolute; left: 0; top: 0; bottom: 0; background: color-mix(in srgb, var(--zw-brand) 12%, transparent); }
.piece {
  position: absolute; left: 0; top: 0; width: 44px; height: 44px;
  display: flex; align-items: center; justify-content: center;
  background: var(--zw-brand); color: var(--zw-on-primary);
  border-radius: var(--zw-radius-sm); cursor: grab; font-size: var(--zw-font-size-lg); font-weight: 700;
  box-shadow: 0 2px 8px color-mix(in srgb, var(--zw-brand) 40%, transparent);
  transition: box-shadow var(--zw-duration-fast);
}
.piece:active { cursor: grabbing; }
.slider-captcha.ok .piece { background: var(--zw-success); color: var(--zw-text-inverse); }
.hint { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: var(--zw-font-size-sm); color: var(--zw-text-quaternary); pointer-events: none; }
.state { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: var(--zw-font-size-sm); font-weight: 600; color: var(--zw-success); pointer-events: none; }
.refresh {
  flex-shrink: 0; width: 44px; height: 44px; border-radius: var(--zw-radius-sm);
  border: 1px solid var(--zw-border); background: var(--zw-bg-card); color: var(--zw-text-tertiary);
  cursor: pointer; font-size: var(--zw-font-size-md); transition: color var(--zw-duration-fast), border-color var(--zw-duration-fast);
}
.refresh:hover { color: var(--zw-brand); border-color: var(--zw-brand); }
</style>
