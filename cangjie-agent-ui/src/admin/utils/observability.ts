/**
 * 可观测性链路视图的公共格式化与状态映射。
 * <p>
 * 链路（trace_link）、Agent 执行（agent_run）、模型调用（llm_trace）三套状态取值不同名，
 * 统一在这里收敛，避免每个面板各写一份。
 */

/** 时间戳解析：步骤留痕是 'yyyy-MM-dd HH:mm:ss'，模型调用是 ISO，统一折算为毫秒 */
export function toMs(t?: string | null): number {
  if (!t) return 0
  const ms = Date.parse(String(t).replace(' ', 'T'))
  return Number.isNaN(ms) ? 0 : ms
}

/** 按后端查询参数要求的 'yyyy-MM-dd HH:mm:ss' 输出本地时间 */
export function fmtLocal(ms: number): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
    + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 以某个时间点为锚做偏移，用于围绕本链路划定查询时间窗 */
export function shiftTime(t: string | number | null | undefined, deltaMs: number): string {
  const base = typeof t === 'number' ? t : toMs(t)
  return fmtLocal(base + deltaMs)
}

export function fmtDuration(ms?: number | null): string {
  if (ms == null) return '-'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(2)} s`
}

export type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

/** 链路聚合状态：fail / running / cancelled / success */
export const LINK_STATUS_OPTIONS = [
  { value: 'success', label: '全部成功' },
  { value: 'fail', label: '有失败' },
  { value: 'running', label: '进行中' },
  { value: 'cancelled', label: '已取消' }
]

export function linkStatusLabel(s?: string): string {
  return LINK_STATUS_OPTIONS.find(o => o.value === s)?.label || s || '-'
}

export function linkStatusTagType(s?: string): TagType {
  switch (s) {
    case 'fail': return 'danger'
    case 'running': return 'primary'
    case 'cancelled': return 'info'
    default: return 'success'
  }
}

/** Agent 执行状态（RunStatus） */
export const RUN_STATUS_OPTIONS = [
  { value: 'running', label: '执行中' },
  { value: 'completed', label: '已完成' },
  { value: 'failed', label: '失败' },
  { value: 'cancelled', label: '已取消' },
  { value: 'waiting_approval', label: '待审批' },
  { value: 'waiting_local', label: '等待本地执行' }
]

export function runStatusLabel(s?: string): string {
  return RUN_STATUS_OPTIONS.find(o => o.value === s)?.label || s || '-'
}

export function runStatusTagType(s?: string): TagType {
  switch (s) {
    case 'completed': return 'success'
    case 'failed': return 'danger'
    case 'waiting_approval':
    case 'waiting_local': return 'warning'
    case 'cancelled': return 'info'
    default: return 'primary'
  }
}

/** 执行形态（harness_type） */
export const HARNESS_OPTIONS = [
  { value: 'chat', label: '对话' },
  { value: 'workflow', label: '工作流' },
  { value: 'subagent', label: '子 Agent' },
  { value: 'debug', label: '调试' }
]

export function harnessLabel(h?: string): string {
  return HARNESS_OPTIONS.find(o => o.value === h)?.label || h || '-'
}

/** span 与模型调用共用 success / fail 两态 */
export function callStatusTagType(s?: string): TagType {
  return s === 'success' ? 'success' : 'danger'
}

export function callStatusLabel(s?: string): string {
  if (!s) return '-'
  return s === 'success' ? '成功' : s === 'fail' ? '失败' : runStatusLabel(s)
}

export function stepTagType(t?: string): TagType {
  if (t === 'tool') return 'warning'
  if (t === 'error') return 'danger'
  if (t === 'hook') return 'info'
  return 'primary'
}

export function riskTagType(r?: string): TagType {
  if (r === 'high') return 'danger'
  if (r === 'medium' || r === 'mid') return 'warning'
  return 'info'
}

export function approvalStatusLabel(s?: string): string {
  return ({ pending: '待审批', approved: '已批准', rejected: '已驳回', expired: '已过期', cancelled: '已取消' } as Record<string, string>)[s || ''] || s || '-'
}

export function approvalStatusTagType(s?: string): TagType {
  switch (s) {
    case 'approved': return 'success'
    case 'rejected': return 'danger'
    case 'expired':
    case 'cancelled': return 'info'
    default: return 'warning'
  }
}

/** 链路入口标签：span 为 module/action，run 为 harness_type，llm 为无归属的模型调用 */
const ENTRY_CN: Record<string, string> = {
  'chat/send': '对话请求',
  'workflow/execute': '工作流执行',
  'retrieval/search': '知识库检索',
  chat: '对话',
  workflow: '工作流',
  subagent: '子 Agent',
  debug: '调试'
}

export function linkEntryLabel(kind?: string, label?: string): string {
  if (kind === 'llm') return '模型调用'
  if (!label) return '未标记入口'
  return ENTRY_CN[label] || label
}

/**
 * promptContent 存的是完整消息数组 JSON；可解析则按角色分段还原，否则原样输出。
 */
export function renderPrompt(raw?: string): string {
  if (!raw) return '-'
  let parsed: any
  try {
    parsed = JSON.parse(raw)
  } catch {
    // 正文超长按字符数截断过，尾部不是合法 JSON，只能原样展示
    return raw
  }
  if (!Array.isArray(parsed)) return raw
  return parsed.map((m: any, i: number) => {
    const role = m.type || m.role || '?'
    const text = [m.text, m.content].find((v: any) => typeof v === 'string')
      ?? (Array.isArray(m.contents)
        ? m.contents.map((c: any) => c?.text ?? `[${c?.type ?? '附件'}]`).join('\n')
        : JSON.stringify(m))
    // 工具调用：langchain4j 用 toolExecutionRequests，旧数据用 toolCalls
    const reqs: any[] = m.toolExecutionRequests || m.toolCalls || []
    const calls = reqs.map((t: any) =>
      `\n→ ${t.name || t.functionName || '?'}(${t.arguments ?? t.args ?? ''})`).join('')
    const toolName = m.toolName ? ` ${m.toolName}` : ''
    return `[${i + 1}] ${role}${toolName}${calls}${text ? `\n${text}` : ''}`
  }).join('\n\n')
}

/**
 * 成本（元）：单价按千 token 计，单次调用常在分以下，
 * 统一两位小数会把 0.003 显示成 0.00，看成「这个模型免费」。
 */
export function fmtCost(cost?: number | string | null): string {
  if (cost == null || cost === '') return '-'
  const n = Number(cost)
  if (Number.isNaN(n)) return '-'
  return '¥' + (n >= 1 ? n.toFixed(2) : n.toFixed(4))
}

/**
 * 在线评测得分：eval_scores 存的是 {指标名: 0~1} 的扁表。
 * <p>
 * 脏 JSON 不按异常处理：列表上一次要渲染几十行，一条历史坏行不该把整列带崩。
 */
export function parseEvalScores(raw?: string | null): Record<string, number> {
  if (!raw) return {}
  let parsed: any
  try {
    parsed = JSON.parse(raw)
  } catch {
    return {}
  }
  if (!parsed || typeof parsed !== 'object') return {}
  const out: Record<string, number> = {}
  for (const [k, v] of Object.entries(parsed)) {
    const n = Number(v)
    // 只收区间内的分数：判定抽风写出的 0.9e9 会直接把均值拖到天花板
    if (!Number.isNaN(n) && n >= 0 && n <= 1) out[k] = n
  }
  return out
}

/** 一行的均分：列上只能放一个数，指标明细回到悬浮层看 */
export function avgScore(scores: Record<string, number>): number | null {
  const values = Object.values(scores || {})
  if (!values.length) return null
  return values.reduce((a, b) => a + b, 0) / values.length
}
