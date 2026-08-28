<template>
  <view class="form-page">
    <view class="form-section">
      <view class="form-item">
        <text class="form-label">原密码</text>
        <input v-model="form.oldPassword" type="password" placeholder="请输入原密码" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">新密码</text>
        <input v-model="form.newPassword" type="password" placeholder="请输入新密码" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">确认密码</text>
        <input v-model="form.confirmPassword" type="password" placeholder="请再次输入新密码" class="form-input" />
      </view>
    </view>

    <view class="tips">
      <text class="tip-text">密码长度至少6位，建议包含字母和数字</text>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">确认修改</button>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const submitting = ref(false)
const form = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

async function handleSubmit() {
  if (!form.value.oldPassword) {
    uni.showToast({ title: '请输入原密码', icon: 'none' }); return
  }
  if (!form.value.newPassword) {
    uni.showToast({ title: '请输入新密码', icon: 'none' }); return
  }
  if (form.value.newPassword.length < 6) {
    uni.showToast({ title: '新密码至少6位', icon: 'none' }); return
  }
  if (form.value.newPassword !== form.value.confirmPassword) {
    uni.showToast({ title: '两次密码不一致', icon: 'none' }); return
  }
  submitting.value = true
  try {
    await changePassword({
      oldPassword: form.value.oldPassword,
      newPassword: form.value.newPassword
    })
    uni.showToast({ title: '修改成功', icon: 'success' })
    setTimeout(() => {
      userStore.logout()
    }, 1500)
  } catch {} finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.form-page { padding: 20rpx; }
.form-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-lg); padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid var(--zw-border-light); }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; }
.tips { padding: 12rpx 24rpx; }
.tip-text { font-size: 24rpx; color: var(--zw-text-tertiary); }
.submit-btn { margin: 40rpx 20rpx; height: 88rpx; line-height: 88rpx; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 32rpx; font-weight: 600; border-radius: var(--zw-radius-sm); border: none; } /* 橙底深字承重规则 */
</style>
