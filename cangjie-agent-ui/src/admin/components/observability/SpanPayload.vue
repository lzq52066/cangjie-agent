<template>
  <div class="span-payload" v-loading="loading">
    <template v-if="inputRows.length">
      <div class="io-label">输入</div>
      <div class="kv-block">
        <div v-for="item in inputRows" :key="item.k" class="kv-row">
          <span class="kv-key">{{ item.k }}</span>
          <span class="kv-val">{{ item.v }}</span>
        </div>
      </div>
    </template>

    <template v-if="hits.length">
      <div class="io-label">输出（{{ hits.length }} 条候选）</div>
      <el-table :data="hits" size="small" border>
        <el-table-column label="#" prop="rank" width="40" align="center" />
        <el-table-column label="来源" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.documentName || row.documentId || row.paragraphId || '-' }}</template>
        </el-table-column>
        <el-table-column label="融合" width="76" align="center">
          <template #default="{ row }">{{ fmtScore(row.score) }}</template>
        </el-table-column>
        <el-table-column label="向量" width="76" align="center">
          <template #default="{ row }">{{ fmtScore(row.vectorScore) }}</template>
        </el-table-column>
        <el-table-column label="全文" width="76" align="center">
          <template #default="{ row }">{{ fmtScore(row.fullTextScore) }}</template>
        </el-table-column>
        <el-table-column label="片段" min-width="260">
          <template #default="{ row }"><span class="snippet">{{ row.content }}</span></template>
        </el-table-column>
      </el-table>
    </template>
    <template v-else-if="outputText">
      <div class="io-label">输出</div>
      <pre class="code-block">{{ outputText }}</pre>
    </template>

    <template v-if="metaRows.length">
      <div class="io-label">附加</div>
      <div class="kv-block">
        <div v-for="item in metaRows" :key="item.k" class="kv-row">
          <span class="kv-key">{{ item.k }}</span>
          <span class="kv-val">{{ item.v }}</span>
        </div>
      </div>
    </template>

    <div v-if="!loading && !inputRows.length && !hits.length && !outputText && !metaRows.length"
         class="empty-hint">
      {{ loadFailed
          ? '载荷拉取失败，收起后重新展开可重试'
          : '该 span 未记录输入 / 输出载荷（早于载荷留痕上线，或本次调用没有可留的中间量）' }}
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 单条 span 的输入 / 输出载荷。
 * <p>
 * 「这次检索耗时 12ms」回答不了「为什么没查到」—— 用的是原 query 还是改写后的、
 * 召回了哪些片段、各通道分数多少、最终排在第几，都记在这里。载荷可能很大，
 * 链路详情只带精简列，展开到本组件时才按需单条拉取；
 * 结果写进调用方传入的 cache，收起再展开不重复请求，切换链路时由调用方清空。
 */
import { computed, onMounted, ref } from 'vue'
import { observabilityApi } from '@admin/api/observability-api'

const props = defineProps<{
  spanId: string
  cache: Record<string, any>
}>()

const loading = ref(false)
const loadFailed = ref(false)
const record = computed<any>(() => props.cache[props.spanId] || {})

function parse(json?: string): any {
  if (!json) return null
  try {
    return JSON.parse(json)
  } catch {
    // 载荷不是合法 JSON（写入方降级成纯文本时）就原样展示，不吞掉
    return null
  }
}

const input = computed(() => parse(record.value.inputPayload))
const output = computed(() => parse(record.value.outputPayload))
const meta = computed(() => parse(record.value.metadata))

/** 平铺成键值行；数组值（如多个知识库 ID）合成一行，免得一个 query 拆成一片 */
function toRows(value: any): { k: string; v: string }[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return []
  return Object.entries(value).map(([k, v]) => ({
    k,
    v: Array.isArray(v) ? v.join('、') : String(v ?? '-')
  }))
}

const inputRows = computed(() => toRows(input.value))
const metaRows = computed(() => toRows(meta.value))
const hits = computed<any[]>(() => (Array.isArray(output.value) ? output.value : []))
const outputText = computed(() => {
  if (!record.value.outputPayload) return ''
  return output.value ? JSON.stringify(output.value, null, 2) : record.value.outputPayload
})

function fmtScore(v: any) {
  return v == null || isNaN(v) ? '-' : Number(v).toFixed(3)
}

onMounted(async () => {
  // 没 id 就压根没得查：旧写法会发出一次 /traces/undefined/payload 的无用请求
  if (!props.spanId || props.cache[props.spanId]) return
  loading.value = true
  try {
    const payload = await observabilityApi.traceSpanPayload(props.spanId)
    // 只缓存真有内容的载荷：空对象会被当成「已加载」，一次网络抖动就再也拉不回来
    if (payload && Object.keys(payload).length) props.cache[props.spanId] = payload
    else loadFailed.value = true
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.io-label { font-size: 12px; color: #909399; margin: 6px 0 4px; }
.kv-block {
  border: 1px solid #ebeef5; border-radius: 8px; padding: 6px 10px; background: #f9fafc;
}
.kv-row { display: flex; gap: 8px; font-size: 12px; line-height: 1.8; }
.kv-key { min-width: 110px; color: #909399; flex-shrink: 0; }
.kv-val { color: #303133; word-break: break-all; }
.snippet { font-size: 12px; color: #606266; word-break: break-all; }
.code-block {
  background: #f9fafc; border: 1px solid #ebeef5; border-radius: 8px; padding: 10px;
  max-height: 320px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  font-size: 12px; line-height: 1.7; margin: 0;
  font-family: 'Menlo', 'Consolas', monospace;
}
.empty-hint { font-size: 12px; color: #a8abb2; padding: 4px 0; }
.span-payload { padding: 4px 12px 10px; }
</style>
