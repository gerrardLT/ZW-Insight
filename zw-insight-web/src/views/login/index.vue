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
        <el-form-item prop="captcha">
          <div style="display: flex; gap: 12px; width: 100%">
            <el-input v-model="loginForm.captcha" placeholder="验证码" prefix-icon="Key" style="flex: 1" />
            <img :src="captchaUrl" @click="refreshCaptcha" class="captcha-img" alt="验证码" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import type { FormInstance } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaUrl = ref('')
const captchaKey = ref('')

const loginForm = ref({
  username: '',
  password: '',
  captcha: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

function refreshCaptcha() {
  captchaKey.value = Date.now().toString()
  captchaUrl.value = `/api/v1/auth/captcha?key=${captchaKey.value}`
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const res: any = await request.post('/v1/auth/login', {
      username: loginForm.value.username,
      password: loginForm.value.password,
      captcha: loginForm.value.captcha,
      captchaKey: captchaKey.value
    })
    userStore.setToken(res.data.token)
    userStore.setUserInfo(res.data.userInfo)
    router.push('/')
  } finally {
    loading.value = false
    refreshCaptcha()
  }
}

onMounted(() => {
  refreshCaptcha()
})
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
</style>
