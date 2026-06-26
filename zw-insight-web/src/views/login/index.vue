<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="login-title">中维智营</h2>
      <p class="login-subtitle">工程项目管理平台</p>
      <el-form :model="loginForm" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div style="display: flex; gap: 12px; width: 100%">
            <el-input v-model="loginForm.captchaCode" placeholder="验证码" prefix-icon="Key" style="flex: 1" />
            <img :src="captchaImage" @click="refreshCaptcha" class="captcha-img" alt="验证码" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
        <div class="login-extra">
          <el-link type="primary" :underline="false" @click="goForgotPassword">忘记密码？</el-link>
        </div>
      </el-form>
    </el-card>
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
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 420px;
  padding: 20px;
}
.login-title {
  text-align: center;
  margin-bottom: 4px;
  font-size: 24px;
  color: #303133;
}
.login-subtitle {
  text-align: center;
  color: #909399;
  margin-bottom: 30px;
}
.captcha-img {
  width: 120px;
  height: 32px;
  cursor: pointer;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.login-extra {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  margin-top: -8px;
}
</style>
