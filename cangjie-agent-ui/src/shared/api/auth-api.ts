import { request } from './http'
import type { LoginResult, UserIdentity } from '../types'

export const authApi = {
  login(data: { username: string; password: string; code?: string }) {
    return request<LoginResult>({
      method: 'POST',
      url: '/auth/login',
      baseURL: '/api/open',
      data
    })
  },
  logout() {
    return request<void>({
      method: 'POST',
      url: '/auth/logout',
      baseURL: '/api/open'
    })
  },
  userInfo() {
    return request<UserIdentity>({
      method: 'GET',
      url: '/user/info'
    })
  },
  keepAlive() {
    return request<boolean>({ method: 'GET', url: '/auth/keep-alive' })
  },
  systemInfo() {
    return request<{ name: string; version: string; domain: string; time: number }>({
      method: 'GET',
      url: '/system/info'
    })
  }
}
