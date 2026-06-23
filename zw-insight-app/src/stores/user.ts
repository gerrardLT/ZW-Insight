import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(uni.getStorageSync('token') || '')
  const userInfo = ref<any>(null)

  function setToken(val: string) {
    token.value = val
    uni.setStorageSync('token', val)
  }

  function setUserInfo(info: any) {
    userInfo.value = info
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    uni.removeStorageSync('token')
    uni.reLaunch({ url: '/pages/login/index' })
  }

  return { token, userInfo, setToken, setUserInfo, logout }
})
