import { request } from '@shared/api/http'

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export const observabilityApi = {
  /** 汇总面板 */
  dashboard() {
    return request<Record<string, any>>({ method: 'GET', url: '/observability/dashboard' })
  },
  logs(params: { module?: string; action?: string; status?: string; pageNum?: number; pageSize?: number }) {
    return request<PageResult<any>>({ method: 'GET', url: '/observability/logs', params })
  },
  metrics(params: { metricType?: string; pageNum?: number; pageSize?: number }) {
    return request<PageResult<any>>({ method: 'GET', url: '/observability/metrics', params })
  },
  /** 手动触发一次指标采集 */
  collectMetrics() {
    return request<any[]>({ method: 'GET', url: '/observability/metrics/collect' })
  },
  traces(params: {
    traceId?: string
    module?: string
    action?: string
    startTime?: string
    endTime?: string
    pageNum?: number
    pageSize?: number
  }) {
    return request<PageResult<any>>({ method: 'GET', url: '/observability/traces', params })
  }
}
