import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(uni.getStorageSync('token') || '')
  // 冷启动从存储恢复（D3 修复）：保证 App.vue 启动时 offlineCache.sync() 能读到真实 userInfo，
  // 否则 USER_INFO 离线缓存永远为空，离线态「我的」页/水印人员字段全部缺失
  const userInfo = ref<any>(uni.getStorageSync('userInfo') || null)

  function setToken(val: string) {
    token.value = val
    uni.setStorageSync('token', val)
  }

  function setUserInfo(info: any) {
    userInfo.value = info
    if (info) {
      uni.setStorageSync('userInfo', info)
    } else {
      uni.removeStorageSync('userInfo')
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
    uni.reLaunch({ url: '/pages/login/index' })
  }

  return { token, userInfo, setToken, setUserInfo, logout }
})
