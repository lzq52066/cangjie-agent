import { request } from '@shared/api/http'

export const evalApi = {
  // 数据集
  listDatasets(keyword?: string) {
    return request<any[]>({ method: 'GET', url: '/observability/eval/datasets', params: { keyword } })
  },
  createDataset(data: any) {
    return request<any>({ method: 'POST', url: '/observability/eval/datasets', data })
  },
  updateDataset(id: string, data: any) {
    return request<any>({ method: 'PUT', url: `/observability/eval/datasets/${id}`, data })
  },
  deleteDataset(id: string) {
    return request<void>({ method: 'DELETE', url: `/observability/eval/datasets/${id}` })
  },

  // 用例
  listCases(datasetId: string) {
    return request<any[]>({ method: 'GET', url: `/observability/eval/datasets/${datasetId}/cases` })
  },
  createCase(datasetId: string, data: any) {
    return request<any>({ method: 'POST', url: `/observability/eval/datasets/${datasetId}/cases`, data })
  },
  updateCase(id: string, data: any) {
    return request<any>({ method: 'PUT', url: `/observability/eval/cases/${id}`, data })
  },
  deleteCase(id: string) {
    return request<void>({ method: 'DELETE', url: `/observability/eval/cases/${id}` })
  },

  // 运行与报告
  run(datasetId: string, config: any) {
    return request<any>({ method: 'POST', url: `/observability/eval/datasets/${datasetId}/run`, data: config })
  },
  getRun(runId: string) {
    return request<any>({ method: 'GET', url: `/observability/eval/runs/${runId}` })
  }
}
