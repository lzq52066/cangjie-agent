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
        style="width:340px"
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

    <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                   :total="total" v-model:current-page="pageNum" v-model:page-size="pageSize"
                   :page-sizes="[10, 20, 50]" @size-change="loadList" @current-change="loadList" />

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

        <el-divider content-position="left">执行步骤（{{ detail.steps?.length || 0 }}）</el-divider>
        <el-table :data="detail.steps || []" size="small" border stripe>
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="step-io">
                <div v-if="row.input">
                  <div class="step-io-label">入参</div>
                  <pre class="code-block">{{ pretty(row.input) }}</pre>
                </div>
                <div v-if="row.output">
                  <div class="step-io-label">产出</div>
                  <pre class="code-block">{{ pretty(row.output) }}</pre>
                </div>
                <el-alert v-if="row.errorMessage" type="error" :closable="false" show-icon
                          :title="row.errorMessage" style="margin-top:8px" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="#" prop="stepNo" width="48" align="center" />
          <el-table-column label="轮" prop="round" width="48" align="center" />
          <el-table-column label="类型" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="stepTagType(row.type)">{{ row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="名称" prop="name" min-width="150" show-overflow-tooltip />
          <el-table-column label="Token" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.inputTokens != null" style="font-size:12px">
                {{ row.inputTokens }} / {{ row.outputTokens }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="80" align="center">
            <template #default="{ row }">{{ fmtDuration(row.duration) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'success' || row.status === 'completed' ? 'success' : 'danger'">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

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
import { onMounted, reactive, ref } from 'vue'
import QueryBar from '@admin/components/QueryBar.vue'
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
function pretty(text?: string) {
  if (!text) return '-'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
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
async function showDetail(runId: string) {
  detail.value = null
  drawer.value = true
  detail.value = await observabilityApi.agentRunDetail(runId)
}
</script>

<style lang="scss" scoped>
.filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 16px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.step-io { padding: 8px 16px; }
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
</style>
