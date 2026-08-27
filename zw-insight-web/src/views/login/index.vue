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
import { BlueprintCornerIcon, TowerCraneIcon, HelmetIcon } from '@/components/icons/zw'
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

/* ===== 左侧品牌区（石墨黑全幅画布，无渐变无光斑） ===== */
.login-brand {
  position: relative;
  width: 55%;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--zw-bg-sidebar);
  display: flex;
  align-items: center;
  justify-content: center;
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
  padding: 0 48px;
  color: #f2f3f1;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 40px;
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
  font-size: 16px;
  letter-spacing: 0.5px;
}

.brand-logo-text {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

/* 大写英文副线（Display 层签名） */
.brand-eyebrow {
  font-family: var(--zw-font-display);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 3px;
  text-transform: uppercase;
  color: var(--zw-brand);
  margin-bottom: 16px;
}

/* 中文铭牌：重黑 + 宽字距 */
.brand-slogan {
  font-size: 40px;
  font-weight: 700;
  line-height: 1.25;
  margin-bottom: 20px;
  letter-spacing: 0.04em;
}

.brand-desc {
  font-size: 15px;
  line-height: 1.7;
  color: #c6c9cc;
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
  color: #f2f3f1;
}

/* 自绘工程图标（方帽直角，品牌橙） */
.feature-icon {
  font-size: 20px;
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
