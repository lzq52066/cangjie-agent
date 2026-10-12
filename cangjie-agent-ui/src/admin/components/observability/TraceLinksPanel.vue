<template>
  <div class="trace-links-panel">
    <div class="mode-bar">
      <el-radio-group v-model="mode" @change="onModeChange">
        <el-radio-button value="link">链路</el-radio-button>
        <el-radio-button value="llm">仅模型调用</el-radio-button>
      </el-radio-group>
      <span class="mode-hint">
        {{ mode === 'link'
          ? '一次请求 = 一行：span、Agent 执行、模型调用都在详情里，排查问题看这里'
          : '逐条模型调用，用于 Token / 成本核对与无 run 归属的后台调用' }}
      </span>
    </div>

    <!-- 链路：按 traceId 聚合 -->
    <template v-if="mode === 'link'">
      <QueryBar :loading="loading" @search="onSearch" @reset="resetQuery">
        <el-input v-model="linkQuery.traceId" placeholder="链路 ID / TraceId" clearable style="width:220px"
                  @keyup.enter="onSearch" />
        <el-select v-model="linkQuery.appId" placeholder="全部应用" clearable filterable style="width:170px">
          <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
        </el-select>
        <el-input v-model="linkQuery.sessionId" placeholder="会话 ID" clearable style="width:180px"
                  @keyup.enter="onSearch" />
        <el-input v-model="linkQuery.userId" placeholder="用户 ID" clearable style="width:140px"
                  @keyup.enter="onSearch" />
        <el-select v-model="linkQuery.status" placeholder="全部状态" clearable style="width:130px">
          <el-option v-for="s in LINK_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-date-picker v-model="linkQuery.timeRange" type="datetimerange" range-separator="至"
                        start-placeholder="开始时间" end-placeholder="结束时间"
                        format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss"
                        class="cj-daterange" />
      </QueryBar>

      <el-table :data="links" v-loading="loading" stripe @row-click="showLink">
        <el-table-column label="开始时间" width="165">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="入口" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">
              {{ linkEntryLabel(row.entryKind, row.entryLabel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="链路 ID" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click.stop="showLink(row)">{{ row.traceId }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="应用" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.appName || row.appId || '-' }}</template>
        </el-table-column>
        <el-table-column label="会话" prop="sessionId" min-width="140" show-overflow-tooltip />
        <el-table-column label="模型" prop="modelName" min-width="110" show-overflow-tooltip />
        <el-table-column label="构成" width="110" align="center">
          <template #default="{ row }">
            <el-tooltip :content="`span ${row.spanCount || 0} · Agent 执行 ${row.runCount || 0} · 模型调用 ${row.llmCallCount || 0}`"
                        placement="top">
              <span>{{ row.runCount || 0 }} / {{ row.llmCallCount || 0 }} / {{ row.spanCount || 0 }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="Token" width="90" align="center">
          <template #default="{ row }">{{ row.totalTokens || '-' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="center">
          <template #default="{ row }">{{ fmtDuration(row.duration) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top">
              <el-tag size="small" :type="linkStatusTagType(row.status)">
                {{ linkStatusLabel(row.status) }}
              </el-tag>
            </el-tooltip>
            <el-tag v-else size="small" :type="linkStatusTagType(row.status)">
              {{ linkStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click.stop="showLink(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 仅模型调用：逐条留痕，面向 Token / 成本核对 -->
    <template v-else>
      <QueryBar :loading="loading" @search="onSearch" @reset="resetQuery">
        <el-input v-model="llmQuery.traceId" placeholder="链路 ID / TraceId" clearable style="width:200px"
                  @keyup.enter="onSearch" />
        <el-input v-model="llmQuery.appName" placeholder="应用名称" clearable style="width:150px"
                  @keyup.enter="onSearch" />
        <el-input v-model="llmQuery.modelName" placeholder="模型名称" clearable style="width:150px"
                  @keyup.enter="onSearch" />
        <el-input v-model="llmQuery.sessionId" placeholder="会话 ID" clearable style="width:180px"
                  @keyup.enter="onSearch" />
        <el-input v-model="llmQuery.promptKeyword" placeholder="输入关键词" clearable style="width:140px"
                  @keyup.enter="onSearch" />
        <el-input v-model="llmQuery.responseKeyword" placeholder="输出关键词" clearable style="width:140px"
                  @keyup.enter="onSearch" />
        <el-select v-model="llmQuery.status" placeholder="全部状态" clearable style="width:120px">
          <el-option label="成功" value="success" />
          <el-option label="失败" value="fail" />
        </el-select>
        <el-date-picker v-model="llmQuery.timeRange" type="datetimerange" range-separator="至"
                        start-placeholder="开始时间" end-placeholder="结束时间"
                        format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss"
                        class="cj-daterange" />
      </QueryBar>

      <el-table :data="llmList" v-loading="loading" stripe @row-click="showLlm">
        <el-table-column label="开始时间" width="165">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="链路 ID" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link v-if="row.traceId" type="primary" @click.stop="toLink(row)">{{ row.traceId }}</el-link>
            <span v-else class="no-trace">未关联</span>
          </template>
        </el-table-column>
        <el-table-column label="应用" prop="appName" min-width="120" show-overflow-tooltip />
        <el-table-column label="模型" prop="modelName" min-width="110" show-overflow-tooltip />
        <!-- 正文列已从列表裁掉（一条调用两三万字符），这一排换成不拉正文也能算的体感与钱 -->
        <el-table-column label="首字" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.ttft != null">{{ fmtDuration(row.ttft) }}</span>
            <span v-else class="no-trace">非流式</span>
          </template>
        </el-table-column>
        <el-table-column label="成本" width="95" align="center">
          <template #default="{ row }">{{ fmtCost(row.cost) }}</template>
        </el-table-column>
        <el-table-column label="评测" width="80" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="evalOf(row).list.length" placement="top">
              <template #content>
                <div v-for="m in evalOf(row).list" :key="m.name">{{ m.name }}: {{ m.text }}</div>
              </template>
              <span :style="{ color: scoreColor(evalOf(row).avg) }">{{ fmtScore(evalOf(row).avg) }}</span>
            </el-tooltip>
            <span v-else class="no-trace">未评</span>
          </template>
        </el-table-column>
        <el-table-column label="Token（入/出/总）" width="140" align="center">
          <template #default="{ row }">
            <span class="cell-sm">
              {{ row.inputTokens ?? '-' }} / {{ row.outputTokens ?? '-' }} / {{ row.totalTokens ?? '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="center">
          <template #default="{ row }">{{ fmtDuration(row.duration) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="callStatusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click.stop="showLlm(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <DataPager v-model:current-page="pageNum" v-model:page-size="pageSize"
               :total="total" :page-sizes="[10, 20, 50, 100]" @change="loadList" />

    <!-- 链路详情：端到端 span + Agent 执行时间线 + 后台模型调用 -->
    <el-drawer v-model="linkDrawer" :title="`链路详情${linkId ? ' - ' + shortKey(linkId) : ''}`"
               size="880px" destroy-on-close>
      <TraceLinkDetail v-if="linkId" :link-id="linkId" @switch="(id: string) => (linkId = id)" />
    </el-drawer>

    <!-- 单次模型调用详情 -->
    <el-drawer v-model="llmDrawer" title="模型调用详情" size="720px" destroy-on-close>
      <template v-if="currentLlm">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="开始时间">{{ formatDateTime(currentLlm.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ fmtDuration(currentLlm.duration) }}</el-descriptions-item>
          <el-descriptions-item label="首字延迟">
            {{ currentLlm.ttft != null ? fmtDuration(currentLlm.ttft) : '非流式' }}
          </el-descriptions-item>
          <el-descriptions-item label="成本">{{ fmtCost(currentLlm.cost) }}</el-descriptions-item>
          <el-descriptions-item label="应用">{{ currentLlm.appName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ currentLlm.modelName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会话 ID">{{ currentLlm.sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ currentLlm.userId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Token（入/出/总）">
            {{ currentLlm.inputTokens ?? '-' }} / {{ currentLlm.outputTokens ?? '-' }} /
            {{ currentLlm.totalTokens ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="FinishReason">{{ currentLlm.finishReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="在线评测" :span="2">
            <template v-if="evalOf(currentLlm).list.length">
              <el-tag v-for="m in evalOf(currentLlm).list" :key="m.name" size="small" effect="plain"
                      :style="{ color: scoreColor(m.value), marginRight: '6px' }">
                {{ m.name }} {{ m.text }}
              </el-tag>
            </template>
            <span v-else class="no-trace">未评</span>
          </el-descriptions-item>
          <el-descriptions-item label="链路 ID" :span="2">
            <el-link v-if="currentLlm.traceId" type="primary" @click="toLink(currentLlm)">
              {{ currentLlm.traceId }}
            </el-link>
            <span v-else>未关联</span>
          </el-descriptions-item>
          <el-descriptions-item label="RequestId" :span="2">{{ currentLlm.requestId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态" :span="2">
            <el-tag size="small" :type="callStatusTagType(currentLlm.status)">
              {{ callStatusLabel(currentLlm.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <!-- 正文不在精简行上，只在行真带正文时才预填缓存，否则交给正文组件按 id 单条拉 -->
        <LlmCallBody :trace-id="currentLlm.id" :cache="llmCache" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
/**
 * 链路追踪面板：原先分散在「调用追踪 / LLM 调用 / Agent 执行」三个 tab 的同一批数据，
 * 这里按 traceId 归并成一个视图排障，仅保留「仅模型调用」作为 Token 与成本核对口径。
 */
import { onMounted, reactive, ref, watch } from 'vue'
import QueryBar from '@admin/components/QueryBar.vue'
import DataPager from '@admin/components/DataPager.vue'
import TraceLinkDetail from '@admin/components/observability/TraceLinkDetail.vue'
import LlmCallBody from '@admin/components/observability/LlmCallBody.vue'
import { formatDateTime } from '@admin/utils/format'
import {
  avgScore, callStatusLabel, callStatusTagType, fmtCost, fmtDuration, LINK_STATUS_OPTIONS,
  linkEntryLabel, linkStatusLabel, linkStatusTagType, parseEvalScores
} from '@admin/utils/observability'
import { fmtScore, metricLabel, scoreColor } from '@admin/utils/metrics'
import { observabilityApi } from '@admin/api/observability-api'
import { applicationApi } from '@admin/api/application-api'

/** 其它视图点开的定位目标：具体链路（操作日志详情）或整段会话（会话记录） */
const props = withDefaults(defineProps<{ presetTraceId?: string; presetSessionId?: string }>(),
  { presetTraceId: '', presetSessionId: '' })

/** 定位跳转（操作日志→链路、会话记录→会话全部链路）的兜底回溯窗口：只给 7 天会把老数据挡在结果外 */
const PRESET_WINDOW_DAYS = 90

/** 列表默认回溯窗口：与后端聚合的兜底窗口一致，不给时间下限会退化成全表聚合 */
const LIST_WINDOW_DAYS = 7

type Mode = 'link' | 'llm'
const mode = ref<Mode>('link')

function rangeOf(days: number): [string, string] {
  const pad = (n: number) => String(n).padStart(2, '0')
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  const end = new Date()
  const start = new Date(end.getTime() - days * 86400000)
  return [fmt(start), fmt(end)]
}

const linkQuery = reactive({
  traceId: '', sessionId: '', appId: '', userId: '', status: '',
  timeRange: rangeOf(LIST_WINDOW_DAYS) as [string, string] | null
})
const llmQuery = reactive({
  traceId: '', appName: '', modelName: '', sessionId: '',
  promptKeyword: '', responseKeyword: '', status: '',
  timeRange: null as [string, string] | null
})

const links = ref<any[]>([])
const llmList = ref<any[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const appOptions = ref<any[]>([])
onMounted(async () => {
  if (props.presetTraceId) applyPreset(props.presetTraceId, false)
  else if (props.presetSessionId) applySessionPreset(props.presetSessionId, false)
  try {
    appOptions.value = await applicationApi.options()
  } catch {
    appOptions.value = []
  }
  loadList()
})

/** 定位到调用方指定的链路：时间窗一并放宽，否则上周之前的日志查不到 */
function applyPreset(traceId: string, reload = true) {
  mode.value = 'link'
  linkQuery.traceId = traceId
  linkQuery.sessionId = ''
  linkQuery.timeRange = rangeOf(PRESET_WINDOW_DAYS)
  pageNum.value = 1
  if (reload) loadList()
}

/** 定位到某个会话：一次请求只是一条链路，整段对话要靠 sessionId 展开成多条 */
function applySessionPreset(sessionId: string, reload = true) {
  mode.value = 'link'
  linkQuery.traceId = ''
  linkQuery.sessionId = sessionId
  linkQuery.timeRange = rangeOf(PRESET_WINDOW_DAYS)
  pageNum.value = 1
  if (reload) loadList()
}

// 面板已挂载时从别的视图切来看指定链路/会话
watch(() => props.presetTraceId, (v) => {
  if (v) applyPreset(v)
})
watch(() => props.presetSessionId, (v) => {
  if (v) applySessionPreset(v)
})

async function loadList() {
  loading.value = true
  try {
    if (mode.value === 'link') {
      const page = await observabilityApi.traceLinks({
        traceId: linkQuery.traceId || undefined,
        sessionId: linkQuery.sessionId || undefined,
        appId: linkQuery.appId || undefined,
        userId: linkQuery.userId || undefined,
        status: linkQuery.status || undefined,
        startTime: linkQuery.timeRange?.[0],
        endTime: linkQuery.timeRange?.[1],
        pageNum: pageNum.value,
        pageSize: pageSize.value
      })
      links.value = page?.records || []
      total.value = page?.total || 0
    } else {
      const page = await observabilityApi.llmTraces({
        traceId: llmQuery.traceId || undefined,
        appName: llmQuery.appName || undefined,
        modelName: llmQuery.modelName || undefined,
        sessionId: llmQuery.sessionId || undefined,
        promptKeyword: llmQuery.promptKeyword || undefined,
        responseKeyword: llmQuery.responseKeyword || undefined,
        status: llmQuery.status || undefined,
        startTime: llmQuery.timeRange?.[0],
        endTime: llmQuery.timeRange?.[1],
        pageNum: pageNum.value,
        pageSize: pageSize.value
      })
      llmList.value = page?.records || []
      total.value = page?.total || 0
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNum.value = 1
  loadList()
}

function onModeChange() {
  pageNum.value = 1
  total.value = 0
  links.value = []
  llmList.value = []
  loadList()
}

/** 只清空当前模式的筛选条件，另一模式的输入保持不变 */
function resetQuery() {
  if (mode.value === 'link') {
    linkQuery.traceId = ''
    linkQuery.sessionId = ''
    linkQuery.appId = ''
    linkQuery.userId = ''
    linkQuery.status = ''
    linkQuery.timeRange = rangeOf(LIST_WINDOW_DAYS)
  } else {
    llmQuery.traceId = ''
    llmQuery.appName = ''
    llmQuery.modelName = ''
    llmQuery.sessionId = ''
    llmQuery.promptKeyword = ''
    llmQuery.responseKeyword = ''
    llmQuery.status = ''
    llmQuery.timeRange = null
  }
  onSearch()
}

const linkDrawer = ref(false)
const linkId = ref('')

function showLink(row: any) {
  linkId.value = row.traceId
  linkDrawer.value = true
}

/** 孤立留痕（无 traceId）用 #kind#记录ID 作为链路键 */
function linkKeyOf(row: any) {
  return row.traceId || `#llm#${row.id}`
}

function toLink(row: any) {
  llmDrawer.value = false
  linkId.value = linkKeyOf(row)
  linkDrawer.value = true
}

function shortKey(key: string) {
  return key.length > 18 ? `${key.substring(0, 8)}…${key.substring(key.length - 6)}` : key
}

const llmDrawer = ref(false)
const currentLlm = ref<any>(null)
const llmCache = reactive<Record<string, any>>({})

function showLlm(row: any) {
  currentLlm.value = row
  // 裁列后的列表行没有 prompt / response，把它当「已加载」写进缓存，正文组件就会跳过请求
  // 而永远显示空正文；只有行上真带着正文时才值得预填
  if (row.promptContent || row.responseContent) llmCache[row.id] = row
  llmDrawer.value = true
}

/**
 * 一行的在线评测得分：列表只带 eval_scores 这一列小体积 JSON，展开前就能先看到分数水位。
 * <p>
 * 返回均分 + 逐指标明细：列上只放得下一个数，具体是哪一项拖后腿要能在悬浮层里看到。
 */
function evalOf(row: any): { avg: number | null, list: { name: string, text: string, value: number }[] } {
  const scores = parseEvalScores(row?.evalScores)
  const list = Object.entries(scores).map(([k, v]) => ({ name: metricLabel(k), text: fmtScore(v), value: v }))
  return { avg: avgScore(scores), list }
}
</script>

<style lang="scss" scoped>
.mode-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.mode-hint { font-size: 12px; color: #909399; }
.cell-sm { font-size: 12px; }
.no-trace { font-size: 12px; color: #a8abb2; }
</style>
