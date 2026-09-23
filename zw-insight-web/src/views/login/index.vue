<template>
  <div class="login-page">
    <!-- 背景：视频为主、静帧 jpg 兜底；登录卡靠右 -->
    <video
      class="login-bg-video"
      src="@/assets/media/insight-bg-web.mp4"
      poster="@/assets/login-bg-hero.jpg"
      autoplay
      muted
      loop
      playsinline
      preload="metadata"
    ></video>
    <!-- 左侧品牌文案（浮于背景视频之上） -->
    <div class="login-brand-content">
      <div class="brand-logo">
        <img class="brand-logo-icon" src="@/assets/logo-light.png" alt="中维智营" />
        <span class="brand-logo-text">中维智营</span>
      </div>
      <div class="brand-eyebrow">Project Management Platform</div>
      <h1 class="brand-slogan">工程项目全生命周期<br />智能管理平台</h1>
      <p class="brand-desc">涵盖项目、合同、预算、财务、材料、机械、劳务、分包全流程协同，让工程管理更高效。</p>
      <div class="brand-features">
        <div class="feature-item"><span class="feature-icon-base"><BlueprintCornerIcon class="feature-icon" /></span>全链路业务数字化</div>
        <div class="feature-item"><span class="feature-icon-base"><TowerCraneIcon class="feature-icon" /></span>多组织多项目协同</div>
        <div class="feature-item"><span class="feature-icon-base"><HelmetIcon class="feature-icon" /></span>实时数据看板决策</div>
      </div>
    </div>
    <div class="login-form-area">
      <div class="login-box">
        <div class="login-header">
          <h2 class="login-title">{{ loginMode === 'password' ? '欢迎回来' : '快捷登录' }}</h2>
          <p class="login-subtitle">{{ loginMode === 'password' ? '请登录您的账户以继续' : '使用手机短信验证码快速登录' }}</p>
        </div>

        <!-- 登录方式切换：密码 / 短信验证码（对齐移动端双 Tab 范式，沿用工业精密设计语言） -->
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
          >短信验证码登录</button>
        </div>

        <!-- 密码登录表单 -->
        <el-form v-show="loginMode === 'password'" :model="loginForm" :rules="rules" ref="formRef" size="large">
          <el-form-item prop="username">
            <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
          </el-form-item>
          <el-form-item prop="slider">
            <SliderCaptcha ref="sliderRef" @success="onSliderSuccess" @fail="onSliderFail" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
              登 录
            </el-button>
          </el-form-item>
          <div class="login-extra">
            <el-link type="primary" :underline="false" @click="goForgotPassword">忘记密码？</el-link>
          </div>
        </el-form>

        <!-- 短信验证码登录表单（后端 AuthService#loginBySms 已就绪，按手机号定位用户与租户） -->
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
              登 录
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
import { BlueprintCornerIcon, TowerCraneIcon, HelmetIcon } from '@/components/icons/zw'
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
  background: url('@/assets/login-bg-hero.jpg') center / cover no-repeat, var(--zw-bg-sidebar);
}
/* 背景压暗遮罩：左（品牌文案）右（登录卡）加重，中部保留视频展示 */
.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(90deg,
    color-mix(in srgb, var(--zw-bg-sidebar) 58%, transparent) 0%,
    color-mix(in srgb, var(--zw-bg-sidebar) 20%, transparent) 36%,
    color-mix(in srgb, var(--zw-bg-sidebar) 16%, transparent) 60%,
    color-mix(in srgb, var(--zw-bg-sidebar) 46%, transparent) 100%);
}

/* 背景视频：铺满裁切，置于遮罩之下 */
.login-bg-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 0;
}

/* ===== 左侧品牌文案（浮于背景视频之上，浅色） ===== */
.login-brand-content {
  position: absolute;
  left: clamp(1.5rem, 6vw, 6rem);
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  max-width: 30rem;
  color: var(--zw-steel-text);
}
.brand-logo {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm-md);
  margin-bottom: var(--zw-space-xl);
}
.brand-logo-icon {
  width: 44px;
  height: 44px;
  object-fit: contain;
  filter: drop-shadow(0 2px 8px color-mix(in srgb, var(--zw-bg-sidebar) 60%, transparent));
}
.brand-logo-text {
  font-size: var(--zw-font-size-2xl);
  font-weight: 600;
  letter-spacing: 0.06em;
  text-shadow: 0 2px 10px color-mix(in srgb, var(--zw-bg-sidebar) 60%, transparent);
}
.brand-eyebrow {
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-base);
  font-weight: 700;
  letter-spacing: 4px;
  text-transform: uppercase;
  color: var(--zw-brand);
  margin-bottom: var(--zw-space-md);
  text-shadow: 0 2px 10px color-mix(in srgb, var(--zw-bg-sidebar) 60%, transparent);
}
.brand-slogan {
  font-size: var(--zw-font-size-4xl);
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: var(--zw-space-lg);
  letter-spacing: 0.015em;
  text-shadow: 0 4px 18px color-mix(in srgb, var(--zw-bg-sidebar) 65%, transparent);
}
.brand-desc {
  font-size: var(--zw-font-size-md);
  line-height: 1.7;
  color: var(--zw-steel-text-muted);
  margin-bottom: var(--zw-space-xl);
  text-shadow: 0 2px 10px color-mix(in srgb, var(--zw-bg-sidebar) 60%, transparent);
}
.brand-features {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}
.feature-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm-md);
  font-size: var(--zw-font-size-md);
  color: var(--zw-steel-text);
  text-shadow: 0 2px 8px color-mix(in srgb, var(--zw-bg-sidebar) 60%, transparent);
}
.feature-icon-base {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--zw-radius-md);
  background: color-mix(in srgb, var(--zw-brand) 16%, transparent);
  border: 1px solid color-mix(in srgb, var(--zw-brand) 30%, transparent);
  flex-shrink: 0;
}
.feature-icon {
  font-size: var(--zw-font-size-lg);
  color: var(--zw-brand);
  flex-shrink: 0;
}

/* ===== 表单区：登录卡靠右浮于背景之上 ===== */
.login-form-area {
  position: relative;
  z-index: 2;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  padding: var(--zw-space-xl);
  padding-right: clamp(1.5rem, 8vw, 7.5rem);
}

/* P0：表单白底浮层卡片，奠定现代空间感 */
.login-box {
  width: 100%;
  max-width: 380px;
  background: var(--zw-bg-card);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-xl);
  padding: var(--zw-space-xl) var(--zw-space-lg);
  box-shadow: 0 24px 64px color-mix(in srgb, var(--zw-bg-sidebar) 45%, transparent);
}

.login-header {
  margin-bottom: var(--zw-space-xl);
}

.login-title {
  font-size: var(--zw-font-size-3xl);
  font-weight: 700;
  color: var(--zw-text-primary);
  margin-bottom: var(--zw-space-sm);
}

.login-subtitle {
  font-size: var(--zw-font-size-base);
  color: var(--zw-text-tertiary);
}

/* ===== 登录方式切换 Tab（工业精密：2px 下划线强调，零阴影零渐变） ===== */
.login-tabs {
  display: flex;
  gap: var(--zw-space-xl);
  margin-bottom: var(--zw-space-xl);
  border-bottom: 1px solid var(--zw-border);
}

.login-tab {
  position: relative;
  appearance: none;
  background: none;
  border: none;
  padding: 0 0 var(--zw-space-md);
  font-size: var(--zw-font-size-md);
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--zw-text-tertiary);
  cursor: pointer;
  transition: color var(--zw-duration-fast) var(--zw-ease-out);
}

.login-tab:hover {
  color: var(--zw-text-secondary);
}

.login-tab.active {
  color: var(--zw-brand);
}

.login-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--zw-brand);
}

.login-tab:focus-visible {
  outline: 2px solid var(--zw-brand);
  outline-offset: 2px;
}

/* 获取验证码按钮（与验证码图片同宽，复用精密直角语言） */
.sms-code-btn {
  appearance: none;
  width: 120px;
  height: 40px;
  flex-shrink: 0;
  font-size: var(--zw-font-size-base);
  font-weight: 600;
  color: var(--zw-brand);
  background: var(--zw-brand-light);
  border: 1px solid var(--zw-brand-light);
  border-radius: var(--zw-radius-sm);
  cursor: pointer;
  white-space: nowrap;
  transition: opacity var(--zw-duration-fast) var(--zw-ease-out);
}

.sms-code-btn:hover:not(:disabled) {
  background: var(--zw-brand);
  color: var(--zw-on-primary);
}

.sms-code-btn:disabled {
  color: var(--zw-text-quaternary);
  background: var(--zw-bg-hover);
  border-color: var(--zw-border-light);
  cursor: not-allowed;
}

.captcha-row {
  display: flex;
  gap: var(--zw-space-sm-md);
  width: 100%;
}

/* P1：输入框 Focus 外发光 + 细微渐变 */
.login-box :deep(.el-input__wrapper) {
  border-radius: var(--zw-radius-sm);
  box-shadow: 0 0 0 1px var(--zw-border);
  transition: box-shadow var(--zw-duration-fast), background var(--zw-duration-fast);
}
.login-box :deep(.el-input__wrapper):focus-within,
.login-box :deep(.el-input__wrapper.is-focus) {
  background: linear-gradient(180deg, color-mix(in srgb, var(--zw-brand) 4%, transparent), transparent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--zw-brand) 16%, transparent), 0 0 16px color-mix(in srgb, var(--zw-brand) 22%, transparent);
}

/* P1：登录按钮细微渐变 + 外发光 */
.login-btn {
  width: 100%;
  height: 46px;
  font-size: var(--zw-font-size-md);
  font-weight: 600;
  letter-spacing: 4px;
  border: none;
  background: linear-gradient(135deg, var(--zw-brand) 0%, var(--zw-brand-hover) 100%);
  box-shadow: 0 8px 22px color-mix(in srgb, var(--zw-brand) 34%, transparent);
  transition: box-shadow var(--zw-duration-fast), transform var(--zw-duration-fast), filter var(--zw-duration-fast);
}
.login-btn:hover {
  filter: brightness(1.05);
  box-shadow: 0 10px 28px color-mix(in srgb, var(--zw-brand) 46%, transparent);
  transform: translateY(-1px);
}
.login-btn:active { transform: translateY(0); }

.login-extra {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  margin-top: calc(var(--zw-space-sm) * -1);
}

.login-copyright {
  position: absolute;
  bottom: 24px;
  font-size: var(--zw-font-size-sm);
  color: var(--zw-steel-text-muted);
}

/* ===== 响应式：窄屏隐藏左侧文案、登录卡回居中 ===== */
@media (max-width: 992px) {
  .login-brand-content {
    display: none;
  }
}
@media (max-width: 768px) {
  .login-form-area {
    align-items: center;
    padding: var(--zw-space-xl);
  }
}
</style>
