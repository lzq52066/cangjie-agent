<template>
  <div class="trace-link-detail" v-loading="loading">
    <template v-if="detail">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="linkStatusTagType(summary.status)">
            {{ linkStatusLabel(summary.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="入口">
          <el-tag size="small" type="info" effect="plain">{{ entryLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="端到端耗时">{{ fmtDuration(summary.duration) }}</el-descriptions-item>
        <el-descriptions-item label="开始">{{ formatDateTime(summary.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束">{{ formatDateTime(summary.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="Token">{{ summary.totalTokens ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="应用">{{ summary.appName || summary.appId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ summary.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ summary.modelName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="链路 Key" :span="3">
          <span class="mono">{{ summary.traceId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="会话 ID" :span="3">{{ summary.sessionId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="构成" :span="3">
          {{ summary.spanCount || 0 }} 个 span · {{ summary.runCount || 0 }} 次 Agent 执行 ·
          {{ summary.llmCallCount || 0 }} 次模型调用
        </el-descriptions-item>
      </el-descriptions>

      <el-alert v-if="summary.errorMessage" type="error" :closable="false" show-icon
                :title="summary.errorMessage" style="margin-top:12px" />

      <!-- 一次请求 = 一条链路，多轮对话会散成同会话的多条链路，不横向展开就会被读成"模型只答了一次" -->
      <template v-if="siblings.length > 1 && turnIndex > 0">
        <el-divider content-position="left">
          同会话请求（{{ siblings.length }}）· 本链路为第 {{ turnIndex }} 轮
        </el-divider>
        <div class="sibling-hint">
          一条链路只覆盖一次请求，会话里的第 N 轮对话是第 N 条链路；run 内的「循环 N 轮」指模型被调用的次数，与会话轮次不是一回事。
        </div>
        <el-table :data="siblings" size="small" border :row-class-name="siblingRowClass"
                  class="sibling-table" @row-click="switchTo">
          <el-table-column label="轮次" type="index" width="60" align="center" />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="入口" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="info" effect="plain">
                {{ linkEntryLabel(row.entryKind, row.entryLabel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="链路 ID" min-width="130">
            <template #default="{ row }"><span class="mono">{{ shortKey(row.traceId) }}</span></template>
          </el-table-column>
          <el-table-column label="run / 调用" width="100" align="center">
            <template #default="{ row }">
              <el-tooltip :content="`span ${row.spanCount || 0} · Agent 执行 ${row.runCount || 0} · 模型调用 ${row.llmCallCount || 0}`"
                          placement="top">
                <span>{{ row.runCount || 0 }} / {{ row.llmCallCount || 0 }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="Token" width="80" align="center">
            <template #default="{ row }">{{ row.totalTokens || '-' }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="85" align="center">
            <template #default="{ row }">{{ fmtDuration(row.duration) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="linkStatusTagType(row.status)">
                {{ linkStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <el-divider content-position="left">调用 Span（{{ spans.length }}）</el-divider>
      <el-table v-if="spans.length" :data="spans" size="small" border row-key="spanId">
        <el-table-column label="模块 / 动作" min-width="200">
          <template #default="{ row }">
            <span :style="{ paddingLeft: `${(row.treeDepth || 0) * 16}px` }">
              <span v-if="row.treeDepth" class="tree-branch">└</span>
              <el-tag size="small" :type="row.parentSpanId ? 'info' : 'primary'" effect="plain">
                {{ row.module }}
              </el-tag>
              <span class="span-action">{{ row.action }}</span>
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
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="信息" prop="message" min-width="180" show-overflow-tooltip />
      </el-table>
      <div v-else class="empty-hint">该链路无 span（早于 span 埋点上线，或由后台任务触发）</div>

      <el-divider content-position="left">Agent 执行（{{ runs.length }}）</el-divider>
      <div v-for="run in runs" :key="run.run.id" class="run-block"
           :style="{ marginLeft: `${(run.run.depth || 0) * 14}px` }">
        <div class="run-head" @click="toggleRun(run.run.id)">
          <el-tag size="small" :type="runStatusTagType(run.run.status)">
            {{ runStatusLabel(run.run.status) }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain">{{ harnessLabel(run.run.harnessType) }}</el-tag>
          <span class="run-name">{{ run.run.modelName || run.run.modelId || '-' }}</span>
          <span class="run-meta">
            循环 {{ run.run.rounds ?? 0 }} 轮 · {{ run.run.toolCallCount ?? 0 }} 工具 ·
            {{ run.run.totalTokens ?? '-' }} tok · {{ fmtDuration(run.run.duration) }}
          </span>
          <span class="run-meta mono">run {{ run.run.id }}</span>
          <span class="tl-caret">{{ isOpenRun(run.run.id) ? '−' : '+' }}</span>
        </div>
        <el-alert v-if="run.run.errorMessage" type="error" :closable="false" show-icon
                  :title="run.run.errorMessage" style="margin:0 10px 8px" />
        <div v-if="isOpenRun(run.run.id)" class="run-body">
          <RunTimeline :detail="run" :run-id="run.run.id" :cache="bodyCache" />
        </div>
      </div>
      <div v-if="!runs.length" class="empty-hint">该链路无 Agent 执行记录</div>

      <template v-if="background.length">
        <el-divider content-position="left">
          未关联 run 的模型调用（{{ background.length }}）
        </el-divider>
        <el-table :data="background" size="small" border>
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="bg-body"><LlmCallBody :trace-id="row.id" :cache="bodyCache" /></div>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="模型" prop="modelName" min-width="130" show-overflow-tooltip />
          <el-table-column label="Token（入/出/总）" width="150" align="center">
            <template #default="{ row }">
              {{ row.inputTokens ?? '-' }} / {{ row.outputTokens ?? '-' }} / {{ row.totalTokens ?? '-' }}
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
          <el-table-column label="finishReason" prop="finishReason" width="130" show-overflow-tooltip />
        </el-table>
      </template>
    </template>
    <el-empty v-else-if="loadError" description="链路明细不存在或已超出保留期" :image-size="60" />
  </div>
</template>

<script setup lang="ts">
/**
 * 一条链路的端到端明细：span 树 + 各 Agent run 执行时间线 + 未关联 run 的模型调用。
 * <p>
 * 抽屉用 destroy-on-close 挂载本组件；切换查看同会话的其它链路时原地重载，
 * 链路数据与模型调用正文缓存随目标链路一起重建。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import RunTimeline from '@admin/components/observability/RunTimeline.vue'
import LlmCallBody from '@admin/components/observability/LlmCallBody.vue'
import { formatDateTime } from '@admin/utils/format'
import { observabilityApi } from '@admin/api/observability-api'
import {
  callStatusTagType, fmtDuration, harnessLabel, linkEntryLabel, linkStatusLabel,
  linkStatusTagType, runStatusLabel, runStatusTagType, shiftTime, toMs
} from '@admin/utils/observability'

const props = defineProps<{ linkId: string }>()
const emit = defineEmits<{ (e: 'switch', linkId: string): void }>()

/** 实际渲染中的链路键：切换到同会话其它链路时先由本组件确定，再由调用方回写 props */
const activeId = ref(props.linkId)

const loading = ref(false)
const loadError = ref(false)
const detail = ref<any>(null)
const summary = computed<any>(() => detail.value?.summary || {})
const spans = computed<any[]>(() => detail.value?.spans || [])
const runs = computed<any[]>(() => detail.value?.runs || [])
const background = computed<any[]>(() => detail.value?.backgroundLlmTraces || [])
const entryLabel = computed(() => linkEntryLabel(summary.value.entryKind, summary.value.entryLabel))

/** 模型调用正文按需拉取后的缓存：正文可达数万字符，不随详情一次性下发 */
const bodyCache = reactive<Record<string, any>>({})

const openRuns = ref<string[]>([])
function isOpenRun(id: string) {
  return openRuns.value.includes(id)
}
function toggleRun(id: string) {
  openRuns.value = isOpenRun(id) ? openRuns.value.filter(x => x !== id) : [...openRuns.value, id]
}

const siblings = ref<any[]>([])
const turnIndex = computed(() => siblings.value.findIndex(r => r.traceId === activeId.value) + 1)

function shortKey(key?: string) {
  if (!key) return '-'
  return key.length > 18 ? `${key.substring(0, 8)}…${key.substring(key.length - 6)}` : key
}

function siblingRowClass({ row }: { row: any }) {
  return row.traceId === activeId.value ? 'sibling-active' : ''
}

function switchTo(row: any) {
  if (row.traceId && row.traceId !== activeId.value) emit('switch', row.traceId)
}

/**
 * 同会话的兄弟链路：链路只覆盖一次请求，多轮对话必须按 sessionId 横向展开才看得全。
 * 时间窗锚在本链路自身的前后一天，既够覆盖整段会话，也不会让聚合扫到无关区间。
 */
async function loadSiblings() {
  siblings.value = []
  const { sessionId, startTime } = summary.value
  if (!sessionId || !startTime) return
  const anchor = toMs(startTime)
  const day = 86400000
  const page = await observabilityApi.traceLinks({
    sessionId,
    startTime: shiftTime(anchor, -day),
    endTime: shiftTime(anchor, day),
    pageNum: 1,
    pageSize: 50
  })
  siblings.value = (page?.records || [])
    .slice()
    .sort((a: any, b: any) => toMs(a.startTime) - toMs(b.startTime))
}

/** span 按 parentSpanId 还原层级：入口 span 的 spanId 即 traceId，父节点为空 */
function buildSpanTree(list: any[]) {
  const byId = new Map<string, any>()
  list.forEach(s => s.spanId && byId.set(s.spanId, s))
  list.forEach((s) => {
    let depth = 0
    let parent = s.parentSpanId ? byId.get(s.parentSpanId) : null
    while (parent && depth < 10) {
      depth += 1
      parent = parent.parentSpanId ? byId.get(parent.parentSpanId) : null
    }
    s.treeDepth = depth
  })
}

async function load() {
  loading.value = true
  try {
    detail.value = await observabilityApi.traceLinkDetail(activeId.value)
    buildSpanTree(spans.value)
    const failed = runs.value.filter((r: any) => r.run?.errorMessage || r.run?.status === 'failed')
    openRuns.value = (failed.length ? failed : runs.value.slice(0, 1)).map((r: any) => r.run.id)
  } catch {
    // 链路可能已过期清理，这里只留占位提示，错误详情由 http 层统一弹 toast
    loadError.value = true
  } finally {
    loading.value = false
  }
  // 兄弟链路只是补充上下文，查不到不能把本链路渲染成"不存在"
  try {
    await loadSiblings()
  } catch {
    siblings.value = []
  }
}

// 从「同会话请求」切来看另一轮：props 由调用方改，这里原地重载并丢弃上一条链路的正文缓存
watch(() => props.linkId, (v) => {
  if (!v || v === activeId.value) return
  activeId.value = v
  detail.value = null
  loadError.value = false
  Object.keys(bodyCache).forEach(k => delete bodyCache[k])
  load()
})

onMounted(load)
</script>

<style lang="scss" scoped>
.mono { font-family: 'Menlo', 'Consolas', monospace; font-size: 12px; }
.tree-branch { color: #c0c4cc; margin-right: 4px; }
.span-action { margin-left: 6px; font-size: 12px; font-weight: 600; color: #303133; }
.empty-hint { font-size: 12px; color: #a8abb2; padding: 4px 0 8px; }

.sibling-hint { font-size: 12px; color: #909399; margin: -6px 0 8px; }
.sibling-table :deep(.el-table__row) { cursor: pointer; }
/* Element Plus 的行底色刷在单元格上，只改 tr 会被覆盖 */
.sibling-table :deep(.sibling-active td.el-table__cell) {
  background: var(--el-color-primary-light-9);
}

.run-block {
  margin-top: 10px; border: 1px solid #ebeef5; border-radius: 8px; background: #fff;
}
.run-head {
  display: flex; align-items: center; gap: 8px; padding: 9px 10px;
  cursor: pointer; font-size: 12px;
}
.run-head:hover { background: #f9fafc; }
.run-name { font-size: 12px; font-weight: 600; color: #303133; }
.run-meta { font-size: 12px; color: #909399; }
.tl-caret { margin-left: auto; font-size: 14px; line-height: 1; color: #c0c4cc; }
.run-body { padding: 10px 12px 14px; border-top: 1px solid #f2f3f5; }
.bg-body { padding: 0 12px 10px; }
</style>
