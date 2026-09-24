<template>
  <div class="login-page">
    <!-- 背景：视频为主、静帧 jpg 兜底 -->
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
    <!-- G·斜切动感：全屏斜切橙带 -->
    <i class="slash" aria-hidden="true"></i>
    <i class="slash thin" aria-hidden="true"></i>

    <!-- 左侧品牌文案 -->
    <div class="login-brand-content">
      <span class="brand-kicker"><span>ENGINEERING · FORWARD</span></span>
      <h1 class="brand-slogan">让每个项目，<br />都向前推进。</h1>
      <p class="brand-desc">工程项目全生命周期智能管理平台：项目、合同、预算、财务、材料、机械、劳务、分包全流程协同。</p>
    </div>

    <!-- 右侧登录卡（斜切角） -->
    <div class="login-form-area">
      <div class="login-box">
        <div class="login-header">
          <h2 class="login-title">{{ loginMode === 'password' ? '欢迎回来' : '快捷登录' }}</h2>
          <p class="login-subtitle">{{ loginMode === 'password' ? '登录账户，继续您的工程项目管理' : '输入手机号，验证码一键登录' }}</p>
        </div>

        <!-- 登录方式切换 -->
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

        <!-- 滑块验证码：密码模式强制（短信模式由后端限流防护） -->
        <div v-show="loginMode === 'password'" class="slider-wrap">
          <SliderCaptcha ref="sliderRef" @success="onSliderSuccess" @fail="onSliderFail" />
        </div>

        <!-- 密码登录表单 -->
        <el-form v-show="loginMode === 'password'" :model="loginForm" :rules="rules" ref="formRef" size="large">
          <el-form-item prop="username">
            <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
              <span class="btn-label">进入系统</span>
            </el-button>
          </el-form-item>
          <div class="login-extra">
            <el-link type="primary" :underline="false" @click="goForgotPassword">忘记密码？</el-link>
          </div>
        </el-form>

        <!-- 短信验证码登录表单 -->
        <el-form v-show="loginMode === 'sms'" :model="smsForm" :rules="smsRules" ref="smsFormRef" size="large">
          <el-form-item prop="phone">
            <el-input v-model="smsForm.phone" placeholder="请输入手机号" prefix-icon="Iphone" maxlength="11" clearable @keyup.enter="handleSmsLogin" />
          </el-form-item>
          <el-form-item prop="smsCode">
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
  // 切换后滑块令牌作废，重置滑块
  sliderToken.value = ''
  sliderRef.value?.reset()
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
.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(100deg,
    color-mix(in srgb, var(--zw-bg-sidebar) 66%, transparent) 0%,
    color-mix(in srgb, var(--zw-bg-sidebar) 22%, transparent) 55%,
    color-mix(in srgb, var(--zw-bg-sidebar) 52%, transparent) 100%);
}
.login-bg-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; z-index: 0; }

/* G·斜切橙带（全屏装饰，置于遮罩之上、内容之下） */
.slash { position: absolute; top: -20%; right: 26%; width: 16vw; height: 140%; background: var(--zw-brand); transform: skewX(-14deg); opacity: .9; z-index: 1; }
.slash.thin { right: 24%; width: 2vw; opacity: .5; }

/* 左：斜切标签 + 大标题 */
.login-brand-content { position: absolute; left: clamp(1.5rem, 7vw, 7rem); top: 50%; transform: translateY(-50%); z-index: 2; max-width: 32rem; color: var(--zw-steel-text); }
.brand-kicker { display: inline-block; background: var(--zw-brand); color: var(--zw-text-inverse); font-family: Consolas, 'SFMono-Regular', Menlo, monospace; font-size: var(--zw-font-size-xs); letter-spacing: .24em; padding: var(--zw-space-sm) var(--zw-space-md); transform: skewX(-14deg); margin-bottom: var(--zw-space-lg); }
.brand-kicker span { display: inline-block; transform: skewX(14deg); }
.brand-slogan { font-size: var(--zw-font-size-5xl); font-weight: 800; line-height: 1.15; letter-spacing: -0.01em; margin-bottom: var(--zw-space-lg); text-shadow: 0 4px 20px color-mix(in srgb, var(--zw-bg-sidebar) 65%, transparent); }
.brand-desc { font-size: var(--zw-font-size-md); line-height: 1.9; color: var(--zw-steel-text-muted); max-width: 28rem; text-shadow: 0 2px 10px color-mix(in srgb, var(--zw-bg-sidebar) 60%, transparent); }

/* 右：斜切角白卡 */
.login-form-area { position: relative; z-index: 2; height: 100%; display: flex; flex-direction: column; align-items: flex-end; justify-content: center; padding: var(--zw-space-xl); padding-right: clamp(1.5rem, 8vw, 7.5rem); }
.login-box {
  position: relative;
  width: 100%;
  max-width: 26rem;
  background: var(--zw-bg-card);
  padding: var(--zw-space-xl) var(--zw-space-lg);
  clip-path: polygon(0 0, 100% 0, 100% calc(100% - 1.75rem), calc(100% - 1.75rem) 100%, 0 100%);
  box-shadow: 0 30px 80px color-mix(in srgb, var(--zw-bg-sidebar) 50%, transparent);
  animation: rise-in .6s .18s cubic-bezier(.22, .61, .36, 1) both;
}
.login-header { margin-bottom: var(--zw-space-lg); }
.login-title { font-size: var(--zw-font-size-3xl); font-weight: 800; letter-spacing: -0.01em; color: var(--zw-text-primary); margin-bottom: var(--zw-space-sm); }
.login-subtitle { font-size: var(--zw-font-size-sm); color: var(--zw-text-tertiary); }

/* Tab：粗下划线橙色激活 */
.login-tabs { display: flex; gap: var(--zw-space-xl); margin-bottom: var(--zw-space-xl); border-bottom: 2px solid var(--zw-border-light); }
.login-tab { position: relative; appearance: none; background: none; border: none; padding: 0 0 var(--zw-space-md); font-size: var(--zw-font-size-md); font-weight: 700; color: var(--zw-text-tertiary); cursor: pointer; transition: color var(--zw-duration-fast); }
.login-tab:hover { color: var(--zw-text-secondary); }
.login-tab.active { color: var(--zw-brand); }
.login-tab.active::after { content: ''; position: absolute; left: 0; right: 0; bottom: -2px; height: 2px; background: var(--zw-brand); }
.login-tab:focus-visible { outline: 2px solid var(--zw-brand); outline-offset: 2px; }

/* 滑块验证码容器（双模式共用） */
.slider-wrap { margin-bottom: var(--zw-space-lg); }

/* 输入：粗下划线 + 橙色 focus */
.login-box :deep(.el-input__wrapper) { border-radius: 0; box-shadow: 0 2px 0 0 var(--zw-border-light); background: transparent; transition: box-shadow var(--zw-duration-fast); }
.login-box :deep(.el-input__wrapper):focus-within, .login-box :deep(.el-input__wrapper).is-focus { box-shadow: 0 2px 0 0 var(--zw-brand); }
.login-box :deep(.el-input__prefix) { color: var(--zw-text-tertiary); transition: color var(--zw-duration-fast); }
.login-box :deep(.el-input__wrapper):focus-within .el-input__prefix { color: var(--zw-brand); }
.login-box :deep(.el-input__inner) { caret-color: var(--zw-brand); }

/* 短信按钮 / 验证码行 */
.sms-code-btn { appearance: none; width: 7.5rem; height: 2.5rem; flex-shrink: 0; font-size: var(--zw-font-size-base); font-weight: 600; color: var(--zw-brand); background: var(--zw-brand-light); border: 1px solid var(--zw-brand-light); border-radius: 0; cursor: pointer; white-space: nowrap; transition: background var(--zw-duration-fast), color var(--zw-duration-fast); }
.sms-code-btn:hover:not(:disabled) { background: var(--zw-brand); color: var(--zw-on-primary); }
.sms-code-btn:disabled { color: var(--zw-text-quaternary); background: var(--zw-bg-hover); border-color: var(--zw-border-light); cursor: not-allowed; }
.captcha-row { display: flex; gap: var(--zw-space-sm-md); width: 100%; }

/* CTA：墨黑 + 斜切角 + 悬浮翻橙 */
.login-btn { position: relative; width: 100%; height: 2.875rem; border: none; border-radius: 0; background: var(--zw-bg-sidebar); color: var(--zw-text-inverse); font-size: var(--zw-font-size-md); font-weight: 700; letter-spacing: .2em; clip-path: polygon(0 0, 100% 0, 100% 70%, calc(100% - .875rem) 100%, 0 100%); transition: background var(--zw-duration-fast), box-shadow var(--zw-duration-fast); }
.login-btn:hover { background: var(--zw-brand); box-shadow: 0 10px 26px color-mix(in srgb, var(--zw-brand) 35%, transparent); }

.login-extra { width: 100%; display: flex; justify-content: flex-end; margin-top: calc(var(--zw-space-sm) * -1); }
.login-extra :deep(.el-link) { color: var(--zw-brand); }

.login-copyright { position: absolute; bottom: 1.5rem; font-size: var(--zw-font-size-sm); color: var(--zw-steel-text-muted); animation: rise-in .6s .4s cubic-bezier(.22, .61, .36, 1) both; }

/* 响应式 */
@media (max-width: 992px) { .login-brand-content, .slash { display: none; } }
@media (max-width: 768px) { .login-form-area { align-items: center; padding: var(--zw-space-xl); } }

/* 入场 + reduced-motion + 选区 */
@keyframes rise-in { from { opacity: 0; transform: translateY(.75rem); } to { opacity: 1; transform: translateY(0); } }
.login-brand-content > * { animation: rise-in .6s cubic-bezier(.22, .61, .36, 1) both; }
.login-brand-content > *:nth-child(1) { animation-delay: .05s; }
.login-brand-content > *:nth-child(2) { animation-delay: .14s; }
.login-brand-content > *:nth-child(3) { animation-delay: .23s; }
@media (prefers-reduced-motion: reduce) { .login-brand-content > *, .login-box, .login-copyright { animation: none; } }
::selection { background: color-mix(in srgb, var(--zw-brand) 25%, transparent); }
</style>
