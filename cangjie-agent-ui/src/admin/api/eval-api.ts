import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'

export const evalApi = {
  // 数据集
  listDatasets(query: PageQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: '/observability/eval/datasets', params: query })
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
  listCases(datasetId: string, query: PageQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: `/observability/eval/datasets/${datasetId}/cases`, params: query })
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

  // 线上对话回流：单条（给某条回复点「加入评测集」）与批量（按反馈/标注筛选）
  importCaseFromMessage(datasetId: string, messageId: string) {
    return request<any>({ method: 'POST', url: `/observability/eval/datasets/${datasetId}/cases/from-message`, data: { messageId } })
  },
  importCasesFromConversations(datasetId: string, query: Record<string, any>) {
    return request<{ scanned: number; imported: number; duplicated: number; unusable: number; caseIds: string[] }>(
      { method: 'POST', url: `/observability/eval/datasets/${datasetId}/cases/import-conversations`, data: query })
  },

  // 运行与报告
  run(datasetId: string, config: any) {
    return request<any>({ method: 'POST', url: `/observability/eval/datasets/${datasetId}/run`, data: config })
  },
  listRuns(params: Record<string, any>) {
    return request<PageResult<any>>({ method: 'GET', url: '/observability/eval/runs', params })
  },
  getRun(runId: string) {
    return request<any>({ method: 'GET', url: `/observability/eval/runs/${runId}` })
  },
  // 逐 case 明细：只返精简列（问题 / 各指标 / 耗时 / 状态），跟着轮询重传正文没意义
  listRunResults(runId: string) {
    return request<any[]>({ method: 'GET', url: `/observability/eval/runs/${runId}/results` })
  },
  // 展开某一行时才单条拉正文（回答 / 检索片段 / 判定中间量），与链路侧 payload 同一套语义
  runResultDetail(runId: string, resultId: string) {
    return request<any>({ method: 'GET', url: `/observability/eval/runs/${runId}/results/${resultId}` })
  },
  // 取消运行：执行中的评估在下一个用例边界停下，不再烧剩下的 token
  cancelRun(runId: string) {
    return request<any>({ method: 'POST', url: `/observability/eval/runs/${runId}/cancel` })
  },
  deleteRun(runId: string) {
    return request<void>({ method: 'DELETE', url: `/observability/eval/runs/${runId}` })
  },

  /**
   * 对比两次运行：汇总差值 + 成本差值 + 逐用例差值（跌得最狠的排在前面）。
   * <p>
   * run 是「改动后」，baseline 是「改动前」；baseline 不传时后端取同数据集里紧邻它之前的
   * 那一次已结束运行，首次评估会报错提示显式指定基准。
   */
  compareRuns(run: string, baseline?: string) {
    return request<EvalRunCompare>({ method: 'GET', url: '/observability/eval/runs/compare', params: { run, baseline } })
  },

  // 在线评测：手动抽一批线上调用打判定分，分数就地回填在调用记录上
  scoreOnline(data: OnlineEvalRequest = {}) {
    return request<OnlineEvalSummary>({ method: 'POST', url: '/observability/eval/online/score', data })
  },
  /** 当前待评积压（不评也不会自己消失，只看个数） */
  onlinePending() {
    return request<number>({ method: 'GET', url: '/observability/eval/online/pending' })
  }
}

/** 一格的两次读数：delta = to - from，任一侧没分则为 null（不拿「没评测」显示成「跌到零」） */
export interface MetricDelta {
  from: number | null
  to: number | null
  delta: number | null
  fromCases: number | null
  toCases: number | null
}

/** 一条用例在两次运行里的差值（综合分 = 该 case 所有出了分的指标的均值） */
export interface CaseDiff {
  caseId: string
  question: string
  fromStatus?: string
  toStatus?: string
  compositeFrom: number | null
  compositeTo: number | null
  delta: number | null
  metrics: Record<string, MetricDelta>
}

export interface EvalRunCompare {
  fromRunId: string
  toRunId: string
  fromStatus: string
  toStatus: string
  fromStartTime: string
  toStartTime: string
  /** 假时逐用例对比没有意义（均值里混的是题目差异），得明确提示口径不可比 */
  sameDataset: boolean
  metrics: Record<string, MetricDelta>
  cost: Record<string, MetricDelta>
  cases: CaseDiff[]
  regressed: number
  improved: number
  onlyInFrom: number
  onlyInTo: number
  /** 回归阈值（绝对分，0.05 即 5 个百分点），后端没配时可能为 0 = 门禁关掉 */
  regressThreshold?: number
  /** 跌过阈值的指标：非空就是一次该拦下来的退化（成本项不进这里，涨了不是分数坏了） */
  regressAlerts?: RegressAlert[]
}

export interface RegressAlert {
  name: string
  from: number | null
  to: number | null
  delta: number | null
  /** 对比侧出了多少条这个指标的分数 */
  toCases?: number | null
}

/** 在线评测触发参数：limit 直接等于花多少钱，因此不给前端默认值，由调用方明说 */
export interface OnlineEvalRequest {
  limit?: number
  judgeModelId?: string
  metrics?: string[]
}

export interface OnlineEvalSummary {
  pendingBefore: number
  sampled: number
  scored: number
  /** 一个指标分数都没出的条数（没片段 / 没回答 / 判定全挂） */
  withoutScore: number
  /** 被其它执行抢先标注、本轮写空了的条数 */
  lostRace: number
  judgeTokens: number
  metrics: Record<string, { avg: number | null, cases: number }>
}
