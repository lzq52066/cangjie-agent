import { request } from '@shared/api/http'

export const modelApi = {
  list(keyword?: string, modelType?: string, providerId?: string) {
    return request<any[]>({ method: 'GET', url: '/model', params: { keyword, modelType, providerId } })
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

/** 厂商：统一维护 API Key 与 Base URL，模型通过 providerId 关联（API Key 为脱敏值，形如 ****abcd） */
export interface ModelProvider {
  id: string
  name: string
  code: string
  baseUrl?: string
  apiKey?: string
  status: string
  description?: string
}

export const modelProviderApi = {
  list(keyword?: string, status?: string) {
    return request<ModelProvider[]>({ method: 'GET', url: '/model-provider', params: { keyword, status } })
  },
  create(data: Partial<ModelProvider>) {
    return request<ModelProvider>({ method: 'POST', url: '/model-provider', data })
  },
  update(id: string, data: Partial<ModelProvider>) {
    return request<ModelProvider>({ method: 'PUT', url: `/model-provider/${id}`, data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/model-provider/${id}` })
  }
}
