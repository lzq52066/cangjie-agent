import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@shared/api/auth-api'
import type { UserIdentity } from '@shared/types'
import { setToken, clearToken, getToken } from '@shared/api/http'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const userInfo = ref<UserIdentity | null>(null)
  const isLogin = computed(() => !!token.value)

  async function login(form: { username: string; password: string }) {
    const res = await authApi.login(form)
    token.value = res.token
    setToken(res.token)
    userInfo.value = res.user
    return res
  }

  async function loadUserInfo() {
    if (!token.value) return null
    try {
      userInfo.value = await authApi.userInfo()
      return userInfo.value
    } catch (e) {
      clearToken()
      token.value = ''
      throw e
    }
  }

  async function logout() {
    try { await authApi.logout() } catch { /* ignore */ }
    clearToken()
    token.value = ''
    userInfo.value = null
  }

  return { token, userInfo, isLogin, login, loadUserInfo, logout }
})
