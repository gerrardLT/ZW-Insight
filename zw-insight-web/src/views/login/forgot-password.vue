<template>
  <div class="forgot-container">
    <el-card class="forgot-card">
      <h2 class="forgot-title">找回密码</h2>
      <p class="forgot-subtitle">通过手机号短信验证码重置登录密码</p>

      <el-steps :active="active" align-center finish-status="success" class="forgot-steps">
        <el-step title="验证手机号" />
        <el-step title="校验验证码" />
        <el-step title="设置新密码" />
      </el-steps>

      <!-- 第一步：手机号输入 + 发送验证码 -->
      <div v-show="active === 0" class="step-body">
        <el-form :model="form" :rules="phoneRules" ref="phoneFormRef">
          <el-form-item prop="phone">
            <el-input v-model="form.phone" placeholder="请输入手机号" prefix-icon="Iphone" maxlength="11" />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="sending"
              :disabled="countdown > 0"
              style="width: 100%"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后可重发` : '发送验证码' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 第二步：验证码校验 -->
      <div v-show="active === 1" class="step-body">
        <el-form :model="form" :rules="codeRules" ref="codeFormRef">
          <el-form-item>
            <span class="tip-text">验证码已发送至 {{ maskedPhone }}</span>
          </el-form-item>
          <el-form-item prop="code">
            <el-input v-model="form.code" placeholder="请输入 6 位验证码" prefix-icon="Key" maxlength="6" />
          </el-form-item>
          <el-form-item>
            <div style="display: flex; gap: 12px; width: 100%">
              <el-button style="flex: 1" @click="active = 0">上一步</el-button>
              <el-button
                type="text"
                :disabled="countdown > 0"
                :loading="sending"
                @click="handleSendCode"
              >
                {{ countdown > 0 ? `${countdown}s 后重发` : '重新发送' }}
              </el-button>
              <el-button type="primary" style="flex: 1" :loading="verifying" @click="handleVerifyCode">
                下一步
              </el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>

      <!-- 第三步：新密码设置 -->
      <div v-show="active === 2" class="step-body">
        <el-form :model="form" :rules="pwdRules" ref="pwdFormRef">
          <el-form-item prop="newPassword">
            <el-input
              v-model="form.newPassword"
              type="password"
              placeholder="请输入新密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <span class="tip-text">密码需 8-20 个字符，且同时包含字母和数字</span>
          </el-form-item>
          <el-form-item>
            <div style="display: flex; gap: 12px; width: 100%">
              <el-button style="flex: 1" @click="active = 1">上一步</el-button>
              <el-button type="primary" style="flex: 1" :loading="submitting" @click="handleReset">
                重置密码
              </el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>

      <div class="back-login">
        <el-link type="primary" @click="goLogin">返回登录</el-link>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { sendResetCode, verifyResetCode, resetPassword } from '@/api/password-reset'

const router = useRouter()

const active = ref(0)
const sending = ref(false)
const verifying = ref(false)
const submitting = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const phoneFormRef = ref<FormInstance>()
const codeFormRef = ref<FormInstance>()
const pwdFormRef = ref<FormInstance>()

const form = ref({
  phone: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const maskedPhone = computed(() => {
  const p = form.value.phone
  return p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p
})

// 密码复杂度：8-20 字符且包含字母和数字（与后端一致）
function isValidPassword(pwd: string): boolean {
  return /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(pwd)
}

const phoneRules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

const codeRules: FormRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ]
}

const pwdRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isValidPassword(value)) {
          callback(new Error('密码需 8-20 个字符且包含字母和数字'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.value.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function handleSendCode() {
  await phoneFormRef.value?.validate()
  sending.value = true
  try {
    await sendResetCode(form.value.phone)
    ElMessage.success('验证码已发送')
    startCountdown()
    if (active.value === 0) active.value = 1
  } catch {
    // 错误已由请求拦截器统一提示
  } finally {
    sending.value = false
  }
}

async function handleVerifyCode() {
  await codeFormRef.value?.validate()
  verifying.value = true
  try {
    await verifyResetCode(form.value.phone, form.value.code)
    active.value = 2
  } catch {
    // 错误已由请求拦截器统一提示
  } finally {
    verifying.value = false
  }
}

async function handleReset() {
  await pwdFormRef.value?.validate()
  submitting.value = true
  try {
    await resetPassword(form.value.phone, form.value.code, form.value.newPassword)
    ElMessage.success('密码重置成功，请使用新密码登录')
    goLogin()
  } catch {
    // 错误已由请求拦截器统一提示
  } finally {
    submitting.value = false
  }
}

function goLogin() {
  router.push('/login')
}
</script>

<style scoped>
.forgot-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.forgot-card {
  width: 460px;
  padding: 20px;
}
.forgot-title {
  text-align: center;
  margin-bottom: 4px;
  font-size: 24px;
  color: #303133;
}
.forgot-subtitle {
  text-align: center;
  color: #909399;
  margin-bottom: 24px;
}
.forgot-steps {
  margin-bottom: 32px;
}
.step-body {
  min-height: 160px;
}
.tip-text {
  font-size: 13px;
  color: #909399;
}
.back-login {
  text-align: center;
  margin-top: 8px;
}
</style>
