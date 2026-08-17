import { request } from '@shared/api/http'

export const modelApi = {
  list(keyword?: string, modelType?: string) {
    return request<any[]>({ method: 'GET', url: '/model', params: { keyword, modelType } })
  },
  get(id: string) {
    return request<any>({ method: 'GET', url: `/model/${id}` })
  },
  create(data: any) {
    return request<any>({ method: 'POST', url: '/model', data })
  },
  update(id: string, data: any) {
    return request<any>({ method: 'PUT', url: `/model/${id}`, data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/model/${id}` })
  },
  test(modelId: string, message?: string) {
    return request<string>({ method: 'POST', url: '/model/test', data: { modelId, message } })
  },
  setDefault(id: string) {
    return request<void>({ method: 'PUT', url: `/model/${id}/default` })
  }
}
