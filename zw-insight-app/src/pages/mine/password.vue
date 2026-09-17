<template>
  <!-- ZwiFormPage 壳：sticky 底部提交条（critique P0 拇指区）；校验链与提交逻辑原样保留 -->
  <ZwiFormPage submitText="确认修改" :loading="submitting" @submit="handleSubmit">
    <view class="form-card">
      <ZwiField
        label="原密码"
        v-model="form.oldPassword"
        inputType="password"
        placeholder="请输入原密码"
        required
      />
      <!-- 原页底 .tips 游离提示改为常驻 hint（critique P2：辅助文案不靠 placeholder/浮提示承载） -->
      <ZwiField
        label="新密码"
        v-model="form.newPassword"
        inputType="password"
        placeholder="请输入新密码"
        required
        hint="长度至少 6 位，建议包含字母和数字"
      />
      <ZwiField
        label="确认密码"
        v-model="form.confirmPassword"
        inputType="password"
        placeholder="请再次输入新密码"
        required
      />
    </view>
  </ZwiFormPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import ZwiFormPage from '@/components/zwi/ZwiFormPage.vue'
import ZwiField from '@/components/zwi/ZwiField.vue'

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
/* 表单卡壳：Stage 2 收敛后的通用容器（与 material/* 同构） */
.form-card {
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  overflow: hidden;
}
.form-card :deep(.form-item) {
  padding: 20rpx 24rpx;
  margin-bottom: 0;
  border-bottom: 1rpx solid var(--zw-border-light);
}
.form-card :deep(.form-item:last-child) {
  border-bottom: none;
}
</style>
