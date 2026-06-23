import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<any>(null)
  const menus = ref<any[]>([])
  const permissions = ref<string[]>([])

  function setToken(val: string) {
    token.value = val
    localStorage.setItem('token', val)
  }

  function setUserInfo(info: any) {
    userInfo.value = info
  }

  function setMenus(data: any[]) {
    menus.value = data
  }

  function setPermissions(perms: string[]) {
    permissions.value = perms
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    menus.value = []
    permissions.value = []
    localStorage.removeItem('token')
  }

  return { token, userInfo, menus, permissions, setToken, setUserInfo, setMenus, setPermissions, logout }
}, {
  persist: true
})
