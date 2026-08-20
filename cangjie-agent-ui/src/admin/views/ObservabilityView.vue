<template>
  <div class="obs-view">
    <!-- 汇总面板 -->
    <div class="stat-grid">
      <el-card v-for="s in stats" :key="s.key" shadow="hover" class="stat-card">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
        <div class="stat-unit">{{ s.unit }}</div>
      </el-card>
    </div>

    <el-card>
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="操作日志" name="logs" />
        <el-tab-pane label="系统指标" name="metrics" />
        <el-tab-pane label="调用追踪" name="traces" />
        <el-tab-pane label="LLM 调用" name="llm-traces" />
      </el-tabs>

      <!-- 操作日志 -->
      <template v-if="activeTab === 'logs'">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-input v-model="logQuery.module" placeholder="模块" clearable style="width:150px" @keyup.enter="loadLogs" />
            <el-input v-model="logQuery.action" placeholder="操作" clearable style="width:150px" @keyup.enter="loadLogs" />
            <el-select v-model="logQuery.status" placeholder="全部状态" clearable style="width:130px" @change="loadLogs">
              <el-option label="成功" value="success" />
              <el-option label="失败" value="fail" />
            </el-select>
            <el-button type="primary" @click="loadLogs">查询</el-button>
          </div>
          <el-button @click="refresh"><el-icon><Refresh /></el-icon> 刷新面板</el-button>
        </div>
        <el-table :data="operationLogs" v-loading="loading" stripe>
          <el-table-column label="时间" prop="createTime" width="170" />
          <el-table-column label="模块" prop="module" width="120" />
          <el-table-column label="操作" prop="action" width="130" />
          <el-table-column label="接口" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.method }} {{ row.uri }}</template>
          </el-table-column>
          <el-table-column label="用户" prop="username" width="120" />
          <el-table-column label="IP" prop="ip" width="130" />
          <el-table-column label="耗时" width="100" align="center">
            <template #default="{ row }">{{ row.duration != null ? row.duration + ' ms' : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top">
                <el-tag size="small" type="danger">失败</el-tag>
              </el-tooltip>
              <el-tag v-else size="small" type="success">成功</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="showLog(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 系统指标 -->
      <template v-else-if="activeTab === 'metrics'">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-select v-model="metricQuery.metricType" placeholder="全部类型" clearable style="width:160px"
                       @change="loadMetrics">
              <el-option v-for="t in metricTypes" :key="t" :label="t" :value="t" />
            </el-select>
            <el-button type="primary" @click="loadMetrics">查询</el-button>
          </div>
          <el-button type="success" :loading="collecting" @click="handleCollect">
            <el-icon><Odometer /></el-icon> 立即采集
          </el-button>
        </div>
        <el-table :data="systemMetrics" v-loading="loading" stripe>
          <el-table-column label="采集时间" prop="collectTime" width="180" />
          <el-table-column label="类型" prop="metricType" width="140" />
          <el-table-column label="指标名" prop="metricName" min-width="200" />
          <el-table-column label="数值" width="160" align="right">
            <template #default="{ row }">{{ formatNumber(row.metricValue) }}</template>
          </el-table-column>
          <el-table-column label="单位" prop="unit" width="100" align="center" />
          <el-table-column label="主机" prop="host" min-width="160" show-overflow-tooltip />
        </el-table>
      </template>

      <!-- 调用追踪 -->
      <template v-else-if="activeTab === 'traces'">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-input v-model="traceQuery.traceId" placeholder="TraceId" clearable style="width:220px"
                      @keyup.enter="loadTraces" />
            <el-input v-model="traceQuery.module" placeholder="模块" clearable style="width:150px"
                      @keyup.enter="loadTraces" />
            <el-input v-model="traceQuery.action" placeholder="操作" clearable style="width:150px"
                      @keyup.enter="loadTraces" />
            <el-button type="primary" @click="loadTraces">查询</el-button>
          </div>
        </div>
        <el-table :data="callTraces" v-loading="loading" stripe>
          <el-table-column label="开始时间" width="180">
            <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="TraceId" prop="traceId" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link type="primary" @click="filterByTrace(row.traceId)">{{ row.traceId }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="模块" prop="module" width="130" />
          <el-table-column label="操作" prop="action" width="150" />
          <el-table-column label="服务" prop="serviceName" width="140" />
          <el-table-column label="耗时" width="100" align="center">
            <template #default="{ row }">{{ row.duration != null ? row.duration + ' ms' : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'">
                {{ row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="信息" prop="message" min-width="200" show-overflow-tooltip />
        </el-table>
      </template>

      <!-- LLM 调用追踪 -->
      <template v-else-if="activeTab === 'llm-traces'">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-input v-model="llmTraceQuery.traceId" placeholder="TraceId" clearable style="width:200px"
                      @keyup.enter="loadLlmTraces" />
            <el-input v-model="llmTraceQuery.appName" placeholder="应用名称" clearable style="width:150px"
                      @keyup.enter="loadLlmTraces" />
            <el-input v-model="llmTraceQuery.modelName" placeholder="模型名称" clearable style="width:150px"
                      @keyup.enter="loadLlmTraces" />
            <el-select v-model="llmTraceQuery.status" placeholder="全部状态" clearable style="width:120px" @change="loadLlmTraces">
              <el-option label="成功" value="success" />
              <el-option label="失败" value="fail" />
            </el-select>
            <el-button type="primary" @click="loadLlmTraces">查询</el-button>
          </div>
        </div>
        <el-table :data="llmTraces" v-loading="loading" stripe>
          <el-table-column label="开始时间" width="170">
            <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="TraceId" prop="traceId" min-width="200" show-overflow-tooltip />
          <el-table-column label="应用名称" prop="appName" min-width="140" />
          <el-table-column label="模型" prop="modelName" min-width="120" />
          <el-table-column label="输入Token" prop="inputTokens" width="110" align="center" />
          <el-table-column label="输出Token" prop="outputTokens" width="110" align="center" />
          <el-table-column label="总Token" prop="totalTokens" width="100" align="center" />
          <el-table-column label="耗时" width="100" align="center">
            <template #default="{ row }">{{ row.duration != null ? row.duration + ' ms' : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'">
                {{ row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                     :total="total" v-model:current-page="pageNum" v-model:page-size="pageSize"
                     :page-sizes="[10, 20, 50, 100]" @size-change="reload" @current-change="reload" />
    </el-card>

    <!-- 日志详情 -->
    <el-dialog v-model="logDialog" title="日志详情" width="720px" destroy-on-close>
      <el-descriptions v-if="currentLog" :column="2" border size="small">
        <el-descriptions-item label="模块">{{ currentLog.module }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ currentLog.action }}</el-descriptions-item>
        <el-descriptions-item label="接口">{{ currentLog.method }} {{ currentLog.uri }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentLog.duration ?? '-' }} ms</el-descriptions-item>
        <el-descriptions-item label="用户">{{ currentLog.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ currentLog.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="TraceId" :span="2">{{ currentLog.traceId || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="currentLog?.errorMessage" type="error" :closable="false" show-icon
                :title="currentLog.errorMessage" style="margin-top:12px" />
      <template v-if="currentLog">
        <el-divider>请求参数</el-divider>
        <pre class="code-block">{{ pretty(currentLog.params) }}</pre>
        <el-divider>响应结果</el-divider>
        <pre class="code-block">{{ pretty(currentLog.result) }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Odometer } from '@element-plus/icons-vue'
import { observabilityApi } from '@admin/api/observability-api'

const metricTypes = ['jvm', 'system', 'business']

const activeTab = ref<'logs' | 'metrics' | 'traces' | 'llm-traces'>('logs')
const loading = ref(false)
const collecting = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dashboard = ref<Record<string, any>>({})
const stats = computed(() => [
  { key: 'chat', label: '今日对话', value: dashboard.value.todayChatCount ?? 0, unit: '次', color: '#409eff' },
  { key: 'model', label: '模型调用', value: dashboard.value.todayModelCallCount ?? 0, unit: '次', color: '#67c23a' },
  { key: 'error', label: '今日错误', value: dashboard.value.todayErrorCount ?? 0, unit: '条', color: '#f56c6c' },
  { key: 'avg', label: '平均耗时', value: formatNumber(dashboard.value.avgDuration), unit: 'ms', color: '#e6a23c' },
  { key: 'log', label: '操作日志', value: dashboard.value.todayLogCount ?? 0, unit: '条', color: '#909399' },
  { key: 'trace', label: '调用追踪', value: dashboard.value.todayTraceCount ?? 0, unit: '条', color: '#7c4dff' }
])

const operationLogs = ref<any[]>([])
const logQuery = reactive({ module: '', action: '', status: '' })
const systemMetrics = ref<any[]>([])
const metricQuery = reactive({ metricType: '' })
const callTraces = ref<any[]>([])
const traceQuery = reactive({ traceId: '', module: '', action: '' })
const llmTraces = ref<any[]>([])
const llmTraceQuery = reactive({ traceId: '', appName: '', modelName: '', status: '' })

function formatNumber(value: any) {
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return Number.isInteger(num) ? String(num) : num.toFixed(2)
}

// 时间格式化为 年-月-日 时:分:秒
function formatTime(value?: string) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function pretty(text?: string) {
  if (!text) return '-'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

async function loadDashboard() {
  try {
    dashboard.value = await observabilityApi.dashboard()
  } catch {
    dashboard.value = {}
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const page = await observabilityApi.logs({ ...logQuery, pageNum: pageNum.value, pageSize: pageSize.value })
    operationLogs.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadMetrics() {
  loading.value = true
  try {
    const page = await observabilityApi.metrics({ ...metricQuery, pageNum: pageNum.value, pageSize: pageSize.value })
    systemMetrics.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadTraces() {
  loading.value = true
  try {
    const page = await observabilityApi.traces({ ...traceQuery, pageNum: pageNum.value, pageSize: pageSize.value })
    callTraces.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadLlmTraces() {
  loading.value = true
  try {
    const page = await observabilityApi.llmTraces({ ...llmTraceQuery, pageNum: pageNum.value, pageSize: pageSize.value })
    llmTraces.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  if (activeTab.value === 'logs') loadLogs()
  else if (activeTab.value === 'metrics') loadMetrics()
  else if (activeTab.value === 'llm-traces') loadLlmTraces()
  else loadTraces()
}

function onTabChange() {
  pageNum.value = 1
  total.value = 0
  reload()
}

function refresh() {
  loadDashboard()
  reload()
}

async function handleCollect() {
  collecting.value = true
  try {
    const result = await observabilityApi.collectMetrics()
    ElMessage.success(`采集完成，新增 ${result?.length ?? 0} 条指标`)
    pageNum.value = 1
    loadMetrics()
  } finally {
    collecting.value = false
  }
}

function filterByTrace(traceId: string) {
  traceQuery.traceId = traceId
  pageNum.value = 1
  loadTraces()
}

const logDialog = ref(false)
const currentLog = ref<any>(null)
function showLog(row: any) {
  currentLog.value = row
  logDialog.value = true
}

onMounted(() => {
  loadDashboard()
  loadLogs()
})
</script>

<style lang="scss" scoped>
.stat-grid {
  display: grid; grid-template-columns: repeat(6, 1fr); gap: 16px; margin-bottom: 16px;
}
.stat-card { text-align: center; }
.stat-label { font-size: 13px; color: #909399; }
.stat-value { font-size: 26px; font-weight: 600; margin: 6px 0 2px; }
.stat-unit { font-size: 12px; color: #c0c4cc; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar-left { display: flex; gap: 12px; flex-wrap: wrap; }
.pager { margin-top: 16px; justify-content: flex-end; }
.code-block {
  background: #f9fafc; border: 1px solid #ebeef5; border-radius: 8px; padding: 12px;
  max-height: 220px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  font-size: 12px; line-height: 1.7; margin: 0;
}
@media (max-width: 1440px) {
  .stat-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>