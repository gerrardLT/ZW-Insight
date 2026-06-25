<template>
  <view class="login-page">
    <view class="login-header">
      <text class="app-title">中维智营</text>
      <text class="app-subtitle">工程项目管理平台</text>
    </view>
    <view class="login-form">
      <!-- Tab 切换 -->
      <view class="login-tabs">
        <view class="tab-item" :class="{ active: loginMode === 'password' }" @click="loginMode = 'password'">
          <text>密码登录</text>
        </view>
        <view class="tab-item" :class="{ active: loginMode === 'sms' }" @click="loginMode = 'sms'">
          <text>短信验证码登录</text>
        </view>
      </view>

      <!-- 密码登录表单 -->
      <view v-if="loginMode === 'password'">
        <view class="form-item">
          <input v-model="passwordForm.tenantCode" placeholder="请输入组织码" class="input" />
        </view>
        <view class="form-item">
          <input v-model="passwordForm.username" placeholder="请输入用户名" class="input" />
        </view>
        <view class="form-item">
          <input v-model="passwordForm.password" type="password" placeholder="请输入密码" class="input" />
        </view>
        <button class="login-btn" :loading="loading" @click="handlePasswordLogin">登 录</button>
      </view>

      <!-- 短信验证码登录表单 -->
      <view v-if="loginMode === 'sms'">
        <view class="form-item">
          <input v-model="smsForm.tenantCode" placeholder="请输入组织码" class="input" />
        </view>
        <view class="form-item">
          <input v-model="smsForm.phone" type="number" placeholder="请输入手机号" class="input" maxlength="11" />
        </view>
        <view class="form-item sms-item">
          <input v-model="smsForm.smsCode" type="number" placeholder="请输入验证码" class="input sms-input" maxlength="6" />
          <button
            class="sms-btn"
            :disabled="smsCooldown > 0"
            @click="handleSendSms"
          >
            {{ smsCooldown > 0 ? `${smsCooldown}s后重发` : '获取验证码' }}
          </button>
        </view>
        <button class="login-btn" :loading="loading" @click="handleSmsLogin">登 录</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { login, sendSmsCaptcha } from '@/api/auth'

const userStore = useUserStore()
const loading = ref(false)
const loginMode = ref<'password' | 'sms'>('password')

// 密码登录表单
const passwordForm = ref({ tenantCode: '', username: '', password: '' })

// 短信登录表单
const smsForm = ref({ tenantCode: '', phone: '', smsCode: '' })

// 短信倒计时
const smsCooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

function startCooldown() {
  smsCooldown.value = 60
  cooldownTimer = setInterval(() => {
    smsCooldown.value--
    if (smsCooldown.value <= 0) {
      clearInterval(cooldownTimer!)
      cooldownTimer = null
    }
  }, 1000)
}

onUnmounted(() => {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
  }
})

// 手机号格式校验
function isValidPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

// 发送短信验证码
async function handleSendSms() {
  const phone = smsForm.value.phone.trim()
  if (!phone) {
    uni.showToast({ title: '请输入手机号', icon: 'none' })
    return
  }
  if (!isValidPhone(phone)) {
    uni.showToast({ title: '手机号格式不正确', icon: 'none' })
    return
  }
  try {
    await sendSmsCaptcha(phone)
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    startCooldown()
  } catch (e) {
    // 错误已在 request 中统一处理
  }
}

// 密码登录
async function handlePasswordLogin() {
  if (!passwordForm.value.username || !passwordForm.value.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res: any = await login({
      ...passwordForm.value,
      loginType: 'PASSWORD'
    })
    userStore.setToken(res.data.token)
    userStore.setUserInfo(res.data)
    uni.switchTab({ url: '/pages/home/index' })
  } catch (e) {
    // 错误已在 request 中统一处理
  } finally {
    loading.value = false
  }
}

// 短信验证码登录
async function handleSmsLogin() {
  const phone = smsForm.value.phone.trim()
  const smsCode = smsForm.value.smsCode.trim()
  if (!phone) {
    uni.showToast({ title: '请输入手机号', icon: 'none' })
    return
  }
  if (!isValidPhone(phone)) {
    uni.showToast({ title: '手机号格式不正确', icon: 'none' })
    return
  }
  if (!smsCode) {
    uni.showToast({ title: '请输入验证码', icon: 'none' })
    return
  }
  if (smsCode.length !== 6) {
    uni.showToast({ title: '验证码为6位数字', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res: any = await login({
      phone,
      smsCode,
      loginType: 'SMS',
      tenantCode: smsForm.value.tenantCode || undefined
    })
    userStore.setToken(res.data.token)
    userStore.setUserInfo(res.data)
    uni.switchTab({ url: '/pages/home/index' })
  } catch (e) {
    // 错误已在 request 中统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea, #764ba2);
  padding: 40rpx;
}
.login-header {
  text-align: center;
  margin-bottom: 80rpx;
}
.app-title {
  font-size: 56rpx;
  color: #fff;
  font-weight: bold;
  display: block;
}
.app-subtitle {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 16rpx;
  display: block;
}
.login-form {
  width: 100%;
  background: #fff;
  border-radius: 16rpx;
  padding: 40rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.1);
}
.login-tabs {
  display: flex;
  margin-bottom: 40rpx;
  border-bottom: 1rpx solid #ebeef5;
}
.tab-item {
  flex: 1;
  text-align: center;
  padding-bottom: 20rpx;
  font-size: 28rpx;
  color: #909399;
  position: relative;
}
.tab-item.active {
  color: #409eff;
  font-weight: bold;
}
.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 60%;
  height: 4rpx;
  background: #409eff;
  border-radius: 2rpx;
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
.sms-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.sms-input {
  flex: 1;
}
.sms-btn {
  width: 220rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 24rpx;
  color: #409eff;
  background: #ecf5ff;
  border: 1rpx solid #b3d8ff;
  border-radius: 8rpx;
  padding: 0;
  white-space: nowrap;
}
.sms-btn[disabled] {
  color: #c0c4cc;
  background: #f5f7fa;
  border-color: #e4e7ed;
}
.login-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #409eff;
  color: #fff;
  font-size: 32rpx;
  border-radius: 8rpx;
  margin-top: 40rpx;
  border: none;
}
</style>
