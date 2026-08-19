import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@shared/api/auth-api'
import type { UserIdentity, MenuNode } from '@shared/types'
import { setToken, clearToken, getToken } from '@shared/api/http'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const userInfo = ref<UserIdentity | null>(null)
  const permissions = ref<string[]>([])
  const menus = ref<MenuNode[]>([])
  const isLogin = computed(() => !!token.value)

  async function login(form: { username: string; password: string }) {
    const res = await authApi.login(form)
    token.value = res.token
    setToken(res.token)
    userInfo.value = res.user
    permissions.value = res.user.permissions || []
    menus.value = res.user.menus || []
    return res
  }

  async function loadUserInfo() {
    if (!token.value) return null
    try {
      userInfo.value = await authApi.userInfo()
      permissions.value = userInfo.value?.permissions || []
      menus.value = userInfo.value?.menus || []
      return userInfo.value
    } catch (e) {
      clearToken()
      token.value = ''
      permissions.value = []
      menus.value = []
      throw e
    }
  }

  async function logout() {
    try { await authApi.logout() } catch { /* ignore */ }
    clearToken()
    token.value = ''
    userInfo.value = null
    permissions.value = []
    menus.value = []
  }

  function hasPerm(code: string): boolean {
    return permissions.value.includes(code)
  }

  return { token, userInfo, permissions, menus, isLogin, login, loadUserInfo, logout, hasPerm }
})
