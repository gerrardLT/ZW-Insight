<template>
  <div class="captcha-shell">
    <button
      type="button"
      class="captcha-trigger"
      :class="{ verified }"
      :disabled="loading"
      aria-haspopup="dialog"
      @click="open"
    >
      {{ verified ? '安全验证已完成' : loading ? '正在加载安全验证…' : '点击完成安全验证' }}
    </button>
    <div :id="bindId" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { TAC_ASSET_PATH, TAC_GENERATE_URL, TAC_VERIFY_URL, installTacApiAdapter } from '@/api/captcha'

type Tac = {
  config: { addRequestChain: (chain: object) => void }
  init: () => void
  destroyWindow: () => void
  reloadCaptcha: () => void
}

declare global {
  interface Window {
    initTAC?: (assetPath: string, config: object, style?: object) => Promise<Tac>
  }
}

const emit = defineEmits<{ success: [token: string]; fail: [] }>()
const bindId = `tac-captcha-${Math.random().toString(36).slice(2)}`
const verified = ref(false)
const loading = ref(false)
let tac: Tac | null = null
let loaderPromise: Promise<void> | null = null

function loadSdk() {
  if (window.initTAC) return Promise.resolve()
  if (loaderPromise) return loaderPromise
  loaderPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `${TAC_ASSET_PATH}/load.min.js`
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('TAC SDK 加载失败'))
    document.head.appendChild(script)
  })
  return loaderPromise
}

async function open() {
  if (verified.value || loading.value) return
  loading.value = true
  try {
    await loadSdk()
    if (!window.initTAC) throw new Error('initTAC 未注册')
    tac ??= await window.initTAC(TAC_ASSET_PATH, {
      requestCaptchaDataUrl: TAC_GENERATE_URL,
      validCaptchaUrl: TAC_VERIFY_URL,
      bindEl: `#${bindId}`,
      validSuccess: (res: { data?: { sliderToken?: string } }, _captcha: unknown, instance: Tac) => {
        const token = res.data?.sliderToken
        if (!token) {
          emit('fail')
          instance.reloadCaptcha()
          return
        }
        verified.value = true
        emit('success', token)
        instance.destroyWindow()
      },
      validFail: (_res: unknown, _captcha: unknown, instance: Tac) => {
        emit('fail')
        instance.reloadCaptcha()
      },
      btnRefreshFun: (_el: unknown, instance: Tac) => instance.reloadCaptcha(),
      btnCloseFun: (_el: unknown, instance: Tac) => instance.destroyWindow(),
    }, { logoUrl: null })
    installTacApiAdapter(tac.config)
    tac.init()
  } catch {
    emit('fail')
  } finally {
    loading.value = false
  }
}

function reset() {
  verified.value = false
  tac?.destroyWindow()
}

onBeforeUnmount(() => tac?.destroyWindow())
defineExpose({ reset, open })
</script>

<style scoped>
.captcha-shell { width: 100%; }
.captcha-trigger { width: 100%; min-height: 48px; border: 1px solid var(--zw-border); border-radius: var(--zw-radius-md); background: var(--zw-bg-card); color: var(--zw-text-secondary); font: inherit; cursor: pointer; }
.captcha-trigger:hover { border-color: var(--zw-brand); }
.captcha-trigger:focus-visible { outline: 2px solid var(--zw-brand); outline-offset: 2px; }
.captcha-trigger.verified { border-color: var(--zw-success); color: var(--zw-success); }
.captcha-trigger:disabled { cursor: wait; opacity: .7; }
</style>
