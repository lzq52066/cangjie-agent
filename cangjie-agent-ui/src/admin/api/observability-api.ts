import { request } from '@shared/api/http'

/** MyBatis-Plus IPage 序列化结构（仅 observability 接口使用） */
export interface MpPage<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/** 链路摘要查询条件 */
export interface TraceLinkQuery {
  traceId?: string
  sessionId?: string
  appId?: string
  userId?: string
  status?: string
  startTime?: string
  endTime?: string
  pageNum?: number
  pageSize?: number
}

/** 一条链路的聚合摘要 */
export interface TraceLink {
  /** 链路 ID；无 traceId 的孤立留痕为 #kind#记录ID */
  traceId: string
  /** span / run / llm */
  entryKind?: string
  entryLabel?: string
  startTime?: string
  endTime?: string
  duration?: number
  appId?: string
  appName?: string
  sessionId?: string
  userId?: string
  modelName?: string
  runCount?: number
  llmCallCount?: number
  spanCount?: number
  totalTokens?: number
  status?: string
  errorMessage?: string
}

/** 链路明细：概览 + 请求级 span + 各次 Agent 执行 + 无 run 归属的模型调用 */
export interface TraceLinkDetail {
  summary: TraceLink
  spans: any[]
  runs: any[]
  backgroundLlmTraces: any[]
}

export const observabilityApi = {
  /** 汇总面板 */
  dashboard() {
    return request<Record<string, any>>({ method: 'GET', url: '/observability/dashboard' })
  },
  logs(params: { module?: string; action?: string; status?: string; pageNum?: number; pageSize?: number }) {
    return request<MpPage<any>>({ method: 'GET', url: '/observability/logs', params })
  },
  metrics(params: { metricType?: string; startTime?: string; endTime?: string; pageNum?: number; pageSize?: number }) {
    return request<MpPage<any>>({ method: 'GET', url: '/observability/metrics', params })
  },
  /** 图表数据：按 metricType 分组多 series，支持时间范围 */
  metricsChart(params?: { metricType?: string; startTime?: string; endTime?: string }) {
    return request<{ groups: any[] }>({ method: 'GET', url: '/observability/metrics/chart', params })
  },
  /** 手动触发一次指标采集 */
  collectMetrics() {
    return request<any[]>({ method: 'GET', url: '/observability/metrics/collect' })
  },
  /** 链路摘要分页：一次 traceId 下的 span + Agent 执行 + 模型调用归并成一行 */
  traceLinks(params: TraceLinkQuery) {
    return request<MpPage<TraceLink>>({ method: 'GET', url: '/observability/trace-links', params })
  },
  /** 单条链路明细；linkId 可能是 #kind#记录ID 形式的孤立链路键，故走查询参数 */
  traceLinkDetail(linkId: string) {
    return request<TraceLinkDetail>({ method: 'GET', url: '/observability/trace-links/detail', params: { linkId } })
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
    return request<MpPage<any>>({ method: 'GET', url: '/observability/llm-traces', params })
  },
  /** LLM 调用详情 */
  llmTraceDetail(id: string) {
    return request<any>({ method: 'GET', url: `/observability/llm-traces/${id}` })
  },
  /** 会话 LLM 调用时间线 */
  llmTraceTimeline(sessionId: string) {
    return request<any[]>({ method: 'GET', url: '/observability/llm-traces/timeline', params: { sessionId } })
  },
  /** Agent 审批单分页 */
  agentApprovals(params: { status?: string; appId?: string; userId?: string; pageNum?: number; pageSize?: number }) {
    return request<MpPage<any>>({ method: 'GET', url: '/observability/agent-approvals', params })
  },
  /** Agent 审批单详情 */
  agentApprovalDetail(id: string) {
    return request<any>({ method: 'GET', url: `/observability/agent-approvals/${id}` })
  }
}