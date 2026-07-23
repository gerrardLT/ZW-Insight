<template>
  <div class="login-page">
    <!-- 左侧品牌视觉区 -->
    <div class="login-brand">
      <div class="brand-orb orb-1"></div>
      <div class="brand-orb orb-2"></div>
      <div class="brand-orb orb-3"></div>
      <div class="brand-grid"></div>
      <div class="brand-content">
        <div class="brand-logo">
          <div class="brand-logo-icon">ZW</div>
          <span class="brand-logo-text">中维智营</span>
        </div>
        <h1 class="brand-slogan">工程项目全生命周期<br />智能管理平台</h1>
        <p class="brand-desc">涵盖项目、合同、预算、财务、材料、机械、劳务、分包全流程协同，让工程管理更高效。</p>
        <div class="brand-features">
          <div class="feature-item"><span class="feature-dot"></span>全链路业务数字化</div>
          <div class="feature-item"><span class="feature-dot"></span>多组织多项目协同</div>
          <div class="feature-item"><span class="feature-dot"></span>实时数据看板决策</div>
        </div>
      </div>
    </div>

    <!-- 右侧登录表单区 -->
    <div class="login-form-area">
      <div class="login-box">
        <div class="login-header">
          <h2 class="login-title">欢迎回来</h2>
          <p class="login-subtitle">请登录您的账户以继续</p>
        </div>
        <el-form :model="loginForm" :rules="rules" ref="formRef" size="large">
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
      </div>
      <p class="login-copyright">© 2026 中维智营 · 工程项目管理平台</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getImageCaptcha } from '@/api/captcha'
import request from '@/utils/request'
import type { FormInstance } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaImage = ref('')
const captchaUuid = ref('')

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

/* ===== 左侧品牌区 ===== */
.login-brand {
  position: relative;
  width: 55%;
  flex-shrink: 0;
  overflow: hidden;
  background: linear-gradient(135deg, #1e2a5e 0%, #3370ff 55%, #6b46c1 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(ellipse at center, black 40%, transparent 75%);
}

.brand-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.5;
}

.orb-1 {
  width: 380px;
  height: 380px;
  background: #5b8cff;
  top: -80px;
  left: -60px;
  animation: float 12s ease-in-out infinite;
}

.orb-2 {
  width: 300px;
  height: 300px;
  background: #a855f7;
  bottom: -60px;
  right: 40px;
  animation: float 15s ease-in-out infinite reverse;
}

.orb-3 {
  width: 240px;
  height: 240px;
  background: #38bdf8;
  top: 45%;
  left: 55%;
  animation: float 18s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -40px) scale(1.08); }
  66% { transform: translate(-20px, 30px) scale(0.95); }
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 460px;
  padding: 0 48px;
  color: #fff;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 48px;
}

.brand-logo-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  font-weight: 700;
  font-size: 16px;
  letter-spacing: 0.5px;
}

.brand-logo-text {
  font-size: 22px;
  font-weight: 600;
}

.brand-slogan {
  font-size: 40px;
  font-weight: 700;
  line-height: 1.3;
  margin-bottom: 20px;
  letter-spacing: -0.5px;
}

.brand-desc {
  font-size: 15px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.8);
  margin-bottom: 40px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.9);
}

.feature-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 12px rgba(255, 255, 255, 0.8);
}

/* ===== 右侧表单区 ===== */
.login-form-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  position: relative;
}

.login-box {
  width: 100%;
  max-width: 360px;
}

.login-header {
  margin-bottom: 32px;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--zw-text-primary);
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: var(--zw-text-tertiary);
}

.captcha-row {
  display: flex;
  gap: 12px;
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
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
}

.login-extra {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  margin-top: -8px;
}

.login-copyright {
  position: absolute;
  bottom: 24px;
  font-size: 13px;
  color: var(--zw-text-quaternary);
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .login-brand {
    display: none;
  }
}
</style>
