import { request } from '@shared/api/http'

export const applicationApi = {
  list(keyword?: string, type?: string) {
    return request<any[]>({ method: 'GET', url: '/application', params: { keyword, type } })
  },
  get(id: string) {
    return request<any>({ method: 'GET', url: `/application/${id}` })
  },
  create(data: any) {
    return request<any>({ method: 'POST', url: '/application', data })
  },
  update(id: string, data: any) {
    return request<any>({ method: 'PUT', url: `/application/${id}`, data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/application/${id}` })
  },
  publish(id: string) {
    return request<any>({ method: 'POST', url: `/application/${id}/publish` })
  },
  getByApikey(apikey: string) {
    return request<any>({ method: 'GET', url: `/application/apikey/${apikey}` })
  }
}
