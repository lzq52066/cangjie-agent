<template>
  <el-card shadow="never" class="llm-stats" v-loading="loading">
    <template #header>
      <div class="ls-header">
        <span class="ls-title">模型调用（近 {{ days }} 天）</span>
        <el-radio-group v-model="localDays" size="small" @change="onDaysChange">
          <el-radio-button v-for="d in DAY_OPTIONS" :key="d" :value="d">{{ d }} 天</el-radio-button>
        </el-radio-group>
      </div>
      <div class="ls-hint">
        这一组是「近 N 天」的调用统计，与上面几个今日计数不是一个平面，别混着读：
        P95 看体感、错误率看稳定性、成本看钱花在哪，只看均值会同时骗过这三件事。
      </div>
    </template>

    <div class="ls-grid">
      <div v-for="m in cards" :key="m.key" class="ls-cell">
        <div class="ls-value" :style="{ color: m.color }">{{ m.value }}</div>
        <div class="ls-label">{{ m.label }}</div>
        <div class="ls-sub">{{ m.sub }}</div>
      </div>
    </div>

    <div v-if="models.length" class="ls-models">
      <el-table :data="models" size="small" stripe max-height="300">
        <el-table-column label="模型" prop="modelName" min-width="150" show-overflow-tooltip />
        <el-table-column label="调用" prop="calls" width="90" align="center" />
        <el-table-column label="失败" width="80" align="center">
          <template #default="{ row }">{{ row.failures || 0 }}</template>
        </el-table-column>
        <el-table-column label="错误率" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: errorColor(row.errorRate) }">{{ fmtPercent(row.errorRate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成本" width="110" align="center">
          <template #default="{ row }">{{ fmtCost(row.cost) }}</template>
        </el-table-column>
        <el-table-column label="Token" prop="tokens" width="110" align="center" />
        <el-table-column label="P95 耗时" width="110" align="center">
          <template #default="{ row }">{{ fmtDuration(row.p95Duration) }}</template>
        </el-table-column>
      </el-table>
      <div class="ls-foot">
        成本按模型管理里维护的单价估算，没配单价的模型不计入 —— 总额要跟调用数一起看，
        一个 0 可能只是「没人配价格」，不是「没花钱」。
      </div>
    </div>
    <el-empty v-else :image-size="60" description="窗口内没有模型调用记录" />
  </el-card>
</template>

<script setup lang="ts">
/**
 * 模型调用的窗口统计面板：P95 延迟、错误率、成本与按模型切片。
 * <p>
 * 数据由父级的 dashboard 接口一并带回（一次请求同时给出今日计数与这一组），
 * 面板只负责展示；切换天数是通知父级重取，而不是自己再开一条请求把同一份今日数拉两遍。
 */
import { computed, ref, watch } from 'vue'
import type { LlmTraceStats, ModelStat } from '@admin/api/observability-api'
import { fmtCost, fmtDuration } from '@admin/utils/observability'

const props = withDefaults(defineProps<{
  stats?: LlmTraceStats | null
  days?: number
  loading?: boolean
}>(), { stats: null, days: 7, loading: false })

const emit = defineEmits<{ (e: 'change-days', days: number): void }>()

/** 只给几档常用窗口：天数在后端夹到 1~365，但自由输入会让一次面板请求变成全表扫描 */
const DAY_OPTIONS = [1, 7, 30, 90]

const localDays = ref(props.days)
watch(() => props.days, (v) => { localDays.value = v })

function onDaysChange(value: any) {
  emit('change-days', Number(value) || 7)
}

const models = computed<ModelStat[]>(() => props.stats?.byModel || [])

/** 没有调用时后端给的是 0 而不是 null，卡片要能区分「零调用」与「零错误」 */
const hasCalls = computed(() => Number(props.stats?.calls ?? 0) > 0)

function fmtPercent(v?: number | null) {
  if (v == null || Number.isNaN(Number(v))) return '-'
  return (Number(v) * 100).toFixed(2) + '%'
}

function errorColor(v?: number | null): string {
  const rate = Number(v ?? 0)
  if (!hasCalls.value || rate <= 0) return '#909399'
  return rate >= 0.05 ? '#f56c6c' : rate >= 0.01 ? '#e6a23c' : '#67c23a'
}

function num(v?: number | null): string {
  return v == null ? '-' : String(v)
}

const cards = computed(() => {
  const s = props.stats
  const streaming = Number(s?.streamingCalls ?? 0)
  return [
    {
      key: 'calls', label: '调用数', value: num(s?.calls), color: '#409eff',
      sub: `失败 ${s?.failures ?? 0} 次 · Token ${num(s?.tokens)}`
    },
    {
      key: 'error', label: '错误率', value: fmtPercent(s?.errorRate),
      color: errorColor(s?.errorRate),
      sub: hasCalls.value ? '按调用数分母' : '窗口内没有调用'
    },
    {
      key: 'p95', label: 'P95 耗时', value: fmtDuration(s?.p95Duration), color: '#e6a23c',
      sub: `中位 ${fmtDuration(s?.p50Duration)} · 均值 ${fmtDuration(s?.avgDuration)}`
    },
    {
      // 流式调用为 0 时这一格没有意义，直接说明而不是显示一个 '-' 让人以为首字很快
      key: 'ttft', label: 'P95 首字延迟', value: streaming > 0 ? fmtDuration(s?.p95Ttft) : '-',
      color: '#7c4dff',
      sub: streaming > 0
        ? `流式 ${streaming} 次 · 均值 ${fmtDuration(s?.avgTtft)}`
        : '窗口内没有流式调用'
    },
    {
      key: 'cost', label: '估算成本', value: fmtCost(s?.cost), color: '#f56c6c',
      sub: '仅计已配单价的模型'
    }
  ]
})
</script>

<style lang="scss" scoped>
.llm-stats { margin-bottom: 16px; }
.ls-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.ls-title { font-size: 15px; font-weight: 600; color: #303133; }
.ls-hint { font-size: 12px; color: #909399; margin-top: 6px; line-height: 1.6; }
.ls-grid {
  display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px;
}
.ls-cell {
  background: #f7f8fa; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 12px 8px; text-align: center;
}
.ls-value { font-size: 20px; font-weight: 700; color: #303133; }
.ls-label { font-size: 12px; color: #606266; margin-top: 4px; }
.ls-sub { font-size: 11px; color: #a8abb2; margin-top: 2px; }
.ls-models { margin-top: 14px; }
.ls-foot { font-size: 12px; color: #909399; margin-top: 8px; line-height: 1.6; }

@media (max-width: 1200px) {
  .ls-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 768px) {
  .ls-grid { grid-template-columns: repeat(2, 1fr); }
  .ls-header { flex-direction: column; align-items: flex-start; }
}
</style>
