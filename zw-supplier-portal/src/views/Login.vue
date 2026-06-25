<template>
  <div class="min-h-screen bg-gray-50 flex items-center justify-center p-4">
    <div class="bg-white rounded-lg shadow-md w-full max-w-sm p-8">
      <h1 class="text-2xl font-bold text-center text-gray-800 mb-2">供应商报价平台</h1>
      <p class="text-center text-gray-500 text-sm mb-8">ZW-Insight 工程项目管理系统</p>
      <div class="space-y-4">
        <div>
          <label class="block text-sm text-gray-600 mb-1">手机号</label>
          <input v-model="phone" type="tel" maxlength="11" placeholder="请输入手机号"
            class="w-full px-4 py-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
        </div>
        <div class="flex gap-2">
          <input v-model="code" type="text" maxlength="6" placeholder="验证码"
            class="flex-1 px-4 py-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          <button @click="handleSendCode" :disabled="countdown > 0"
            class="px-4 py-3 bg-gray-100 text-gray-700 rounded-lg text-sm whitespace-nowrap disabled:opacity-50">
            {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
          </button>
        </div>
        <button @click="handleLogin" :disabled="loading"
          class="w-full py-3 bg-blue-500 text-white rounded-lg font-medium hover:bg-blue-600 disabled:opacity-50">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { sendCode, login } from '../api'

const router = useRouter()
const phone = ref(''); const code = ref(''); const loading = ref(false); const countdown = ref(0)

async function handleSendCode() {
  if (!phone.value || phone.value.length !== 11) { alert('请输入正确的手机号'); return }
  try { await sendCode(phone.value); countdown.value = 60; const t = setInterval(() => { countdown.value--; if (countdown.value <= 0) clearInterval(t) }, 1000) } catch { alert('发送失败') }
}

async function handleLogin() {
  if (!phone.value || !code.value) { alert('请填写手机号和验证码'); return }
  loading.value = true
  try { const res: any = await login(phone.value, code.value); localStorage.setItem('supplier_token', res.data.token); router.push('/inquiry') } catch { alert('登录失败，请检查验证码') } finally { loading.value = false }
}
</script>
