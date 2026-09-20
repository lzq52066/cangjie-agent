<template>
  <div class="agent-runs-panel">
    <!-- 筛选条 -->
    <QueryBar :loading="loading" @search="onSearch" @reset="resetQuery">
      <el-input v-model="query.traceId" placeholder="TraceId" clearable style="width:180px"
                @keyup.enter="onSearch" />
      <el-input v-model="query.sessionId" placeholder="会话 ID" clearable style="width:170px"
                @keyup.enter="onSearch" />
      <el-select v-model="query.appId" placeholder="全部应用" clearable filterable
                 style="width:170px">
        <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-input v-model="query.userId" placeholder="用户 ID" clearable style="width:140px"
                @keyup.enter="onSearch" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width:140px">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select v-model="query.harnessType" placeholder="全部形态" clearable style="width:130px">
        <el-option v-for="h in harnessOptions" :key="h.value" :label="h.label" :value="h.value" />
      </el-select>
      <el-input v-model="query.modelName" placeholder="模型名称" clearable style="width:140px"
                @keyup.enter="onSearch" />
      <el-date-picker
        v-model="timeRange" type="datetimerange" range-separator="至"
        start-placeholder="开始时间" end-placeholder="结束时间"
        format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss"
        class="cj-daterange"
      />
    </QueryBar>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column label="开始时间" prop="startTime" width="160" />
      <el-table-column label="应用" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.appName || row.appId }}</template>
      </el-table-column>
      <el-table-column label="形态" width="90" align="center">
        <template #default="{ row }">{{ harnessLabel(row.harnessType) }}</template>
      </el-table-column>
      <el-table-column label="模型" prop="modelName" min-width="110" show-overflow-tooltip />
      <el-table-column label="用户" prop="userId" width="120" show-overflow-tooltip />
      <el-table-column label="轮次" prop="rounds" width="60" align="center" />
      <el-table-column label="工具调用" prop="toolCallCount" width="80" align="center" />
      <el-table-column label="Token" width="90" align="center">
        <template #default="{ row }">{{ row.totalTokens ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="center">
        <template #default="{ row }">{{ fmtDuration(row.duration) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="showDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <DataPager v-model:current-page="pageNum" v-model:page-size="pageSize"
               :total="total" @change="loadList" />

    <!-- 执行详情抽屉 -->
    <el-drawer v-model="drawer" :title="`Agent 执行详情${detail?.run ? ' - ' + detail.run.id : ''}`"
               size="820px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusTagType(detail.run.status)">{{ statusLabel(detail.run.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="形态">{{ harnessLabel(detail.run.harnessType) }}</el-descriptions-item>
          <el-descriptions-item label="深度">{{ detail.run.depth ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="应用">{{ detail.run.appName || detail.run.appId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.run.userId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ detail.run.modelName || detail.run.modelId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="TraceId" :span="3">{{ detail.run.traceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会话 ID" :span="3">{{ detail.run.sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="父 Run">{{ detail.run.parentRunId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="轮次 / 工具">{{ detail.run.rounds ?? 0 }} / {{ detail.run.toolCallCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ fmtDuration(detail.run.duration) }}</el-descriptions-item>
          <el-descriptions-item label="Token（入/出/总）" :span="2">
            {{ detail.run.inputTokens ?? '-' }} / {{ detail.run.outputTokens ?? '-' }} / {{ detail.run.totalTokens ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="预算 / 成本">
            {{ detail.run.tokenBudget ?? '不限' }} / {{ detail.run.estimatedCost ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="开始 / 结束" :span="2">
            {{ detail.run.startTime || '-' }} ~ {{ detail.run.endTime || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="结束原因">{{ detail.run.finishReason || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="detail.run.errorMessage" type="error" :closable="false" show-icon
                  :title="detail.run.errorMessage" style="margin:12px 0" />

        <el-divider content-position="left">
          执行过程（{{ roundGroups.length }} 轮 · {{ detail.steps?.length || 0 }} 步 · {{ llmCallCount }} 次模型调用）
        </el-divider>

        <!-- 上下文装配：还原"模型这一轮到底收到了哪些片段、各占多少预算" -->
        <div v-if="contextSlots.length" class="tl-node tl-node--context">
          <div class="tl-head" @click="toggle('ctx')">
            <el-tag size="small" type="info">context</el-tag>
            <span class="tl-name">上下文装配</span>
            <span class="tl-meta">{{ contextSlots.length }} 个槽位 · 约 {{ contextTokens }} tokens</span>
            <span class="tl-caret">{{ caret('ctx') }}</span>
          </div>
          <div v-if="isOpen('ctx')" class="tl-body">
            <el-table :data="contextSlots" size="small" border>
              <el-table-column label="槽位" prop="slot" width="130" />
              <el-table-column label="来源" prop="source" min-width="120" show-overflow-tooltip />
              <el-table-column label="消息" prop="messages" width="66" align="center" />
              <el-table-column label="占用" prop="estTokens" width="76" align="center" />
              <el-table-column label="预算" prop="budgetTokens" width="76" align="center" />
              <el-table-column label="丢弃" width="76" align="center">
                <template #default="{ row }">
                  <span :style="row.droppedTokens ? 'color:#f56c6c' : ''">{{ row.droppedTokens || 0 }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <div v-for="(g, gi) in roundGroups" :key="gi" class="tl-round">
          <div class="tl-round-head">
            <span class="tl-round-title">{{ g.round == null ? '未归属轮次' : `第 ${g.round} 轮` }}</span>
            <span class="tl-meta">
              {{ g.nodes.length }} 步 · {{ g.totalTokens }} tokens · {{ fmtDuration(g.duration) }}
            </span>
          </div>

          <div v-for="n in g.nodes" :key="n.key" class="tl-node" :class="`tl-node--${n.type}`">
            <div class="tl-head" @click="toggle(n.key)">
              <el-tag size="small" :type="stepTagType(n.type)">{{ n.type }}</el-tag>
              <span class="tl-name">{{ n.name || '-' }}</span>
              <span v-if="n.inputTokens != null" class="tl-meta">{{ n.inputTokens }}/{{ n.outputTokens }} tok</span>
              <span class="tl-meta">{{ fmtDuration(n.duration) }}</span>
              <el-tag v-if="n.calls.length > 1" size="small" type="warning" effect="plain">
                {{ n.calls.length }} 次调用
              </el-tag>
              <el-tag v-if="n.truncated" size="small" type="info" effect="plain">增量已截断</el-tag>
              <el-tag size="small" :type="n.status === 'success' || n.status === 'completed' ? 'success' : 'danger'"
                      effect="plain">{{ n.status }}</el-tag>
              <span class="tl-caret">{{ caret(n.key) }}</span>
            </div>

            <div v-if="isOpen(n.key)" class="tl-body">
              <template v-if="n.input">
                <div class="step-io-label">入参{{ n.type === 'llm' ? '（本轮增量）' : '' }}</div>
                <pre class="code-block">{{ prettyJson(n.input) }}</pre>
              </template>
              <template v-if="n.output">
                <div class="step-io-label">产出</div>
                <pre class="code-block">{{ prettyJson(n.output) }}</pre>
              </template>
              <el-alert v-if="n.errorMessage" type="error" :closable="false" show-icon
                        :title="n.errorMessage" style="margin-top:8px" />

              <!-- 该步骤名下的真实模型调用：完整 prompt、厂商 requestId、重试与降级都在这层 -->
              <div v-if="n.calls.length" class="tl-calls">
                <div class="step-io-label">模型调用（{{ n.calls.length }}）</div>
                <div v-for="(c, ci) in n.calls" :key="c.id" class="tl-call">
                  <div class="tl-call-head" @click="toggleCall(c)">
                    <el-tag size="small" :type="c.status === 'success' ? 'success' : 'danger'" effect="plain">
                      {{ ci + 1 }}
                    </el-tag>
                    <span class="tl-name">{{ c.modelName || '-' }}</span>
                    <span class="tl-meta">
                      {{ c.inputTokens ?? '-' }}/{{ c.outputTokens ?? '-' }} tok · {{ fmtDuration(c.duration) }}
                      · {{ c.finishReason || '-' }}
                    </span>
                    <span class="tl-meta">req {{ c.requestId || '-' }}</span>
                    <span class="tl-caret">{{ caret(c.id) }}</span>
                  </div>
                  <div v-if="isOpen(c.id)" class="tl-call-body" v-loading="!!traceLoading[c.id]">
                    <el-alert v-if="c.errorMessage" type="error" :closable="false" show-icon
                              :title="c.errorMessage" style="margin-bottom:8px" />
                    <div class="step-io-label">完整 Prompt</div>
                    <pre class="code-block">{{ renderPrompt(traceFull[c.id]?.promptContent) }}</pre>
                    <div class="step-io-label">模型响应</div>
                    <pre class="code-block">{{ traceFull[c.id]?.responseContent || '-' }}</pre>
                  </div>
                </div>
              </div>
              <div v-else-if="n.type === 'llm'" class="tl-hint">
                无模型调用明细：该 run 早于 run 关联上线，或步骤留痕未开启
              </div>
            </div>
          </div>
        </div>

        <el-empty v-if="!roundGroups.length && !contextSlots.length"
                  description="无步骤明细（agent-run.record-steps 已关闭）" :image-size="60" />

        <template v-if="detail.approvals?.length">
          <el-divider content-position="left">审批单（{{ detail.approvals.length }}）</el-divider>
          <el-table :data="detail.approvals" size="small" border stripe>
            <el-table-column label="工具" prop="toolName" min-width="130" show-overflow-tooltip />
            <el-table-column label="风险" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="riskTagType(row.riskLevel)">{{ row.riskLevel || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="approvalStatusType(row.status)">{{ approvalStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审批人" prop="decidedBy" width="120" show-overflow-tooltip />
            <el-table-column label="审批时间" prop="decideTime" width="160" />
            <el-table-column label="过期时间" prop="expireTime" width="160" />
          </el-table>
        </template>

        <template v-if="detail.children?.length">
          <el-divider content-position="left">子 Agent（{{ detail.children.length }}）</el-divider>
          <el-table :data="detail.children" size="small" border stripe>
            <el-table-column label="开始时间" prop="startTime" width="160" />
            <el-table-column label="形态" width="90" align="center">
              <template #default="{ row }">{{ harnessLabel(row.harnessType) }}</template>
            </el-table-column>
            <el-table-column label="模型" prop="modelName" min-width="110" show-overflow-tooltip />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="90" align="center">
              <template #default="{ row }">{{ fmtDuration(row.duration) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="showDetail(row.id)">进入</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <template v-if="detail.run.finalText">
          <el-divider content-position="left">最终输出</el-divider>
          <div class="final-text">{{ detail.run.finalText }}</div>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import QueryBar from '@admin/components/QueryBar.vue'
import DataPager from '@admin/components/DataPager.vue'
import { prettyJson } from '@admin/utils/format'
import { observabilityApi } from '@admin/api/observability-api'
import { applicationApi } from '@admin/api/application-api'

const statusOptions = [
  { value: 'running', label: '执行中' },
  { value: 'completed', label: '已完成' },
  { value: 'failed', label: '失败' },
  { value: 'cancelled', label: '已取消' },
  { value: 'waiting_approval', label: '待审批' }
]
const harnessOptions = [
  { value: 'chat', label: '对话' },
  { value: 'workflow', label: '工作流' },
  { value: 'subagent', label: '子 Agent' },
  { value: 'debug', label: '调试' }
]
function statusLabel(s?: string) {
  return statusOptions.find(o => o.value === s)?.label || s || '-'
}
function harnessLabel(h?: string) {
  return harnessOptions.find(o => o.value === h)?.label || h || '-'
}
function statusTagType(s?: string) {
  switch (s) {
    case 'completed': return 'success'
    case 'failed': return 'danger'
    case 'waiting_approval': return 'warning'
    case 'cancelled': return 'info'
    default: return 'primary'
  }
}
function stepTagType(t?: string) {
  if (t === 'tool') return 'warning'
  if (t === 'error') return 'danger'
  if (t === 'hook') return 'info'
  return 'primary'
}
function riskTagType(r?: string) {
  if (r === 'high') return 'danger'
  if (r === 'medium' || r === 'mid') return 'warning'
  return 'info'
}
function approvalStatusType(s?: string) {
  switch (s) {
    case 'approved': return 'success'
    case 'rejected': return 'danger'
    case 'expired': return 'info'
    case 'cancelled': return 'info'
    default: return 'warning'
  }
}
function approvalStatusLabel(s?: string) {
  return { pending: '待审批', approved: '已批准', rejected: '已驳回', expired: '已过期', cancelled: '已取消' }[s || ''] || s || '-'
}
function fmtDuration(ms?: number | null) {
  if (ms == null) return '-'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(2)} s`
}

const appOptions = ref<any[]>([])
onMounted(async () => {
  try {
    appOptions.value = await applicationApi.options()
  } catch {
    appOptions.value = []
  }
  loadList()
})

const query = reactive({
  traceId: '', sessionId: '', appId: '', userId: '',
  status: '', harnessType: '', modelName: ''
})
const timeRange = ref<[string, string] | null>(null)
const list = ref<any[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      traceId: query.traceId || undefined,
      sessionId: query.sessionId || undefined,
      appId: query.appId || undefined,
      userId: query.userId || undefined,
      status: query.status || undefined,
      harnessType: query.harnessType || undefined,
      modelName: query.modelName || undefined,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    const page = await observabilityApi.agentRuns(params)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNum.value = 1
  loadList()
}

/** 重置筛选条件并重新查询 */
function resetQuery() {
  query.traceId = ''
  query.sessionId = ''
  query.appId = ''
  query.userId = ''
  query.status = ''
  query.harnessType = ''
  query.modelName = ''
  timeRange.value = null
  onSearch()
}

const drawer = ref(false)
const detail = ref<any>(null)
const openKeys = ref<string[]>([])
/** 模型调用正文按需拉取后的缓存：正文可达数万字符，不随详情一次性下发 */
const traceFull = ref<Record<string, any>>({})
const traceLoading = ref<Record<string, boolean>>({})

async function showDetail(runId: string) {
  detail.value = null
  openKeys.value = []
  traceFull.value = {}
  drawer.value = true
  detail.value = await observabilityApi.agentRunDetail(runId)
}

function isOpen(key: string) {
  return openKeys.value.includes(key)
}
function toggle(key: string) {
  openKeys.value = isOpen(key) ? openKeys.value.filter(k => k !== key) : [...openKeys.value, key]
}
function caret(key: string) {
  return isOpen(key) ? '−' : '+'
}

function toggleCall(c: any) {
  toggle(c.id)
  if (isOpen(c.id) && !traceFull.value[c.id]) loadTrace(c.id)
}

async function loadTrace(id: string) {
  traceLoading.value[id] = true
  try {
    traceFull.value[id] = await observabilityApi.llmTraceDetail(id)
  } finally {
    delete traceLoading.value[id]
  }
}

/** 步骤时间按后端 @JsonFormat 为 'yyyy-MM-dd HH:mm:ss'，模型调用为 ISO，统一折算为毫秒 */
function toMs(t?: string | null): number {
  if (!t) return 0
  const ms = Date.parse(String(t).replace(' ', 'T'))
  return Number.isNaN(ms) ? 0 : ms
}

const contextSlots = computed<any[]>(() => {
  const raw = detail.value?.run?.contextUsage
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
})
const contextTokens = computed(() =>
  contextSlots.value.reduce((sum, s) => sum + (s.estTokens || 0), 0))

const llmCallCount = computed(() => detail.value?.llmTraces?.length || 0)

/**
 * 步骤与模型调用按时间窗对齐：一轮 exchange 只写一个 llm 步骤，
 * 但重试与供应商降级会在该窗口内产生多条调用，所以是一对多，不做压缩。
 */
const roundGroups = computed<any[]>(() => {
  const steps: any[] = detail.value?.steps || []
  const traces: any[] = detail.value?.llmTraces || []
  const claimed = new Set<string>()
  const groups: any[] = []
  let current: any = null

  for (const s of steps) {
    if (!current || current.round !== s.round) {
      current = { round: s.round ?? 0, nodes: [] }
      groups.push(current)
    }
    const node: any = { ...s, key: `s-${s.stepNo ?? s.id}`, calls: [] }
    if (s.type === 'llm') {
      // 留痕时间各自取整，两端各放宽 1s；已认领的调用不再被后面的步骤抢走
      const from = toMs(s.startTime) - 1000
      const to = toMs(s.endTime) + 1000
      node.calls = traces.filter((t: any) => {
        if (claimed.has(t.id)) return false
        const at = toMs(t.startTime)
        if (at < from || at > to) return false
        claimed.add(t.id)
        return true
      })
    }
    current.nodes.push(node)
  }

  const leftovers = traces.filter((t: any) => !claimed.has(t.id))
  if (leftovers.length) {
    groups.push({
      round: null,
      nodes: leftovers.map((t: any) => ({
        key: `t-${t.id}`,
        type: 'llm',
        name: t.modelName,
        status: t.status,
        inputTokens: t.inputTokens,
        outputTokens: t.outputTokens,
        duration: t.duration,
        input: '',
        output: '',
        errorMessage: t.errorMessage,
        truncated: 0,
        calls: [t]
      }))
    })
  }

  return groups.map(g => ({
    ...g,
    totalTokens: g.nodes.reduce(
      (n: number, x: any) => n + (x.inputTokens || 0) + (x.outputTokens || 0), 0),
    duration: g.nodes.reduce((n: number, x: any) => n + (x.duration || 0), 0)
  }))
})

/** promptContent 存的是完整消息数组 JSON；可解析则按角色分段，否则原样输出 */
function renderPrompt(raw?: string): string {
  if (!raw) return '-'
  let parsed: any
  try {
    parsed = JSON.parse(raw)
  } catch {
    // 正文超长按字符数截断过，尾部不是合法 JSON，只能原样展示
    return raw
  }
  if (!Array.isArray(parsed)) return prettyJson(raw)
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
</script>

<style lang="scss" scoped>
.filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 16px; }
.step-io-label { font-size: 12px; color: #909399; margin: 6px 0 4px; }
.code-block {
  background: #f9fafc; border: 1px solid #ebeef5; border-radius: 8px; padding: 10px;
  max-height: 240px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  font-size: 12px; line-height: 1.7; margin: 0;
}
.final-text {
  background: #fafafa; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px;
  white-space: pre-wrap; word-break: break-all; font-size: 13px; line-height: 1.8;
}

.tl-round { margin-bottom: 14px; }
.tl-round-head {
  display: flex; align-items: center; gap: 10px;
  padding: 6px 0; border-bottom: 1px dashed #ebeef5;
}
.tl-round-title { font-size: 13px; font-weight: 600; color: #303133; }

.tl-node {
  display: flex; flex-direction: column; margin-top: 8px;
  background: #fff; border: 1px solid #ebeef5; border-radius: 8px;
}
.tl-node--llm { border-left: 3px solid #409eff; }
.tl-node--tool { border-left: 3px solid #e6a23c; }
.tl-node--error { border-left: 3px solid #f56c6c; }
.tl-node--hook, .tl-node--context { border-left: 3px solid #909399; }

.tl-head, .tl-call-head {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; cursor: pointer; font-size: 12px;
}
.tl-head:hover, .tl-call-head:hover { background: #f9fafc; }
.tl-name { font-size: 12px; font-weight: 600; color: #303133; }
.tl-meta { font-size: 12px; color: #909399; }
.tl-caret { margin-left: auto; font-size: 14px; line-height: 1; color: #c0c4cc; }
.tl-body { padding: 0 12px 12px; border-top: 1px solid #f2f3f5; }

.tl-calls { margin-top: 10px; }
.tl-call { margin-bottom: 6px; border: 1px solid #ebeef5; border-radius: 6px; }
.tl-call-head { padding: 6px 8px; }
.tl-call-body { padding: 0 10px 10px; border-top: 1px solid #f2f3f5; }
.tl-hint { margin-top: 8px; font-size: 12px; color: #a8abb2; }
</style>
