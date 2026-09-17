import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'
import { OPTION_PAGE_SIZE } from '@shared/types'

export interface ApplicationQuery extends PageQuery {
  type?: string
}

export const applicationApi = {
  list(query: ApplicationQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: '/application', params: query })
  },
  /** 供下拉选择使用的精简列表 */
  async options(type?: string) {
    const res = await applicationApi.list({ pageSize: OPTION_PAGE_SIZE, type })
    return res?.list ?? []
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
  },
  /** 版本分页列表 */
  versions(applicationId: string, query: PageQuery = {}) {
    return request<PageResult<any>>({
      method: 'GET',
      url: `/application/version/application/${applicationId}`,
      params: query
    })
  }
}

/** 应用模板（Bundle 套件） */
export interface TemplateQuery extends PageQuery {
  category?: string
}

export const applicationTemplateApi = {
  list(query: TemplateQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: '/application-template', params: query })
  },
  async options(category?: string) {
    const res = await applicationTemplateApi.list({ pageSize: OPTION_PAGE_SIZE, category })
    return res?.list ?? []
  },
  saveFromApplication(data: any) {
    return request<any>({ method: 'POST', url: '/application-template/from-application', data })
  },
  createFromTemplate(id: string, appName?: string) {
    return request<any>({ method: 'POST', url: `/application-template/${id}/create-app`, params: { appName } })
  },
  exportBundle(id: string) {
    return request<string>({ method: 'GET', url: `/application-template/${id}/export` })
  },
  importBundle(data: any) {
    return request<any>({ method: 'POST', url: '/application-template/import', data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/application-template/${id}` })
  }
}
