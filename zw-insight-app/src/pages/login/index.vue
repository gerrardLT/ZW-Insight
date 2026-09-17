<template>
  <view class="login-page">
    <!-- 品牌铭牌（设计文档：登录页 = 表单 + 品牌铭牌，不渲染 hero-band） -->
    <view class="login-header">
		<!-- 品牌背景（2026-09-16 AI 生成：夜景工地线稿，弱透明置于铭牌后；09-17 JPEG q72 压缩 178KB→48KB） -->
		<image class="brand-bg" src="/static/brand/login-bg.jpg" mode="aspectFill" />
      <view class="brand-mark"><image class="brand-mark-img" src="/static/brand/logo.png" mode="aspectFit" /></view>
      <text class="app-title">中维智营</text>
      <text class="app-subtitle eyebrow-cap">Engineering Project Management</text>
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
        <view class="form-item captcha-item">
          <input v-model="passwordForm.captchaCode" placeholder="请输入验证码" class="input captcha-input" maxlength="4" />
          <image v-if="captchaImage" :src="captchaImage" class="captcha-img" mode="aspectFit" @click="refreshCaptcha" />
          <view v-else class="captcha-img captcha-placeholder" @click="refreshCaptcha"><text>点击加载</text></view>
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

      <!-- 忘记密码入口 -->
      <view class="login-extra">
        <text class="forgot-link" @click="goForgotPassword">忘记密码？</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { useNetworkStore } from '@/stores/network'
import { login, sendSmsCaptcha, getImageCaptcha } from '@/api/auth'
import { offlineCache } from '@/utils/offlineCache'

const userStore = useUserStore()
const loading = ref(false)
const loginMode = ref<'password' | 'sms'>('password')

// 密码登录表单（后端开启验证码时密码登录必带 captchaUuid/captchaCode）
const passwordForm = ref({ tenantCode: '', username: '', password: '', captchaCode: '' })

// 图形验证码（CaptchaController GET /image，imageBase64 带 data:image/png 前缀可直接绑定）
const captchaImage = ref('')
const captchaUuid = ref('')

async function refreshCaptcha() {
  try {
    const res: any = await getImageCaptcha()
    captchaUuid.value = res.data?.uuid || ''
    captchaImage.value = res.data?.imageBase64 || ''
  } catch {
    // 加载失败展示占位，点击重试；不阻断页面（与 PC 端范式一致）
    captchaImage.value = ''
  }
}

onMounted(() => { refreshCaptcha() })

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

// 跳转忘记密码页
function goForgotPassword() {
  uni.navigateTo({ url: '/pages/login/forgot-password' })
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

// 登录成功后补触发离线缓存初始同步（需求 4.1）：
// App.vue 仅在冷启动且已有 token 时同步，SPA 会话内登录成功不经过该时机，
// 导致首次登录后离线缓存缺失（2026-08-29 H5 走查实证）
function syncOfflineCacheAfterLogin() {
  if (useNetworkStore().isOffline) return
  offlineCache.sync().catch((e) => {
    console.warn('[offline] 登录后初始同步失败，下次冷启动重试', e)
  })
}

// 密码登录（验证码一次性消费，失败后后端会要求新验证码，故失败必刷新）
async function handlePasswordLogin() {
  if (!passwordForm.value.username || !passwordForm.value.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  if (!passwordForm.value.captchaCode) {
    uni.showToast({ title: '请输入验证码', icon: 'none' })
    return
  }
  if (!captchaUuid.value) {
    uni.showToast({ title: '验证码未加载，请点击验证码图片重试', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res: any = await login({
      ...passwordForm.value,
      captchaUuid: captchaUuid.value,
      loginType: 'PASSWORD'
    })
    userStore.setToken(res.data.token)
    userStore.setUserInfo(res.data)
    syncOfflineCacheAfterLogin()
    uni.switchTab({ url: '/pages/home/index' })
  } catch (e) {
    // 错误已在 request 中统一处理；验证码已被消费或错误，刷新新图
    passwordForm.value.captchaCode = ''
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

// 短信验证码登录
async function handleSmsLogin() {  const phone = smsForm.value.phone.trim()
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
    syncOfflineCacheAfterLogin()
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
  background: var(--zw-bg-sidebar); /* 石墨黑画布（去渐变纪律） */
  padding: 40rpx;
}
.login-header {
  position: relative;
  overflow: hidden;
}
.brand-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0.32;
}
.login-header > .brand-mark, .login-header > .app-title, .login-header > .app-subtitle {
  position: relative;
  z-index: 1;
}
.login-header {
  text-align: center;
  margin-bottom: 64rpx;
}
.brand-mark {
  width: 168rpx;
  height: 96rpx;
  margin: 0 auto 24rpx;
  background: #fff; /* 暗底登录头：白 chip 承载透明 logo（预览验证过的承载方式） */
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--zw-radius-xs);
}
.brand-mark-img {
  width: 140rpx;
  height: 76rpx;
}
.app-title {
  font-size: 48rpx;
  color: #fff;
  font-weight: bold;
  letter-spacing: 8rpx;
  display: block;
}
.app-subtitle {
  margin-top: 16rpx;
  display: block;
  color: rgba(255, 255, 255, 0.6);
}
.login-form {
  width: 100%;
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border);
  border-radius: var(--zw-radius-xs); /* 精密直角纪律 */
  padding: 40rpx;
  box-shadow: var(--zw-shadow-card);
}
.login-tabs {
  display: flex;
  margin-bottom: 40rpx;
  border-bottom: 1rpx solid var(--zw-border-light);
}
.tab-item {
  flex: 1;
  text-align: center;
  padding-bottom: 20rpx;
  font-size: 28rpx;
  color: var(--zw-text-tertiary);
  position: relative;
}
.tab-item.active {
  color: var(--zw-brand);
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
  background: var(--zw-brand);
}
.form-item {
  margin-bottom: 32rpx;
}
.input {
  height: 88rpx; /* Mobile Shell 章：输入控件 44px */
  border: 1rpx solid var(--zw-border);
  border-radius: var(--zw-radius-xs);
  padding: 0 24rpx;
  font-size: 28rpx;
}
.sms-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.captcha-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.captcha-input {
  flex: 1;
}
.captcha-img {
  width: 220rpx;
  height: 88rpx;
  border: 1rpx solid var(--zw-border);
  border-radius: var(--zw-radius-xs);
  background: var(--zw-bg-hover);
}
.captcha-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  color: var(--zw-text-tertiary);
}
.sms-input {
  flex: 1;
}
.sms-btn {
  width: 220rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 24rpx;
  color: var(--zw-brand);
  background: var(--zw-brand-light);
  border: 1rpx solid var(--zw-brand-light);
  border-radius: var(--zw-radius-xs);
  padding: 0;
  white-space: nowrap;
}
.sms-btn[disabled] {
  color: var(--zw-text-quaternary);
  background: var(--zw-bg-hover);
  border-color: var(--zw-border-light);
}
.login-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: var(--zw-brand);
  color: var(--zw-on-primary); /* 橙底深字承重规则 */
  font-size: 32rpx;
  font-weight: 600;
  border-radius: var(--zw-radius-xs);
  margin-top: 40rpx;
  border: none;
}
.login-btn:active {
  background: var(--zw-brand-active);
  transform: translateY(2rpx); /* 压合反馈，150ms 预算内 */
}
.login-extra {
  text-align: center;
  margin-top: 24rpx;
}
.forgot-link {
  font-size: 26rpx;
  color: var(--zw-brand);
}
</style>
