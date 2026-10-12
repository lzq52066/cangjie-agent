<template>
  <div class="llm-call-body" v-loading="loading">
    <el-alert v-if="record?.errorMessage" type="error" :closable="false" show-icon
              :title="record.errorMessage" style="margin-bottom:8px" />
    <el-alert v-else-if="loadFailed" type="warning" :closable="false" show-icon
              title="正文拉取失败，收起后重新展开可重试" style="margin-bottom:8px" />
    <div v-if="paramList.length" class="param-row">
      <span>调用参数</span>
      <el-tag v-for="p in paramList" :key="p.k" size="small" type="info" effect="plain">{{ p.k }}={{ p.v }}</el-tag>
    </div>
    <div class="io-label">完整 Prompt</div>
    <pre class="code-block">{{ renderPrompt(record?.promptContent) }}</pre>
    <div class="io-label">模型响应</div>
    <pre class="code-block">{{ record?.responseContent || '-' }}</pre>
  </div>
</template>

<script setup lang="ts">
/**
 * 单条模型调用的正文（完整 prompt + 响应）。
 * <p>
 * 正文可达数万字符，列表与时间线都只带精简行，展开到这里时才按需拉取；
 * 结果写进调用方传入的 cache，收起再展开不重复请求，切换查看对象时由调用方清空。
 */
import { computed, onMounted, ref } from 'vue'
import { observabilityApi } from '@admin/api/observability-api'
import { renderPrompt } from '@admin/utils/observability'

const props = defineProps<{
  traceId: string
  cache: Record<string, any>
}>()

const loading = ref(false)
const loadFailed = ref(false)
const record = computed(() => props.cache[props.traceId])

/** 采样参数：调参与效果对不上号是排障常见卡点，光看 prompt 与 token 看不出是不是 temperature 拉高导致的跑偏 */
const paramList = computed(() => {
  const raw = record.value?.modelParams
  if (!raw) return []
  try {
    const params = JSON.parse(raw)
    return Object.entries(params || {})
      .filter(([, v]) => v != null)
      .map(([k, v]) => ({ k, v: String(v) }))
  } catch {
    return []
  }
})

onMounted(async () => {
  if (record.value) return
  loading.value = true
  try {
    const detail = await observabilityApi.llmTraceDetail(props.traceId)
    // 只缓存真正文：空对象会被当成「已加载」，网络抖动一次这条调用就永远显示空白且不再重试
    if (detail) props.cache[props.traceId] = detail
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
.param-row {
  display: flex; align-items: center; flex-wrap: wrap; gap: 6px;
  font-size: 12px; color: #909399; margin-bottom: 8px;
}
.code-block {
  background: #f9fafc; border: 1px solid #ebeef5; border-radius: 8px; padding: 10px;
  max-height: 320px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  font-size: 12px; line-height: 1.7; margin: 0;
  font-family: 'Menlo', 'Consolas', monospace;
}
</style>
