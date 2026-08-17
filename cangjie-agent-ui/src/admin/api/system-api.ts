import { request } from '@shared/api/http'

/** 系统设置类型：1-基础设置 2-邮件设置 3-对话设置 */
export const SETTING_TYPE = {
  BASE: 1,
  EMAIL: 2,
  CHAT: 3
} as const

export const systemApi = {
  info() {
    return request<Record<string, any>>({ method: 'GET', url: '/system/info' })
  },
  getSetting(type: number) {
    return request<Record<string, any>>({ method: 'GET', url: `/system/setting/${type}` })
  },
  saveSetting(type: number, meta: Record<string, any>) {
    return request<void>({ method: 'PUT', url: `/system/setting/${type}`, data: meta })
  }
}
