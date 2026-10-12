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

/**
 * 时间窗口内的模型调用统计。
 * <p>
 * 三个口径各管一件事：P95 看体感、错误率看稳定性、成本看钱花在哪，均值会把三件事一起抹平，
 * 所以这里一个 avg* 都不省地留着，但展示时不能只拿它说话。
 */
export interface LlmTraceStats {
  calls: number
  failures: number
  /** 0~1，没有调用时为 0 */
  errorRate: number
  /** 估算成本合计（元）：没维护单价的模型不计入 */
  cost: number
  tokens: number
  avgDuration: number | null
  p50Duration: number | null
  p95Duration: number | null
  /** 带首字延迟的调用数（流式调用数）：为 0 时下面两项没有意义 */
  streamingCalls: number
  avgTtft: number | null
  p95Ttft: number | null
  byModel: ModelStat[]
}

/** 单个模型的调用切片：总成本看不出是谁吃掉的，靠这一层回落 */
export interface ModelStat {
  modelName: string
  calls: number
  failures: number
  errorRate: number
  cost: number
  tokens: number
  p95Duration: number | null
}

/** 在线评测的水位与趋势 */
export interface OnlineEvalTrend {
  scored: number
  /** 积压：均值再高，这一项大就说明看到的只是少数样本 */
  pending: number
  /** 0~1 覆盖率 */
  coverage: number | null
  /** 指标名 → 均值与出了这个分数的条数 */
  metrics: Record<string, MetricAvg>
  daily: TrendDaily[]
}

/** cases 是「出了这个指标分数的条数」，不是已评条数：2 条与 200 条的均值可信度完全不同 */
export interface MetricAvg {
  avg: number | null
  cases: number
}

export interface TrendDaily {
  day: string
  scored: number
  pending: number
  metrics: Record<string, MetricAvg>
}

export const observabilityApi = {
  /**
   * 汇总面板：今日计数 + 近 days 天的模型调用统计。
   * <p>
   * days 只作用于 llmStats（自然日，含今天，后端夹到 1~365）；今日那几个数不受它影响。
   */
  dashboard(days?: number) {
    return request<Record<string, any>>({ method: 'GET', url: '/observability/dashboard', params: { days } })
  },
  logs(params: { module?: string; action?: string; status?: string; pageNum?: number; pageSize?: number }) {
    return request<MpPage<any>>({ method: 'GET', url: '/observability/logs', params })
  },
  metrics(params: { metricType?: string; startTime?: string; endTime?: string; pageNum?: number; pageSize?: number }) {
    return request<MpPage<any>>({ method: 'GET', url: '/observability/metrics', params })
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
  /**
   * 单条 span 的输入 / 输出载荷（检索 query / 召回片段与各通道分数）。
   * 链路详情不带载荷，十几条检索 span 就能把响应体撑到 MB 级，展开时再单条拉。
   */
  traceSpanPayload(spanId: string) {
    return request<any>({ method: 'GET', url: `/observability/traces/${spanId}/payload` })
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
  /**
   * 在线评测的水位与按天趋势。挂在调用记录同一个接口下是因为它们回答的是同一个问题：
   * 「线上现在好不好」—— 而覆盖率必须跟均值一起看，只评了 3% 的样本，那条曲线基本只是噪声。
   */
  llmTraceEvalTrend(days?: number) {
    return request<OnlineEvalTrend>({ method: 'GET', url: '/observability/llm-traces/eval-trend', params: { days } })
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