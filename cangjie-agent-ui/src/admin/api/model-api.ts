import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'
import { OPTION_PAGE_SIZE } from '@shared/types'

export interface ModelQuery extends PageQuery {
  modelType?: string
  providerId?: string
}

export const modelApi = {
  list(query: ModelQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: '/model', params: query })
  },
  /** 供下拉选择使用的精简列表 */
  async options(providerId?: string) {
    const res = await modelApi.list({ pageSize: OPTION_PAGE_SIZE, providerId })
    return res?.list ?? []
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

export interface ModelProviderQuery extends PageQuery {
  status?: string
}

export const modelProviderApi = {
  list(query: ModelProviderQuery = {}) {
    return request<PageResult<ModelProvider>>({ method: 'GET', url: '/model-provider', params: query })
  },
  /** 供下拉选择使用的精简列表 */
  async options() {
    const res = await modelProviderApi.list({ pageSize: OPTION_PAGE_SIZE })
    return res?.list ?? []
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
