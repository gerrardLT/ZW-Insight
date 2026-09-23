<template>
  <!-- 精致版滑块验证码：后端下发缺口位置，前端拖动对齐，服务端校验签发一次性令牌 -->
  <div class="slider-captcha" :class="{ ok: state === 'success', fail: state === 'fail', dragging: state === 'drag' }">
    <div ref="trackRef" class="track" @pointerdown.self="noop">
      <!-- 目标缺口槽 -->
      <div class="gap" :style="{ left: gapLeft }">
        <svg class="gap-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
          <path d="M12 3l3 3h4a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l3-3z" />
        </svg>
      </div>
      <!-- 已滑过填充（品牌渐变 + 流动高光） -->
      <div class="fill" :style="{ width: fillWidth }"><i class="sheen"></i></div>
      <!-- 拖块 -->
      <div class="handle" :style="{ transform: `translateX(${pieceX}px)` }" @pointerdown="onDown">
        <svg v-if="state === 'success'" class="h-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12l5 5L20 6" /></svg>
        <svg v-else class="h-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 7l5 5-5 5" /><path d="M14 7l5 5-5 5" /></svg>
      </div>
      <span class="hint" v-if="showHint">拖动滑块完成验证</span>
      <span class="ok-label" v-if="state === 'success'">验证通过</span>
    </div>
    <button type="button" class="refresh" title="换一个" @click="load">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7" /><path d="M21 3v6h-6" /></svg>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getSliderCaptcha, verifySliderCaptcha } from '@/api/captcha'

const emit = defineEmits<{ (e: 'success', token: string): void; (e: 'fail'): void }>()

const trackRef = ref<HTMLElement | null>(null)
const HANDLE = 46
const pieceX = ref(0)
const gapPct = ref(0.6)
const challengeId = ref('')
const state = ref<'idle' | 'drag' | 'verify' | 'success' | 'fail'>('idle')
let startX = 0
let startPiece = 0

const gapLeft = computed(() => `calc(${(gapPct.value * 100).toFixed(2)}% - ${HANDLE / 2}px)`)
const fillWidth = computed(() => `${pieceX.value + HANDLE / 2}px`)
const showHint = computed(() => pieceX.value === 0 && state.value !== 'success' && state.value !== 'drag')
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
    const w = (trackRef.value?.clientWidth ?? 0) - HANDLE
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
  const pct = (pieceX.value + HANDLE / 2) / w
  state.value = 'verify'
  try {
    const res: any = await verifySliderCaptcha(challengeId.value, pct)
    state.value = 'success'
    emit('success', res.data.sliderToken)
  } catch {
    state.value = 'fail'
    emit('fail')
    setTimeout(() => load(), 550)
  }
}

defineExpose({ reset: load })
onMounted(load)
</script>

<style scoped>
.slider-captcha { display: flex; gap: var(--zw-space-sm); align-items: center; width: 100%; }

/* 轨道：内凹质感 + 细边 */
.track {
  position: relative; flex: 1; height: 48px; border-radius: var(--zw-radius-lg);
  background: linear-gradient(180deg, color-mix(in srgb, var(--zw-bg-hover) 70%, transparent), color-mix(in srgb, var(--zw-bg-card) 60%, transparent));
  border: 1px solid var(--zw-border);
  box-shadow: inset 0 2px 6px color-mix(in srgb, var(--zw-bg-sidebar) 8%, transparent);
  overflow: hidden; user-select: none; touch-action: none;
  transition: border-color var(--zw-duration-fast), box-shadow var(--zw-duration-fast);
}
.slider-captcha.dragging .track { border-color: color-mix(in srgb, var(--zw-brand) 45%, var(--zw-border)); }
.slider-captcha.ok .track { border-color: color-mix(in srgb, var(--zw-success) 55%, var(--zw-border)); box-shadow: inset 0 2px 6px color-mix(in srgb, var(--zw-success) 10%, transparent), 0 0 0 3px color-mix(in srgb, var(--zw-success) 14%, transparent); }
.slider-captcha.fail .track { border-color: color-mix(in srgb, var(--zw-danger) 55%, var(--zw-border)); animation: shake .32s; }
@keyframes shake { 0%,100%{transform:translateX(0)} 20%{transform:translateX(-5px)} 45%{transform:translateX(4px)} 70%{transform:translateX(-3px)} 90%{transform:translateX(2px)} }

/* 目标缺口槽：虚线圆角 + 内阴影 + 图标 */
.gap {
  position: absolute; top: 0; bottom: 0; width: 46px;
  display: flex; align-items: center; justify-content: center;
  color: color-mix(in srgb, var(--zw-text-tertiary) 70%, transparent);
  background: color-mix(in srgb, var(--zw-bg-sidebar) 6%, transparent);
  border-left: 1px dashed var(--zw-border-hover); border-right: 1px dashed var(--zw-border-hover);
  box-shadow: inset 0 0 10px color-mix(in srgb, var(--zw-bg-sidebar) 10%, transparent);
}
.gap-icon { width: 20px; height: 20px; opacity: .8; }

/* 填充：品牌渐变 + 流动高光 */
.fill { position: absolute; left: 0; top: 0; bottom: 0; overflow: hidden; background: linear-gradient(90deg, color-mix(in srgb, var(--zw-brand) 16%, transparent), color-mix(in srgb, var(--zw-brand) 26%, transparent)); }
.slider-captcha.ok .fill { background: linear-gradient(90deg, color-mix(in srgb, var(--zw-success) 16%, transparent), color-mix(in srgb, var(--zw-success) 26%, transparent)); }
.sheen { position: absolute; top: 0; bottom: 0; width: 40%; background: linear-gradient(100deg, transparent, color-mix(in srgb, var(--zw-text-inverse) 22%, transparent), transparent); animation: sheen 2.2s linear infinite; }
@keyframes sheen { from { left: -40%; } to { left: 110%; } }

/* 拖块：圆角白底 + 品牌图标 + 阴影，成功变绿打勾 */
.handle {
  position: absolute; left: 0; top: 0;
  width: 46px; height: 46px; border-radius: var(--zw-radius-md);
  display: flex; align-items: center; justify-content: center;
  background: var(--zw-bg-card); color: var(--zw-brand);
  border: 1px solid color-mix(in srgb, var(--zw-brand) 30%, var(--zw-border));
  box-shadow: 0 3px 10px color-mix(in srgb, var(--zw-bg-sidebar) 22%, transparent);
  cursor: grab; transition: box-shadow var(--zw-duration-fast), transform var(--zw-duration-fast), background var(--zw-duration-fast), color var(--zw-duration-fast);
}
.handle:hover { box-shadow: 0 5px 14px color-mix(in srgb, var(--zw-brand) 30%, transparent); }
.slider-captcha.dragging .handle { cursor: grabbing; box-shadow: 0 6px 18px color-mix(in srgb, var(--zw-brand) 38%, transparent); }
.slider-captcha.ok .handle { background: var(--zw-success); color: var(--zw-text-inverse); border-color: var(--zw-success); }
.h-icon { width: 20px; height: 20px; }

/* 文案 */
.hint { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: var(--zw-font-size-sm); letter-spacing: .12em; color: var(--zw-text-quaternary); pointer-events: none; transition: opacity var(--zw-duration-fast); }
.ok-label { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: var(--zw-font-size-sm); font-weight: 600; letter-spacing: .12em; color: var(--zw-success); pointer-events: none; }

/* 刷新按钮：圆形图标钮 */
.refresh {
  flex-shrink: 0; width: 48px; height: 48px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--zw-border); background: var(--zw-bg-card); color: var(--zw-text-tertiary);
  cursor: pointer; transition: color var(--zw-duration-fast), border-color var(--zw-duration-fast), transform var(--zw-duration-fast);
}
.refresh svg { width: 18px; height: 18px; }
.refresh:hover { color: var(--zw-brand); border-color: color-mix(in srgb, var(--zw-brand) 45%, var(--zw-border)); transform: rotate(40deg); }
</style>
