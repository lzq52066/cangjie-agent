<template>
  <div class="result-detail" v-loading="loading">
    <div v-if="item.error" class="detail-block detail-error">{{ item.error }}</div>
    <div class="detail-block"><span class="detail-key">回答</span>{{ item.answer || '-' }}</div>
    <div v-if="judgeReason" class="detail-block"><span class="detail-key">判定</span>{{ judgeReason }}</div>
    <div v-if="missedDocs.length" class="detail-block">
      <span class="detail-key">未召回</span>{{ missedDocs.join('、') }}
    </div>
    <div v-if="recallSkipped" class="detail-block detail-muted">
      <span class="detail-key">召回</span>{{ recallSkipped }}
    </div>
    <div v-for="(c, i) in contexts" :key="i" class="context-item">
      <div class="context-meta">
        [{{ c.rank ?? i + 1 }}] {{ c.documentId || c.paragraphId || '未知文档' }} ·
        融合 {{ fmtScore(c.score) }} / 向量 {{ fmtScore(c.vectorScore) }} / 全文 {{ fmtScore(c.fullTextScore) }}
      </div>
      <div class="context-text">{{ c.content }}</div>
    </div>
    <div v-if="!loading && !contexts.length && !item.answer && !item.error" class="detail-block detail-muted">
      本次没有可展示的正文（未配置回答模型或未执行检索）
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 评估报告里展开某一 case 的正文。
 * <p>
 * 列表接口只带精简列（问题 / 各指标 / 耗时 / 状态），回答与检索片段是单条拉来的：
 * 一次运行上百个 case，每人都带全文的话跟着轮询重传就是每次 MB 级响应。
 * record 为空时退化成只用行上已有的字段渲染 —— 拆表之前的历史运行就是这么存的
 * （正文内嵌在 eval_run.results 整包里，检索片段的键还叫 retrievalSources）。
 */
import { computed } from 'vue'

const props = defineProps<{
  row: any
  record?: any | null
  loading?: boolean
}>()

/** 单条拉来的正文覆盖精简列；拉取失败或未拉时行上的旧值照常可用 */
const item = computed<any>(() => (props.record ? { ...props.row, ...props.record } : props.row || {}))

function safeParse<T>(text: any, fallback: T): T {
  if (!text) return fallback
  if (typeof text !== 'string') return (text ?? fallback) as T
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}

function parseList(raw: any): any[] {
  // 历史运行没有 contexts 列，检索片段写在 retrievalSources 里，键名不同但结构可用
  const v = safeParse<any[]>(raw, [])
  return Array.isArray(v) ? v : []
}

function parseObject(raw: any): Record<string, any> {
  const v = safeParse<Record<string, any>>(raw, {})
  return v && typeof v === 'object' && !Array.isArray(v) ? v : {}
}

const contexts = computed(() => parseList(item.value.contexts || item.value.retrievalSources))
const detail = computed(() => parseObject(item.value.detail))
const judgeReason = computed(() => detail.value.judgeReason || '')
const recallSkipped = computed(() => detail.value.recallSkipped || '')
const missedDocs = computed(() => {
  const docs = detail.value.missedDocs
  return Array.isArray(docs) ? docs : []
})

function fmtScore(v: any) {
  return v == null || isNaN(v) ? '-' : Number(v).toFixed(3)
}
</script>

<style lang="scss" scoped>
.result-detail {
  padding: 6px 14px; font-size: 12px; color: #606266; min-height: 40px;
  .detail-block { margin-bottom: 6px; line-height: 1.6; word-break: break-all; }
  .detail-error { color: #f56c6c; }
  .detail-muted { color: #909399; }
  .detail-key {
    display: inline-block; min-width: 40px; margin-right: 6px;
    color: #909399; font-weight: 600;
  }
  .context-item {
    padding: 6px 8px; margin-bottom: 6px;
    background: #f7f8fa; border-radius: 6px;
  }
  .context-meta { color: #909399; margin-bottom: 2px; }
  .context-text { line-height: 1.6; white-space: pre-wrap; word-break: break-all; }
}
</style>
