/**
 * 评测指标的中文名与分数格式。
 * <p>
 * 评测报告、运行对比、在线评测三处都要把 SPI 指标名翻成人话，各自维护一份就会在不同页面上
 * 对同一个指标叫不同名字；指标本身是后端 EvalMetric#name() 定的，新增指标在这里补一行即可。
 */
const METRIC_LABEL_CN: Record<string, string> = {
  // 内置列（在 summary 里是 avgRecall / avgCorrectness，明细列上是 recall / correctness）
  recall: '召回率',
  correctness: '正确性',
  // RAGAS 口径的补充指标
  faithfulness: '忠实度',
  answerRelevancy: '答案相关性',
  contextPrecision: '上下文精度',
  contextRecall: '上下文召回'
}

export function metricLabel(name: string): string {
  return METRIC_LABEL_CN[name] || name
}

/** 分数统一按百分位展示：0~1 的原始值直接给人看只会让人拿它当百分比读 */
export function fmtScore(v?: number | null): string {
  if (v == null || Number.isNaN(Number(v))) return '-'
  return (Number(v) * 100).toFixed(1) + '%'
}

/**
 * 差值带符号展示。
 * <p>
 * 没有读数是 null 而不是 0 —— 把 null 当 0 就会把「这次没评测」显示成「跌到零」。
 */
export function fmtDelta(v?: number | null): string {
  if (v == null || Number.isNaN(Number(v))) return '-'
  const pct = Number(v) * 100
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(1)}%`
}

/** 差值的语义色：涨绿跌红，0 与空值不着色 */
export function deltaColor(v?: number | null): string {
  if (v == null || Number.isNaN(Number(v)) || Number(v) === 0) return ''
  return Number(v) > 0 ? '#67c23a' : '#f56c6c'
}

/**
 * 分数的语义色。
 * <p>
 * 阈值只是「要不要多看一眼」的提醒，不是验收线：真正的口径由评测集与判定提示词决定，
 * 这里只是把明显偏低的从一片百分比数字里拎出来。
 */
export function scoreColor(v?: number | null): string {
  if (v == null || Number.isNaN(Number(v))) return ''
  const n = Number(v)
  if (n >= 0.8) return '#67c23a'
  if (n >= 0.6) return '#e6a23c'
  return '#f56c6c'
}
