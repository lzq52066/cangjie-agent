import axios, { AxiosInstance, AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { R } from '../types'

const TOKEN_KEY = 'cangjie-token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export async function request<T = any>(config: AxiosRequestConfig & { baseURL?: string }): Promise<T> {
  const instance = axios.create({
    baseURL: config.baseURL || '/api/admin',
    timeout: 60000,
    withCredentials: true
  })
  const token = getToken()
  if (token) {
    instance.interceptors.request.use(c => {
      c.headers = c.headers || {}
      c.headers.Authorization = token.startsWith('Bearer ') ? token : 'Bearer ' + token
      return c
    })
  }
  instance.interceptors.response.use(
    (response: any) => {
      const d: R<any> = response.data
      if (d && typeof d.code !== 'undefined') {
        if (d.code === 200) return d.data
        if (d.code === 401) {
          clearToken()
          setTimeout(() => location.reload(), 800)
        }
        ElMessage.error(d.msg || '请求失败')
        return Promise.reject(new Error(d.msg))
      }
      return response.data
    },
    (error: any) => {
      ElMessage.error(error?.response?.data?.msg || error.message || '网络错误')
      return Promise.reject(error)
    }
  )
  return instance.request(config as any) as any
}
