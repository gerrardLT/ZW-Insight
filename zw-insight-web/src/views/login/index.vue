<template>
  <div class="login-page">
    <!-- 左侧品牌视觉区（石墨黑 Hero：工程铭牌语言） -->
    <div class="login-brand">
      <div class="brand-hazard hazard-divider"></div>
      <div class="brand-content">
        <div class="brand-logo">
          <div class="brand-logo-icon">ZW</div>
          <span class="brand-logo-text">中维智营</span>
        </div>
        <div class="brand-eyebrow">Project Management Platform</div>
        <h1 class="brand-slogan">工程项目全生命周期<br />智能管理平台</h1>
        <p class="brand-desc">涵盖项目、合同、预算、财务、材料、机械、劳务、分包全流程协同，让工程管理更高效。</p>
        <div class="brand-features">
          <div class="feature-item"><BlueprintCornerIcon class="feature-icon" />全链路业务数字化</div>
          <div class="feature-item"><TowerCraneIcon class="feature-icon" />多组织多项目协同</div>
          <div class="feature-item"><HelmetIcon class="feature-icon" />实时数据看板决策</div>
        </div>
      </div>
    </div>

    <!-- 右侧登录表单区 -->
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
          <el-form-item prop="captchaCode">
            <div class="captcha-row">
              <el-input v-model="loginForm.captchaCode" placeholder="验证码" prefix-icon="Key" @keyup.enter="handleLogin" />
              <img :src="captchaImage" @click="refreshCaptcha" class="captcha-img" alt="验证码" />
            </div>
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
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getImageCaptcha, sendSmsCaptcha } from '@/api/captcha'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { BlueprintCornerIcon, TowerCraneIcon, HelmetIcon } from '@/components/icons/zw'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const smsFormRef = ref<FormInstance>()
const loading = ref(false)
const captchaImage = ref('')
const captchaUuid = ref('')

// 登录方式：密码 / 短信验证码
const loginMode = ref<'password' | 'sms'>('password')

const loginForm = ref({
  username: '',
  password: '',
  captchaCode: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
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

async function refreshCaptcha() {
  try {
    const res: any = await getImageCaptcha()
    captchaUuid.value = res.data.uuid
    captchaImage.value = res.data.imageBase64
  } catch {
    // 验证码获取失败时不阻断页面
  }
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const res: any = await request.post('/v1/auth/login', {
      username: loginForm.value.username,
      password: loginForm.value.password,
      captchaCode: loginForm.value.captchaCode,
      captchaUuid: captchaUuid.value
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
    // 登录失败时自动刷新验证码
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
})

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
  height: 100vh;
  display: flex;
  overflow: hidden;
  background-color: var(--zw-bg-card);
}

/* ===== 左侧品牌区（石墨黑全幅画布，无渐变无光斑） =====
 * 2026-09-16 品牌背景插画：AI 生成夜景鸟瞰工地线稿（等轴测塔吊群+铜架轮廓，
 * 橙色点锥：塔吊警示灯/安全帽）；横向渐变遮罩：左深（0.78）保文案可读，
 * 右浅（0.35）让线稿隐约浮现——低对比背景不抢视觉主体 */
.login-brand {
  position: relative;
  width: 55%;
  flex-shrink: 0;
  overflow: hidden;
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--zw-bg-sidebar) 78%, transparent) 0%, color-mix(in srgb, var(--zw-bg-sidebar) 52%, transparent) 55%, color-mix(in srgb, var(--zw-bg-sidebar) 35%, transparent) 100%),
    url('@/assets/login-bg-site.png') center / cover no-repeat,
    var(--zw-bg-sidebar);
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 窄屏降级：插画隐藏退回纯色（避免小屏拥挤） */
@media (max-width: 992px) {
  .login-brand {
    background: var(--zw-bg-sidebar);
  }
}

/* 顶部 4px 警示条纹：品牌识别线 */
.brand-hazard {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 460px;
  padding: 0 var(--zw-space-2xl);
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
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--zw-radius-xs);
  background: var(--zw-brand);
  color: var(--zw-on-primary);
  font-weight: 700;
  font-size: var(--zw-font-size-md);
  letter-spacing: 0.5px;
}

.brand-logo-text {
  font-size: var(--zw-font-size-2xl);
  font-weight: 600;
  letter-spacing: 0.04em;
}

/* 大写英文副线（Display 层签名） */
.brand-eyebrow {
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-base);
  font-weight: 700;
  letter-spacing: 3px;
  text-transform: uppercase;
  color: var(--zw-brand);
  margin-bottom: var(--zw-space-md);
}

/* 中文铭牌：重黑 + 宽字距 */
.brand-slogan {
  font-size: var(--zw-font-size-4xl);
  font-weight: 700;
  line-height: 1.25;
  margin-bottom: var(--zw-space-lg);
  letter-spacing: 0.04em;
}

.brand-desc {
  font-size: var(--zw-font-size-md);
  line-height: 1.7;
  color: var(--zw-steel-text-muted);
  margin-bottom: var(--zw-space-xl);
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
}

/* 自绘工程图标（方帽直角，品牌橙） */
.feature-icon {
  font-size: var(--zw-font-size-xl);
  color: var(--zw-brand);
  flex-shrink: 0;
}

/* ===== 右侧表单区 ===== */
.login-form-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--zw-space-xl);
  position: relative;
}

.login-box {
  width: 100%;
  max-width: 360px;
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

.captcha-img {
  width: 120px;
  height: 40px;
  cursor: pointer;
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-sm);
  flex-shrink: 0;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: var(--zw-font-size-md);
  font-weight: 600;
  letter-spacing: 4px;
}

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
  color: var(--zw-text-quaternary);
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .login-brand {
    display: none;
  }
}
</style>
