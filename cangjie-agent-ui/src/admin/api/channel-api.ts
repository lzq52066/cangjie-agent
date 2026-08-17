import { request } from '@shared/api/http'

export const channelApi = {
  list(keyword?: string, type?: string) {
    return request<any[]>({ method: 'GET', url: '/channel', params: { keyword, type } })
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
  messages(id: string) {
    return request<any[]>({ method: 'GET', url: `/channel/${id}/messages` })
  }
}
