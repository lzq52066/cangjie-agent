import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'
import { OPTION_PAGE_SIZE } from '@shared/types'

export interface ChannelQuery extends PageQuery {
  type?: string
}

export const channelApi = {
  list(query: ChannelQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: '/channel', params: query })
  },
  /** 供下拉选择使用的精简列表 */
  async options(type?: string) {
    const res = await channelApi.list({ pageSize: OPTION_PAGE_SIZE, type })
    return res?.list ?? []
  },
  get(id: string) {
    return request<any>({ method: 'GET', url: `/channel/${id}` })
  },
  create(data: any) {
    return request<any>({ method: 'POST', url: '/channel', data })
  },
  update(id: string, data: any) {
    return request<any>({ method: 'PUT', url: `/channel/${id}`, data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/channel/${id}` })
  },
  enable(id: string) {
    return request<any>({ method: 'POST', url: `/channel/${id}/enable` })
  },
  disable(id: string) {
    return request<any>({ method: 'POST', url: `/channel/${id}/disable` })
  },
  messages(id: string, query: PageQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: `/channel/${id}/messages`, params: query })
  }
}
