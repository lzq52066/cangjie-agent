import { request } from '@shared/api/http'

export const toolApi = {
  list(keyword?: string, type?: string) {
    return request<any[]>({ method: 'GET', url: '/tool', params: { keyword, type } })
  },
  get(id: string) {
    return request<any>({ method: 'GET', url: `/tool/${id}` })
  },
  create(data: any) {
    return request<any>({ method: 'POST', url: '/tool', data })
  },
  update(id: string, data: any) {
    return request<any>({ method: 'PUT', url: `/tool/${id}`, data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/tool/${id}` })
  },
  /** 执行工具，input 为参数键值对 */
  execute(id: string, input: Record<string, any>) {
    return request<any>({ method: 'POST', url: `/tool/${id}/execute`, data: { input } })
  }
}

export const pluginApi = {
  list(keyword?: string, type?: string) {
    return request<any[]>({ method: 'GET', url: '/plugin', params: { keyword, type } })
  },
  get(id: string) {
    return request<any>({ method: 'GET', url: `/plugin/${id}` })
  },
  create(data: any) {
    return request<any>({ method: 'POST', url: '/plugin', data })
  },
  update(id: string, data: any) {
    return request<any>({ method: 'PUT', url: `/plugin/${id}`, data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/plugin/${id}` })
  },
  reload(id: string) {
    return request<any>({ method: 'POST', url: `/plugin/${id}/reload` })
  },
  /** 扫描已注册的插件（SPI 发现） */
  scan() {
    return request<any[]>({ method: 'GET', url: '/plugin/scan' })
  }
}
