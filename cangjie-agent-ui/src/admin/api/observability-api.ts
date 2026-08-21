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
  metrics(params: { metricType?: string; startTime?: string; endTime?: string; pageNum?: number; pageSize?: number }) {
    return request<PageResult<any>>({ method: 'GET', url: '/observability/metrics', params })
  },
  /** 图表数据：按 metricType 分组多 series，支持时间范围 */
  metricsChart(params?: { metricType?: string; startTime?: string; endTime?: string }) {
    return request<{ groups: any[] }>({ method: 'GET', url: '/observability/metrics/chart', params })
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
  },
  /** LLM 调用追踪分页 */
  llmTraces(params: {
    traceId?: string
    appName?: string
    modelName?: string
    sessionId?: string
    status?: string
    promptKeyword?: string
    responseKeyword?: string
    startTime?: string
    endTime?: string
    pageNum?: number
    pageSize?: number
  }) {
    return request<PageResult<any>>({ method: 'GET', url: '/observability/llm-traces', params })
  },
  /** LLM 调用详情 */
  llmTraceDetail(id: string) {
    return request<any>({ method: 'GET', url: `/observability/llm-traces/${id}` })
  },
  /** 会话 LLM 调用时间线 */
  llmTraceTimeline(sessionId: string) {
    return request<any[]>({ method: 'GET', url: '/observability/llm-traces/timeline', params: { sessionId } })
  }
}