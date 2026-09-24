<template>
  <div class="login-page">
    <!-- 背景：视频为主、静帧 jpg 兜底；登录卡靠右 -->
    <video
      ref="bgVideoRef"
      class="login-bg-video"
      src="@/assets/media/login-bg-web.mp4"
      poster="@/assets/login-bg-poster.jpg"
      autoplay
      muted
      loop
      playsinline
      preload="metadata"
      @play="onBgPlay"
    ></video>
    <!-- 左侧品牌区（B·精密仪器：标签 + 大标题 + 规格表） -->
    <div class="login-brand-content">
      <div class="brand-logo">
        <img class="brand-logo-icon" src="@/assets/logo-light.png" alt="中维智营" />
        <span class="brand-logo-text">中维智营</span>
      </div>
      <span class="brand-tag"><i></i>ZW-INSIGHT / PM PLATFORM</span>
      <h1 class="brand-slogan">工程项目全生命周期<br />智能管理平台</h1>
      <dl class="brand-spec">
        <div><dt>业务覆盖</dt><dd>项目·合同·预算·财务·材料·机械·劳务·分包</dd></div>
        <div><dt>协同模式</dt><dd>多组织 / 多项目 / 实时</dd></div>
        <div><dt>决策支撑</dt><dd><b>●</b> 实时数据看板</dd></div>
      </dl>
    </div>
    <div class="login-form-area">
      <div class="login-box">
        <i class="box-corner tl"></i><i class="box-corner br"></i>
        <div class="box-head"><span>ACCESS / 登录</span><b>NO.2026</b></div>
        <div class="login-header">
          <h2 class="login-title">{{ loginMode === 'password' ? '欢迎回来' : '快捷登录' }}</h2>
          <p class="login-subtitle">{{ loginMode === 'password' ? '登录账户，继续您的工程项目管理' : '输入手机号，验证码一键登录' }}</p>
        </div>

        <!-- 登录方式：分段开关（B 方案） -->
        <div class="login-tabs" role="tablist" aria-label="登录方式">
          <button
            type="button"
            class="login-tab"
            :class="{ active: loginMode === 'password' }"
            role="tab"
            :aria-selected="loginMode === 'password'"
            @click="switchMode('password')"
          >密码登录</button>
          <button
            type="button"
            class="login-tab"
            :class="{ active: loginMode === 'sms' }"
            role="tab"
            :aria-selected="loginMode === 'sms'"
            @click="switchMode('sms')"
          >短信登录</button>
        </div>

        <!-- 密码登录表单 -->
        <el-form v-show="loginMode === 'password'" :model="loginForm" :rules="rules" ref="formRef" size="large">
          <el-form-item prop="username">
            <div class="row-label"><span>USERNAME</span><b>01</b></div>
            <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <div class="row-label"><span>PASSWORD</span><b>02</b></div>
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
          </el-form-item>
          <el-form-item prop="slider">
            <SliderCaptcha ref="sliderRef" @success="onSliderSuccess" @fail="onSliderFail" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
              <span class="btn-label">进入系统</span>
            </el-button>
          </el-form-item>
          <div class="login-extra">
            <span class="extra-hint">滑块验证已就绪</span>
            <el-link type="primary" :underline="false" @click="goForgotPassword">忘记密码？</el-link>
          </div>
        </el-form>

        <!-- 短信验证码登录表单（后端 AuthService#loginBySms 已就绪，按手机号定位用户与租户） -->
        <el-form v-show="loginMode === 'sms'" :model="smsForm" :rules="smsRules" ref="smsFormRef" size="large">
          <el-form-item prop="phone">
            <div class="row-label"><span>PHONE</span><b>01</b></div>
            <el-input v-model="smsForm.phone" placeholder="请输入手机号" prefix-icon="Iphone" maxlength="11" clearable @keyup.enter="handleSmsLogin" />
          </el-form-item>
          <el-form-item prop="smsCode">
            <div class="row-label"><span>CODE</span><b>02</b></div>
            <div class="captcha-row">
              <el-input v-model="smsForm.smsCode" placeholder="验证码" prefix-icon="Key" maxlength="6" @keyup.enter="handleSmsLogin" />
              <button
                type="button"
                class="sms-code-btn"
                :disabled="smsCooldown > 0 || smsSending"
                @click="handleSendSms"
              >{{ smsCooldown > 0 ? `${smsCooldown}s 后重发` : '获取验证码' }}</button>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" class="login-btn" @click="handleSmsLogin">
              <span class="btn-label">进入系统</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>
      <p class="login-copyright">© 2026 中维智营 · 工程项目管理平台</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { sendSmsCaptcha } from '@/api/captcha'
import SliderCaptcha from './SliderCaptcha.vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const smsFormRef = ref<FormInstance>()
const loading = ref(false)
const sliderToken = ref('')
const sliderRef = ref<InstanceType<typeof SliderCaptcha> | null>(null)

// 背景视频慢放：调此常量即可（1=原速，0.5=半速，越小越慢）
const BG_PLAYBACK_RATE = 0.5
const bgVideoRef = ref<HTMLVideoElement | null>(null)
function onBgPlay() {
  if (bgVideoRef.value) bgVideoRef.value.playbackRate = BG_PLAYBACK_RATE
}

// 登录方式：密码 / 短信验证码
const loginMode = ref<'password' | 'sms'>('password')

const loginForm = ref({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 短信验证码登录表单（手机号校验正则与移动端同源）
const smsForm = ref({
  phone: '',
  smsCode: ''
})

const smsRules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  smsCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ]
}

// 短信发送倒计时（60s 防重发）
const smsCooldown = ref(0)
const smsSending = ref(false)
let smsTimer: ReturnType<typeof setInterval> | null = null

function startSmsCooldown() {
  smsCooldown.value = 60
  smsTimer = setInterval(() => {
    smsCooldown.value--
    if (smsCooldown.value <= 0 && smsTimer) {
      clearInterval(smsTimer)
      smsTimer = null
    }
  }, 1000)
}

function onSliderSuccess(token: string) {
  sliderToken.value = token
}
function onSliderFail() {
  sliderToken.value = ''
}

async function handleLogin() {
  await formRef.value?.validate()
  if (!sliderToken.value) {
    ElMessage.warning('请先拖动滑块完成验证')
    return
  }
  loading.value = true
  try {
    const res: any = await request.post('/v1/auth/login', {
      username: loginForm.value.username,
      password: loginForm.value.password,
      sliderToken: sliderToken.value
    })
    userStore.setToken(res.data.token)
    userStore.setUserInfo({
      userId: res.data.userId,
      username: res.data.username,
      realName: res.data.realName,
      tenantId: res.data.tenantId,
      tenantName: res.data.tenantName,
      roles: res.data.roles
    })
    userStore.setPermissions(res.data.permissions || [])
    router.push('/')
  } catch {
    // 登录失败：滑块令牌已消费，重置滑块
    sliderToken.value = ''
    sliderRef.value?.reset()
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (smsTimer) clearInterval(smsTimer)
})

// 切换登录方式：清除两侧表单校验态，避免残留错误提示
function switchMode(mode: 'password' | 'sms') {
  if (loginMode.value === mode) return
  loginMode.value = mode
  formRef.value?.clearValidate()
  smsFormRef.value?.clearValidate()
}

// 发送短信验证码（先校验手机号；错误由 request 拦截器统一提示）
async function handleSendSms() {
  try {
    await smsFormRef.value?.validateField('phone')
  } catch {
    return
  }
  smsSending.value = true
  try {
    await sendSmsCaptcha(smsForm.value.phone.trim())
    ElMessage.success('验证码已发送')
    startSmsCooldown()
  } catch {
    // 错误已由请求拦截器统一提示
  } finally {
    smsSending.value = false
  }
}

// 短信验证码登录（loginType=SMS，后端按手机号定位用户与租户）
async function handleSmsLogin() {
  await smsFormRef.value?.validate()
  loading.value = true
  try {
    const res: any = await request.post('/v1/auth/login', {
      loginType: 'SMS',
      phone: smsForm.value.phone.trim(),
      smsCode: smsForm.value.smsCode.trim()
    })
    userStore.setToken(res.data.token)
    userStore.setUserInfo({
      userId: res.data.userId,
      username: res.data.username,
      realName: res.data.realName,
      tenantId: res.data.tenantId,
      tenantName: res.data.tenantName,
      roles: res.data.roles
    })
    userStore.setPermissions(res.data.permissions || [])
    router.push('/')
  } catch {
    // 登录失败（验证码错误/手机号未注册等）已由拦截器提示，用户重新获取验证码
  } finally {
    loading.value = false
  }
}

function goForgotPassword() {
  router.push('/forgot-password')
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100vh;
  overflow: hidden;
  background: url('@/assets/login-bg-poster.jpg') center / cover no-repeat, var(--zw-bg-sidebar);
}
/* 背景压暗遮罩：左（品牌）右（卡）加重，中部保留视频 */
.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(90deg,
    color-mix(in srgb, var(--zw-bg-sidebar) 62%, transparent) 0%,
    color-mix(in srgb, var(--zw-bg-sidebar) 22%, transparent) 45%,
    color-mix(in srgb, var(--zw-bg-sidebar) 50%, transparent) 100%);
}
.login-bg-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; z-index: 0; }

/* ===== 左：精密仪器品牌区 ===== */
.login-brand-content { position: absolute; left: clamp(1.5rem, 6vw, 6rem); top: 50%; transform: translateY(-50%); z-index: 2; max-width: 32rem; color: var(--zw-steel-text); }
.brand-logo { display: flex; align-items: center; gap: var(--zw-space-sm-md); margin-bottom: var(--zw-space-xl); }
.brand-logo-icon { width: 2.5rem; height: 2.5rem; object-fit: contain; filter: drop-shadow(0 2px 8px color-mix(in srgb, var(--zw-bg-sidebar) 60%, transparent)); }
.brand-logo-text { font-size: var(--zw-font-size-2xl); font-weight: 600; letter-spacing: .06em; text-shadow: 0 2px 10px color-mix(in srgb, var(--zw-bg-sidebar) 60%, transparent); }
.brand-tag { display: inline-flex; align-items: center; gap: var(--zw-space-sm); font-family: Consolas, 'SFMono-Regular', Menlo, monospace; font-size: var(--zw-font-size-xs); letter-spacing: .3em; color: var(--zw-brand); border: 1px solid color-mix(in srgb, var(--zw-brand) 50%, transparent); padding: var(--zw-space-xs) var(--zw-space-md); margin-bottom: var(--zw-space-lg); }
.brand-tag i { width: .375rem; height: .375rem; background: var(--zw-brand); }
.brand-slogan { font-size: var(--zw-font-size-4xl); font-weight: 700; line-height: 1.2; letter-spacing: .02em; margin-bottom: var(--zw-space-lg); text-shadow: 0 4px 18px color-mix(in srgb, var(--zw-bg-sidebar) 65%, transparent); }
.brand-spec { margin-top: var(--zw-space-xl); border-top: 1px solid color-mix(in srgb, var(--zw-steel-text) 25%, transparent); }
.brand-spec div { display: flex; justify-content: space-between; gap: var(--zw-space-lg); padding: var(--zw-space-md) 0; border-bottom: 1px solid color-mix(in srgb, var(--zw-steel-text) 16%, transparent); font-size: var(--zw-font-size-sm); }
.brand-spec dt { color: var(--zw-steel-text-muted); }
.brand-spec dd { font-family: Consolas, 'SFMono-Regular', Menlo, monospace; letter-spacing: .08em; color: var(--zw-steel-text); text-align: right; }
.brand-spec dd b { color: var(--zw-brand); font-weight: 400; }

/* ===== 右：仪器面板卡 ===== */
.login-form-area { position: relative; z-index: 2; height: 100%; display: flex; flex-direction: column; align-items: flex-end; justify-content: center; padding: var(--zw-space-xl); padding-right: clamp(1.5rem, 8vw, 7.5rem); }
.login-box { position: relative; width: 100%; max-width: 26rem; background: var(--zw-bg-card); border: 1px solid var(--zw-border-light); border-radius: 0; padding: var(--zw-space-xl) var(--zw-space-lg); box-shadow: 0 2px 6px color-mix(in srgb, var(--zw-bg-sidebar) 12%, transparent), 0 26px 70px color-mix(in srgb, var(--zw-bg-sidebar) 45%, transparent); animation: rise-in .6s .18s cubic-bezier(.22, .61, .36, 1) both; }
.box-corner { position: absolute; width: .85rem; height: .85rem; border: 2px solid var(--zw-brand); pointer-events: none; }
.box-corner.tl { top: .4rem; left: .4rem; border-right: 0; border-bottom: 0; }
.box-corner.br { bottom: .4rem; right: .4rem; border-left: 0; border-top: 0; }
.box-head { display: flex; justify-content: space-between; font-family: Consolas, 'SFMono-Regular', Menlo, monospace; font-size: var(--zw-font-size-xs); letter-spacing: .18em; color: var(--zw-text-tertiary); padding-bottom: var(--zw-space-sm); border-bottom: 1px solid var(--zw-border); margin-bottom: var(--zw-space-lg); }
.box-head b { color: var(--zw-brand); font-weight: 400; }
.login-header { margin-bottom: var(--zw-space-lg); }
.login-title { font-size: var(--zw-font-size-3xl); font-weight: 700; letter-spacing: .01em; color: var(--zw-text-primary); margin-bottom: var(--zw-space-sm); }
.login-subtitle { font-size: var(--zw-font-size-sm); color: var(--zw-text-tertiary); }

/* 分段开关 */
.login-tabs { display: flex; border: 1px solid var(--zw-border); margin-bottom: var(--zw-space-xl); }
.login-tab { flex: 1; appearance: none; border: 0; background: var(--zw-bg-card); padding: var(--zw-space-sm-md) 0; font-size: var(--zw-font-size-md); color: var(--zw-text-tertiary); cursor: pointer; transition: background var(--zw-duration-fast), color var(--zw-duration-fast); }
.login-tab:hover:not(.active) { color: var(--zw-brand); }
.login-tab.active { background: var(--zw-bg-sidebar); color: var(--zw-text-inverse); }
.login-tab:focus-visible { outline: 2px solid var(--zw-brand); outline-offset: -2px; }

/* 等宽行标签 */
.row-label { display: flex; justify-content: space-between; width: 100%; font-family: Consolas, 'SFMono-Regular', Menlo, monospace; font-size: var(--zw-font-size-xs); letter-spacing: .16em; color: var(--zw-text-tertiary); margin-bottom: var(--zw-space-sm); }
.row-label b { color: var(--zw-brand); font-weight: 400; }

/* 输入：直角边框 + 橙色 focus 环 + 前缀点亮 */
.login-box :deep(.el-input__wrapper) { border-radius: 0; box-shadow: 0 0 0 1px var(--zw-border); transition: box-shadow var(--zw-duration-fast); }
.login-box :deep(.el-input__wrapper):focus-within, .login-box :deep(.el-input__wrapper).is-focus { box-shadow: 0 0 0 1px var(--zw-brand), 0 0 0 4px color-mix(in srgb, var(--zw-brand) 14%, transparent); }
.login-box :deep(.el-input__prefix) { color: var(--zw-text-tertiary); transition: color var(--zw-duration-fast); }
.login-box :deep(.el-input__wrapper):focus-within .el-input__prefix { color: var(--zw-brand); }
.login-box :deep(.el-input__inner) { caret-color: var(--zw-brand); }

/* 短信按钮 / 验证码行 */
.sms-code-btn { appearance: none; width: 7.5rem; height: 2.5rem; flex-shrink: 0; font-size: var(--zw-font-size-base); font-weight: 600; color: var(--zw-brand); background: var(--zw-brand-light); border: 1px solid var(--zw-brand-light); border-radius: 0; cursor: pointer; white-space: nowrap; transition: background var(--zw-duration-fast), color var(--zw-duration-fast); }
.sms-code-btn:hover:not(:disabled) { background: var(--zw-brand); color: var(--zw-on-primary); }
.sms-code-btn:disabled { color: var(--zw-text-quaternary); background: var(--zw-bg-hover); border-color: var(--zw-border-light); cursor: not-allowed; }
.captcha-row { display: flex; gap: var(--zw-space-sm-md); width: 100%; }

/* CTA：墨黑 + 橙色扫入 */
.login-btn { position: relative; overflow: hidden; width: 100%; height: 2.875rem; border: none; border-radius: 0; background: var(--zw-bg-sidebar); color: var(--zw-text-inverse); font-size: var(--zw-font-size-md); font-weight: 600; letter-spacing: .24em; transition: box-shadow var(--zw-duration-fast); }
.login-btn .btn-label { position: relative; z-index: 1; }
.login-btn::after { content: ''; position: absolute; inset: 0; background: var(--zw-brand); transform: scaleX(0); transform-origin: left; transition: transform .25s var(--zw-ease-out); }
.login-btn:hover::after { transform: scaleX(1); }
.login-btn:hover { box-shadow: 0 10px 26px color-mix(in srgb, var(--zw-brand) 35%, transparent); }

.login-extra { width: 100%; display: flex; justify-content: space-between; align-items: center; margin-top: calc(var(--zw-space-sm) * -1); font-size: var(--zw-font-size-sm); }
.extra-hint { color: var(--zw-text-quaternary); font-family: Consolas, 'SFMono-Regular', Menlo, monospace; letter-spacing: .08em; }
.login-extra :deep(.el-link) { color: var(--zw-brand); }

.login-copyright { position: absolute; bottom: 1.5rem; font-family: Consolas, 'SFMono-Regular', Menlo, monospace; font-size: var(--zw-font-size-xs); letter-spacing: .12em; color: var(--zw-steel-text-muted); animation: rise-in .6s .4s cubic-bezier(.22, .61, .36, 1) both; }

/* 响应式 */
@media (max-width: 992px) { .login-brand-content { display: none; } }
@media (max-width: 768px) { .login-form-area { align-items: center; padding: var(--zw-space-xl); } }

/* 入场错峰 + reduced-motion + 选区 */
@keyframes rise-in { from { opacity: 0; transform: translateY(.75rem); } to { opacity: 1; transform: translateY(0); } }
.login-brand-content > * { animation: rise-in .6s cubic-bezier(.22, .61, .36, 1) both; }
.login-brand-content > *:nth-child(1) { animation-delay: .05s; }
.login-brand-content > *:nth-child(2) { animation-delay: .12s; }
.login-brand-content > *:nth-child(3) { animation-delay: .19s; }
.login-brand-content > *:nth-child(4) { animation-delay: .26s; }
@media (prefers-reduced-motion: reduce) { .login-brand-content > *, .login-box, .login-copyright { animation: none; } }
::selection { background: color-mix(in srgb, var(--zw-brand) 25%, transparent); }
</style>
