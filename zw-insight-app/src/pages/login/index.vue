<template>
  <view class="login-page">
    <view class="login-header">
      <text class="app-title">中维智营</text>
      <text class="app-subtitle">工程项目管理平台</text>
    </view>
    <view class="login-form">
      <view class="form-item">
        <input v-model="form.tenantCode" placeholder="请输入组织码" class="input" />
      </view>
      <view class="form-item">
        <input v-model="form.username" placeholder="请输入用户名" class="input" />
      </view>
      <view class="form-item">
        <input v-model="form.password" type="password" placeholder="请输入密码" class="input" />
      </view>
      <button class="login-btn" :loading="loading" @click="handleLogin">登 录</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { login } from '@/api/auth'

const userStore = useUserStore()
const loading = ref(false)
const form = ref({ tenantCode: '', username: '', password: '' })

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res: any = await login(form.value)
    userStore.setToken(res.data.token)
    userStore.setUserInfo(res.data)
    uni.switchTab({ url: '/pages/home/index' })
  } catch (e) {
    // 错误已在request中处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; background: linear-gradient(135deg, #667eea, #764ba2); padding: 40rpx; }
.login-header { text-align: center; margin-bottom: 80rpx; }
.app-title { font-size: 56rpx; color: #fff; font-weight: bold; display: block; }
.app-subtitle { font-size: 28rpx; color: rgba(255,255,255,0.8); margin-top: 16rpx; display: block; }
.login-form { width: 100%; background: #fff; border-radius: 16rpx; padding: 60rpx 40rpx; box-shadow: 0 8rpx 32rpx rgba(0,0,0,0.1); }
.form-item { margin-bottom: 32rpx; }
.input { height: 88rpx; border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 0 24rpx; font-size: 28rpx; }
.login-btn { width: 100%; height: 88rpx; line-height: 88rpx; background: #409eff; color: #fff; font-size: 32rpx; border-radius: 8rpx; margin-top: 40rpx; border: none; }
</style>
