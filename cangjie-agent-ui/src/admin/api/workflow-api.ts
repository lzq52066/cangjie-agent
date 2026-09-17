import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'
import { OPTION_PAGE_SIZE } from '@shared/types'

export const workflowApi = {
  list(query: PageQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: '/workflow', params: query })
  },
  /** 供下拉选择使用的精简列表 */
  async options() {
    const res = await workflowApi.list({ pageSize: OPTION_PAGE_SIZE })
    return res?.list ?? []
  },
  get(id: string) {
    return request<any>({ method: 'GET', url: `/workflow/${id}` })
  },
  create(data: any) {
    return request<any>({ method: 'POST', url: '/workflow', data })
  },
  update(id: string, data: any) {
    return request<any>({ method: 'PUT', url: `/workflow/${id}`, data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/workflow/${id}` })
  },
  publish(id: string) {
    return request<any>({ method: 'POST', url: `/workflow/${id}/publish` })
  },
  execute(id: string, inputs: Record<string, any>) {
    return request<any>({ method: 'POST', url: `/workflow/${id}/execute`, data: { inputs } })
  },
  executions(id: string, query: PageQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: `/workflow/${id}/executions`, params: query })
  },
  execution(executionId: string) {
    return request<any>({ method: 'GET', url: `/workflow/execution/${executionId}` })
  },
  /** 已注册的节点类型编码 */
  nodeTypes() {
    return request<string[]>({ method: 'GET', url: '/workflow/node-types' })
  },
  /** 已注册的节点元信息（type/name/description） */
  nodes() {
    return request<any[]>({ method: 'GET', url: '/workflow/nodes' })
  }
}
