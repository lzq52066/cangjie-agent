import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'
import { OPTION_PAGE_SIZE } from '@shared/types'

export interface ToolQuery extends PageQuery {
  type?: string
}

export const toolApi = {
  list(query: ToolQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: '/tool', params: query })
  },
  /** 供下拉选择使用的精简列表 */
  async options(type?: string) {
    const res = await toolApi.list({ pageSize: OPTION_PAGE_SIZE, type })
    return res?.list ?? []
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
  list(query: ToolQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: '/plugin', params: query })
  },
  /** 供下拉选择使用的精简列表 */
  async options(type?: string) {
    const res = await pluginApi.list({ pageSize: OPTION_PAGE_SIZE, type })
    return res?.list ?? []
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
