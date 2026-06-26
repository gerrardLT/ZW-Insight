<template>
  <view class="forgot-page">
    <view class="forgot-header">
      <text class="page-title">找回密码</text>
      <text class="page-subtitle">通过手机号短信验证码重置登录密码</text>
    </view>

    <!-- 步骤指示 -->
    <view class="steps">
      <view v-for="(label, idx) in stepLabels" :key="idx" class="step">
        <view class="step-dot" :class="{ active: active >= idx, done: active > idx }">
          <text class="step-no">{{ idx + 1 }}</text>
        </view>
        <text class="step-label" :class="{ active: active >= idx }">{{ label }}</text>
      </view>
    </view>

    <view class="forgot-form">
      <!-- 第一步：手机号 -->
      <view v-if="active === 0">
        <view class="form-item">
          <input v-model="form.phone" type="number" placeholder="请输入手机号" class="input" maxlength="11" />
        </view>
        <button class="primary-btn" :disabled="countdown > 0" :loading="sending" @click="handleSendCode">
          {{ countdown > 0 ? `${countdown}s 后可重发` : '发送验证码' }}
        </button>
      </view>

      <!-- 第二步：验证码 -->
      <view v-if="active === 1">
        <view class="form-tip">
          <text>验证码已发送至 {{ maskedPhone }}</text>
        </view>
        <view class="form-item code-item">
          <input v-model="form.code" type="number" placeholder="请输入 6 位验证码" class="input code-input" maxlength="6" />
          <button class="code-btn" :disabled="countdown > 0" @click="handleSendCode">
            {{ countdown > 0 ? `${countdown}s` : '重发' }}
          </button>
        </view>
        <view class="btn-row">
          <button class="ghost-btn" @click="active = 0">上一步</button>
          <button class="primary-btn flex1" :loading="verifying" @click="handleVerifyCode">下一步</button>
        </view>
      </view>

      <!-- 第三步：新密码 -->
      <view v-if="active === 2">
        <view class="form-item">
          <input v-model="form.newPassword" type="password" placeholder="请输入新密码" class="input" />
        </view>
        <view class="form-item">
          <input v-model="form.confirmPassword" type="password" placeholder="请再次输入新密码" class="input" />
        </view>
        <view class="form-tip">
          <text>密码需 8-20 个字符，且同时包含字母和数字</text>
        </view>
        <view class="btn-row">
          <button class="ghost-btn" @click="active = 1">上一步</button>
          <button class="primary-btn flex1" :loading="submitting" @click="handleReset">重置密码</button>
        </view>
      </view>
    </view>

    <view class="back-login" @click="goLogin">
      <text>返回登录</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { sendResetCode, verifyResetCode, resetPassword } from '@/api/auth'

const stepLabels = ['验证手机号', '校验验证码', '设置新密码']

const active = ref(0)
const sending = ref(false)
const verifying = ref(false)
const submitting = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

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

function isValidPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

// 密码复杂度：8-20 字符且包含字母和数字（与后端一致）
function isValidPassword(pwd: string): boolean {
  return /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(pwd)
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
  const phone = form.value.phone.trim()
  if (!phone) {
    uni.showToast({ title: '请输入手机号', icon: 'none' }); return
  }
  if (!isValidPhone(phone)) {
    uni.showToast({ title: '手机号格式不正确', icon: 'none' }); return
  }
  sending.value = true
  try {
    await sendResetCode(phone)
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    startCountdown()
    if (active.value === 0) active.value = 1
  } catch {
    // 错误已在 request 中统一处理
  } finally {
    sending.value = false
  }
}

async function handleVerifyCode() {
  const code = form.value.code.trim()
  if (!code) {
    uni.showToast({ title: '请输入验证码', icon: 'none' }); return
  }
  if (!/^\d{6}$/.test(code)) {
    uni.showToast({ title: '验证码为 6 位数字', icon: 'none' }); return
  }
  verifying.value = true
  try {
    await verifyResetCode(form.value.phone.trim(), code)
    active.value = 2
  } catch {
    // 错误已在 request 中统一处理
  } finally {
    verifying.value = false
  }
}

async function handleReset() {
  const newPassword = form.value.newPassword
  if (!isValidPassword(newPassword)) {
    uni.showToast({ title: '密码需 8-20 位且含字母和数字', icon: 'none' }); return
  }
  if (newPassword !== form.value.confirmPassword) {
    uni.showToast({ title: '两次输入的密码不一致', icon: 'none' }); return
  }
  submitting.value = true
  try {
    await resetPassword(form.value.phone.trim(), form.value.code.trim(), newPassword)
    uni.showToast({ title: '密码重置成功', icon: 'success' })
    setTimeout(() => goLogin(), 1500)
  } catch {
    // 错误已在 request 中统一处理
  } finally {
    submitting.value = false
  }
}

function goLogin() {
  uni.reLaunch({ url: '/pages/login/index' })
}
</script>

<style scoped>
.forgot-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea, #764ba2);
  padding: 60rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.forgot-header {
  text-align: center;
  margin-bottom: 60rpx;
}
.page-title {
  font-size: 48rpx;
  color: #fff;
  font-weight: bold;
  display: block;
}
.page-subtitle {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 16rpx;
  display: block;
}
.steps {
  display: flex;
  justify-content: space-between;
  width: 100%;
  margin-bottom: 40rpx;
  padding: 0 20rpx;
}
.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}
.step-dot {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12rpx;
}
.step-dot.active {
  background: #fff;
}
.step-dot.done {
  background: #67c23a;
}
.step-no {
  font-size: 26rpx;
  color: #764ba2;
  font-weight: bold;
}
.step-label {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.7);
}
.step-label.active {
  color: #fff;
}
.forgot-form {
  width: 100%;
  background: #fff;
  border-radius: 16rpx;
  padding: 40rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.1);
}
.form-item {
  margin-bottom: 32rpx;
}
.input {
  height: 88rpx;
  border: 1rpx solid #dcdfe6;
  border-radius: 8rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
}
.form-tip {
  margin-bottom: 24rpx;
}
.form-tip text {
  font-size: 24rpx;
  color: #909399;
}
.code-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.code-input {
  flex: 1;
}
.code-btn {
  width: 160rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 24rpx;
  color: #409eff;
  background: #ecf5ff;
  border: 1rpx solid #b3d8ff;
  border-radius: 8rpx;
  padding: 0;
}
.code-btn[disabled] {
  color: #c0c4cc;
  background: #f5f7fa;
  border-color: #e4e7ed;
}
.btn-row {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
}
.flex1 {
  flex: 1;
}
.primary-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #409eff;
  color: #fff;
  font-size: 30rpx;
  border-radius: 8rpx;
  border: none;
  margin-top: 16rpx;
}
.primary-btn[disabled] {
  background: #a0cfff;
}
.ghost-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  background: #fff;
  color: #606266;
  font-size: 30rpx;
  border: 1rpx solid #dcdfe6;
  border-radius: 8rpx;
}
.back-login {
  margin-top: 40rpx;
}
.back-login text {
  font-size: 28rpx;
  color: #fff;
}
</style>
