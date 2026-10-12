<template>
  <div class="online-eval-panel" v-loading="loading">
    <div class="oe-bar">
      <div class="oe-bar-left">
        <span class="oe-title">在线评测（近 {{ days }} 天）</span>
        <el-radio-group v-model="localDays" size="small" @change="onDaysChange">
          <el-radio-button v-for="d in DAY_OPTIONS" :key="d" :value="d">{{ d }} 天</el-radio-button>
        </el-radio-group>
      </div>
      <div class="oe-bar-right">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
        <el-tooltip content="抽样打判定分要真花判定模型的 token，这一步是花钱动作，所以只手工触发" placement="top">
          <el-button type="primary" @click="openScoreDialog">立即抽样打分</el-button>
        </el-tooltip>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon class="oe-note"
              title="这里看的是线上真实回答的质量水位：分数由判定模型按 RAGAS 口径打在每条模型调用上，只覆盖已抽样打分的部分。" />

    <div class="oe-grid">
      <div class="oe-cell">
        <div class="oe-value">{{ scored }}</div>
        <div class="oe-label">窗口内已评</div>
        <div class="oe-sub">条调用出了判定分</div>
      </div>
      <div class="oe-cell">
        <div class="oe-value" :style="{ color: Number(pending) > 0 ? '#e6a23c' : '#67c23a' }">{{ pending }}</div>
        <div class="oe-label">窗口内待评</div>
        <div class="oe-sub">还没抽样到的调用</div>
      </div>
      <div class="oe-cell">
        <div class="oe-value" :style="{ color: coverageColor }">{{ coverageText }}</div>
        <div class="oe-label">评测覆盖率</div>
        <div class="oe-sub">已评 ÷（已评 + 待评）</div>
      </div>
      <div class="oe-cell">
        <div class="oe-value" :style="{ color: Number(backlog) > 0 ? '#f56c6c' : '#67c23a' }">{{ backlog }}</div>
        <div class="oe-label">全库积压</div>
        <div class="oe-sub">不限窗口，不会自己消失</div>
      </div>
    </div>

    <!-- 覆盖率低时先说一句：否则下面那条均值曲线会被当成「全体水位」读，而它其实只是少数样本 -->
    <el-alert v-if="lowCoverage" type="warning" :closable="false" show-icon class="oe-note"
              :title="`覆盖率仅 ${coverageText}，下方均值只代表被抽到的那一小部分调用，不能当作线上整体水位`" />

    <el-table v-if="metricRows.length" :data="metricRows" size="small" stripe class="oe-metrics">
      <el-table-column label="指标" prop="label" min-width="160" />
      <el-table-column label="均值" width="120" align="center">
        <template #default="{ row }">
          <span :style="{ color: scoreColor(row.avg) }">{{ fmtScore(row.avg) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="出分样本" width="120" align="center">
        <template #default="{ row }">{{ row.cases }} 条</template>
      </el-table-column>
      <el-table-column label="占已评" width="160" align="center">
        <template #default="{ row }">{{ shareText(row.cases) }}</template>
      </el-table-column>
    </el-table>
    <div v-if="metricRows.length" class="oe-foot">
      分母是「出了这个指标分数的条数」，不是已评总数，也不是线上调用总数 ——
      只有一两条时这个均值当不得真。
    </div>
    <el-empty v-else :image-size="60" description="窗口内还没有判定分数，可点上方【立即抽样打分】跑一轮" />

    <div v-if="hasTrend" class="oe-charts">
      <el-card shadow="hover" class="oe-chart-card">
        <template #header><span class="oe-chart-title">指标均值按天</span></template>
        <div ref="trendRef" class="oe-chart"></div>
      </el-card>
      <el-card shadow="hover" class="oe-chart-card">
        <template #header><span class="oe-chart-title">已评 / 待评按天</span></template>
        <div ref="volumeRef" class="oe-chart"></div>
      </el-card>
    </div>

    <!-- 打分参数：条数直接等于花多少钱，所以必须让操作者自己填一个数再确认 -->
    <el-dialog v-model="scoreDialog" title="抽样打分" width="520px">
      <el-form label-width="100px">
        <el-form-item label="抽样条数">
          <el-input-number v-model="scoreForm.limit" :min="1" :max="200" :step="10" />
          <span class="oe-form-hint">每条样本要跑 2~4 次判定调用，上限 200</span>
        </el-form-item>
        <el-form-item label="判定模型">
          <el-select v-model="scoreForm.judgeModelId" filterable clearable style="width:100%"
                     placeholder="不选则取第一个可用的对话模型">
            <el-option v-for="m in models" :key="m.id" :label="`${m.name}（${m.modelName || ''}）`" :value="m.id" />
          </el-select>
          <span class="oe-form-hint">judge 不要求多强的生成能力，单独指一个便宜的能把成本降下来</span>
        </el-form-item>
        <el-form-item label="指标">
          <el-select v-model="scoreForm.metrics" multiple clearable style="width:100%"
                     placeholder="全部开启（不选即为全跑）">
            <el-option v-for="mt in METRIC_OPTIONS" :key="mt.value" :label="mt.label" :value="mt.value" />
          </el-select>
          <span class="oe-form-hint">线上样本没有参考答案，contextRecall 这类需要参考的指标会自己跳过</span>
        </el-form-item>
      </el-form>
      <el-alert v-if="scoreResult" type="success" :closable="false" show-icon class="oe-result"
                :title="scoreResult" />
      <template #footer>
        <el-button @click="scoreDialog = false">关闭</el-button>
        <el-button type="primary" :loading="scoring" @click="submitScore">开始打分</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 在线评测面板：把「线上回答现在好不好、评了多少、还有多少没评」放在同一屏。
 * <p>
 * 均值必须跟覆盖率一起看 —— 只评了 3% 的样本，那条曲线基本只是噪声，所以覆盖率与积压
 * 是面板主体而不是脚注；打分动作要花判定 token，因此只开放手工触发，由人明确决定评多少条。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { observabilityApi } from '@admin/api/observability-api'
import type { OnlineEvalTrend } from '@admin/api/observability-api'
import { evalApi } from '@admin/api/eval-api'
import { modelApi } from '@admin/api/model-api'
import { metricLabel, fmtScore, scoreColor } from '@admin/utils/metrics'
import { useTheme, getChartPalette } from '@admin/utils/theme'

const { themeColor } = useTheme()

const DAY_OPTIONS = [1, 7, 30, 90]

/** 与评测运行那边同一套指标名，取值必须对上后端 EvalMetric#name() */
const METRIC_OPTIONS = [
  { value: 'faithfulness', label: '忠实度 faithfulness' },
  { value: 'answerRelevancy', label: '答案相关性 answerRelevancy' },
  { value: 'contextPrecision', label: '上下文精度 contextPrecision' },
  { value: 'contextRecall', label: '上下文召回 contextRecall（需参考答案，线上样本通常跳过）' }
]

const days = ref(7)
const localDays = ref(7)
const loading = ref(false)
const trend = ref<OnlineEvalTrend | null>(null)
/** 全库积压走评测侧接口：它不限窗口，窗口外的欠账同样要还 */
const backlog = ref<number | null>(null)
const models = ref<any[]>([])

const scored = computed(() => trend.value?.scored ?? 0)
const pending = computed(() => trend.value?.pending ?? 0)
const coverage = computed(() => trend.value?.coverage ?? null)
const coverageText = computed(() => coverage.value == null ? '-' : (Number(coverage.value) * 100).toFixed(1) + '%')
const lowCoverage = computed(() => {
  const c = coverage.value
  // 一条都没评时不算「低覆盖率」，那种情况下面板已经给了「还没有判定分数」的空态
  return c != null && Number(c) > 0 && Number(c) < 0.1
})
const coverageColor = computed(() => {
  const c = coverage.value
  if (c == null || Number(c) <= 0) return '#909399'
  return Number(c) >= 0.5 ? '#67c23a' : Number(c) >= 0.1 ? '#e6a23c' : '#f56c6c'
})

const metricRows = computed(() => {
  const table = trend.value?.metrics || {}
  return Object.keys(table).map(name => ({
    name,
    label: metricLabel(name),
    avg: table[name]?.avg ?? null,
    cases: table[name]?.cases ?? 0
  }))
})

/** 出分样本占已评多少：一个指标只覆盖了已评样本的一小部分时，那个均值说的不是这批人 */
function shareText(cases: number): string {
  const total = Number(trend.value?.scored ?? 0)
  if (!total) return '-'
  return `${((Number(cases) || 0) / total * 100).toFixed(0)}%`
}

const hasTrend = computed(() => (trend.value?.daily?.length ?? 0) > 0)

function onDaysChange(value: any) {
  days.value = Number(value) || 7
  localDays.value = days.value
  load()
}

async function load() {
  loading.value = true
  try {
    const [t, b] = await Promise.allSettled([
      observabilityApi.llmTraceEvalTrend(days.value),
      evalApi.onlinePending()
    ])
    trend.value = t.status === 'fulfilled' ? t.value : null
    backlog.value = b.status === 'fulfilled' ? b.value : null
    await renderCharts()
  } finally {
    loading.value = false
  }
}

// ============ 图表 ============
const trendRef = ref<any>(null)
const volumeRef = ref<any>(null)
const charts: Record<string, echarts.ECharts> = {}

function chart(dom: any, key: string): echarts.ECharts | null {
  if (!dom) return null
  if (charts[key]) charts[key].dispose()
  const instance = echarts.init(dom)
  charts[key] = instance
  return instance
}

async function renderCharts() {
  await nextTick()
  const daily = trend.value?.daily || []
  if (!daily.length) return
  const axis = daily.map(d => d.day)

  // 指标均值：某天没有这个指标的样本就是断点，连起来会把「没评」画成「有分」
  const names = new Set<string>()
  daily.forEach(d => Object.keys(d.metrics || {}).forEach(n => names.add(n)))
  chart(trendRef.value, 'trend')?.setOption({
    color: getChartPalette(),
    tooltip: { trigger: 'axis', appendTo: 'body', valueFormatter: (v: any) => v == null ? '-' : (Number(v) * 100).toFixed(1) + '%' },
    legend: { top: 0, type: 'scroll' },
    grid: { left: 52, right: 20, top: 34, bottom: 30 },
    xAxis: { type: 'category', data: axis, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', min: 0, max: 1, axisLabel: { formatter: (v: number) => (v * 100) + '%' } },
    series: Array.from(names).map(n => ({
      name: metricLabel(n),
      type: 'line',
      symbolSize: 6,
      connectNulls: false,
      data: daily.map(d => d.metrics?.[n]?.avg ?? null)
    }))
  })

  // 已评 / 待评：这条堆叠柱的高度就是当天的抽样水位，均值曲线要跟它对照着看
  chart(volumeRef.value, 'volume')?.setOption({
    color: ['#67c23a', '#e6a23c'],
    tooltip: { trigger: 'axis', appendTo: 'body' },
    legend: { top: 0 },
    grid: { left: 52, right: 20, top: 34, bottom: 30 },
    xAxis: { type: 'category', data: axis, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '已评', type: 'bar', stack: 'total', data: daily.map(d => d.scored ?? 0) },
      { name: '待评', type: 'bar', stack: 'total', data: daily.map(d => d.pending ?? 0) }
    ]
  })
}

function handleResize() {
  for (const key of Object.keys(charts)) charts[key]?.resize()
}

// ============ 手工打分 ============
const scoreDialog = ref(false)
const scoring = ref(false)
const scoreResult = ref('')
const scoreForm = reactive({ limit: 20, judgeModelId: '', metrics: [] as string[] })

function openScoreDialog() {
  scoreResult.value = ''
  scoreDialog.value = true
}

async function submitScore() {
  scoring.value = true
  try {
    const data: Record<string, any> = { limit: scoreForm.limit }
    if (scoreForm.judgeModelId) data.judgeModelId = scoreForm.judgeModelId
    if (scoreForm.metrics.length) data.metrics = scoreForm.metrics
    const s = await evalApi.scoreOnline(data)
    const parts = [`取样 ${s.sampled} 条`, `回填 ${s.scored} 条`, `判定 token ${s.judgeTokens}`]
    if (s.withoutScore) parts.push(`无分数 ${s.withoutScore} 条`)
    if (s.lostRace) parts.push(`被并发抢先 ${s.lostRace} 条`)
    scoreResult.value = parts.join(' · ')
    ElMessage.success('这一轮打分已完成')
    load()
  } catch (e: any) {
    // 来源未接入、没有可用判定模型这类前置失败都是「一轮都没跑」，得当场说清楚而不是转圈
    scoreResult.value = ''
    ElMessage.error(e?.message || '打分失败')
  } finally {
    scoring.value = false
  }
}

onMounted(() => {
  load()
  modelApi.options().then(list => { models.value = list || [] }).catch(() => { models.value = [] })
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  for (const key of Object.keys(charts)) charts[key]?.dispose()
})

// 主题色变化时重绘：ECharts 是 canvas 渲染，不会跟着 CSS 变量走
watch(themeColor, () => renderCharts())
</script>

<style lang="scss" scoped>
.oe-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 10px; }
.oe-bar-left { display: flex; align-items: center; gap: 12px; }
.oe-bar-right { display: flex; align-items: center; gap: 10px; }
.oe-title { font-size: 14px; font-weight: 600; color: #303133; }
.oe-note { margin-bottom: 12px; }
.oe-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.oe-cell {
  background: #f7f8fa; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 14px 8px; text-align: center;
}
.oe-value { font-size: 22px; font-weight: 700; color: #303133; }
.oe-label { font-size: 12px; color: #606266; margin-top: 4px; }
.oe-sub { font-size: 11px; color: #a8abb2; margin-top: 2px; }
.oe-metrics { margin-bottom: 8px; }
.oe-foot { font-size: 12px; color: #909399; line-height: 1.7; margin-bottom: 14px; }
.oe-charts { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.oe-chart-card { border-radius: 10px; }
.oe-chart-title { font-size: 14px; font-weight: 600; color: #303133; }
.oe-chart { width: 100%; height: 280px; }
.oe-form-hint { font-size: 12px; color: #909399; margin-left: 8px; line-height: 1.6; }
.oe-result { margin-top: 4px; }

@media (max-width: 1200px) {
  .oe-charts { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .oe-grid { grid-template-columns: repeat(2, 1fr); }
  .oe-chart { height: 220px; }
}
</style>
