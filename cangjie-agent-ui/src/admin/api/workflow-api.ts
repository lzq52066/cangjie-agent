import { request } from '@shared/api/http'

export const workflowApi = {
  list(keyword?: string) {
    return request<any[]>({ method: 'GET', url: '/workflow', params: { keyword } })
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
  executions(id: string) {
    return request<any[]>({ method: 'GET', url: `/workflow/${id}/executions` })
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
